package com.vaibhav.emicalc.data

import com.vaibhav.emicalc.core.loan.Loan
import com.vaibhav.emicalc.core.loan.Prepayment
import com.vaibhav.emicalc.core.loan.PrepaymentMode
import com.vaibhav.emicalc.core.loan.RateReset
import com.vaibhav.emicalc.core.session.HistoryEntry
import com.vaibhav.emicalc.core.session.InteractionKind
import com.vaibhav.emicalc.core.session.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Async adapters over Room.
 *
 * The core module declares `HistoryStore` and `ResumeStore` as synchronous interfaces,
 * which is what makes the history rules unit-testable without coroutines and what backs
 * the Compose previews. Room, though, must not be called on the main thread, so the app
 * uses these suspend/[Flow] repositories instead of implementing the core interface
 * directly. Both sides share the same domain models, so nothing is duplicated except
 * the threading.
 */
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
private data class PrepaymentDto(val afterInstalment: Int, val amount: String, val mode: String)

@Serializable
private data class RateResetDto(val fromInstalment: Int, val annualRatePercent: String, val mode: String)

/** A loan together with its row id, which the list screen needs to edit or delete it. */
data class StoredLoan(val id: String, val loan: Loan)

class LoanRepository(private val dao: LoanDao) {

    fun observeLoans(): Flow<List<Loan>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    fun observeStored(): Flow<List<StoredLoan>> =
        dao.observeAll().map { rows -> rows.map { StoredLoan(it.id, toDomain(it)) } }

    suspend fun find(id: String): Loan? = dao.find(id)?.let(::toDomain)

    suspend fun save(id: String?, loan: Loan): String {
        val key = id ?: UUID.randomUUID().toString()
        dao.upsert(
            LoanEntity(
                id = key,
                name = loan.name,
                principal = loan.principal.toPlainString(),
                annualRatePercent = loan.annualRatePercent.toPlainString(),
                tenureMonths = loan.tenureMonths,
                createdAt = Instant.now().toEpochMilli(),
                prepaymentsJson = json.encodeToString(
                    loan.prepayments.map {
                        PrepaymentDto(it.afterInstalment, it.amount.toPlainString(), it.mode.name)
                    },
                ),
                rateResetsJson = json.encodeToString(
                    loan.rateResets.map {
                        RateResetDto(it.fromInstalment, it.annualRatePercent.toPlainString(), it.mode.name)
                    },
                ),
            ),
        )
        return key
    }

    suspend fun delete(id: String) = dao.delete(id)

    private fun toDomain(entity: LoanEntity) = Loan(
        name = entity.name,
        principal = BigDecimal(entity.principal),
        annualRatePercent = BigDecimal(entity.annualRatePercent),
        tenureMonths = entity.tenureMonths,
        prepayments = json.decodeFromString<List<PrepaymentDto>>(entity.prepaymentsJson).map {
            Prepayment(it.afterInstalment, BigDecimal(it.amount), PrepaymentMode.valueOf(it.mode))
        },
        rateResets = json.decodeFromString<List<RateResetDto>>(entity.rateResetsJson).map {
            RateReset(it.fromInstalment, BigDecimal(it.annualRatePercent), PrepaymentMode.valueOf(it.mode))
        },
    )
}

class HistoryRepository(private val dao: HistoryDao) {

    fun observeAll(): Flow<List<HistoryEntry>> =
        dao.observeActive().map { rows -> rows.map(::toDomain) }

    fun observeByKind(kind: InteractionKind): Flow<List<HistoryEntry>> =
        dao.observeByKind(kind.name).map { rows -> rows.map(::toDomain) }

    fun observeDeleted(): Flow<List<HistoryEntry>> =
        dao.observeDeleted().map { rows -> rows.map(::toDomain) }

    suspend fun find(id: String): HistoryEntry? = dao.find(id)?.let(::toDomain)

    /** Records an interaction and returns its id, so the caller can annotate it. */
    suspend fun record(
        kind: InteractionKind,
        title: String,
        summary: String,
        payload: String,
        at: Instant = Instant.now(),
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            HistoryEntryEntity(
                id = id,
                kind = kind.name,
                createdAt = at.toEpochMilli(),
                title = title,
                summary = summary,
                payload = payload,
                noteText = null,
                noteUpdatedAt = null,
                pinned = false,
                deletedAt = null,
            ),
        )
        return id
    }

    suspend fun annotate(id: String, text: String?, at: Instant = Instant.now()) {
        val body = text?.takeIf { it.isNotBlank() }
        require(body == null || body.length <= Note.MAX_LENGTH) {
            "note exceeds ${Note.MAX_LENGTH} characters"
        }
        dao.setNote(id, body, body?.let { at.toEpochMilli() })
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(id: String, at: Instant = Instant.now()) = dao.softDelete(id, at.toEpochMilli())

    suspend fun restore(id: String) = dao.restore(id)

    suspend fun purge(id: String) = dao.purge(id)

    suspend fun clear(kind: InteractionKind?, at: Instant = Instant.now()): Int =
        if (kind == null) dao.softDeleteAll(at.toEpochMilli()) else dao.softDeleteKind(kind.name, at.toEpochMilli())

    /** Drops anything deleted more than [retentionDays] ago. Called on app start. */
    suspend fun sweep(retentionDays: Long = 30, now: Instant = Instant.now()): Int =
        dao.purgeDeletedBefore(now.minusSeconds(retentionDays * 86_400).toEpochMilli())

    private fun toDomain(entity: HistoryEntryEntity) = HistoryEntry(
        id = entity.id,
        kind = runCatching { InteractionKind.valueOf(entity.kind) }
            .getOrDefault(InteractionKind.CALCULATION),
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        title = entity.title,
        summary = entity.summary,
        payload = entity.payload,
        note = entity.noteText?.let { Note(it, Instant.ofEpochMilli(entity.noteUpdatedAt ?: entity.createdAt)) },
        pinned = entity.pinned,
        deletedAt = entity.deletedAt?.let(Instant::ofEpochMilli),
    )
}
