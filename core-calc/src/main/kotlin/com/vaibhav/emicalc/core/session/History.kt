package com.vaibhav.emicalc.core.session

import java.time.Instant

/** The kinds of interaction the app remembers. */
enum class InteractionKind {
    CALCULATION,
    LOAN,
    SETTLEMENT,
    RUNWAY,
}

/** A note the user attached to an interaction. */
data class Note(val text: String, val updatedAt: Instant) {
    init {
        require(text.length <= MAX_LENGTH) { "note exceeds $MAX_LENGTH characters" }
    }

    companion object {
        const val MAX_LENGTH: Int = 2_000
    }
}

/**
 * One remembered interaction: a calculation, a loan, a settlement or a runway view.
 *
 * [payload] carries the serialised inputs rather than only the answer, so an entry can
 * be reopened and edited instead of merely read. [deletedAt] makes deletion reversible —
 * a settlement takes a long time to fill in, and losing one to a stray tap is the kind
 * of thing people do not forgive.
 */
data class HistoryEntry(
    val id: String,
    val kind: InteractionKind,
    val createdAt: Instant,
    val title: String,
    val summary: String,
    val payload: String,
    val note: Note? = null,
    val pinned: Boolean = false,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * Storage for remembered interactions.
 *
 * Declared as an interface in the pure-Kotlin core so the calculation and history rules
 * are testable without an Android device; the app module backs it with Room.
 */
interface HistoryStore {
    fun record(entry: HistoryEntry): HistoryEntry

    /** Most recent first. Excludes soft-deleted entries unless asked. */
    fun list(kind: InteractionKind? = null, includeDeleted: Boolean = false): List<HistoryEntry>

    fun find(id: String): HistoryEntry?

    /** Adds, replaces or (with null) removes the note on an entry. */
    fun annotate(id: String, text: String?, at: Instant): HistoryEntry?

    fun setPinned(id: String, pinned: Boolean): HistoryEntry?

    /** Reversible delete. Pinned entries are kept, so a pin also protects. */
    fun delete(id: String, at: Instant): HistoryEntry?

    fun restore(id: String): HistoryEntry?

    /** Irreversible. */
    fun purge(id: String): Boolean

    /** Irreversibly drops every soft-deleted entry older than [before]. */
    fun purgeDeletedBefore(before: Instant): Int

    /** Soft-deletes everything of a kind, or everything. Pinned entries survive. */
    fun clear(kind: InteractionKind? = null, at: Instant): Int
}

/**
 * In-memory [HistoryStore]. The reference implementation the rules are tested against,
 * and what the UI previews run on.
 */
class InMemoryHistoryStore : HistoryStore {

    private val entries = LinkedHashMap<String, HistoryEntry>()

    override fun record(entry: HistoryEntry): HistoryEntry {
        entries[entry.id] = entry
        return entry
    }

    override fun list(kind: InteractionKind?, includeDeleted: Boolean): List<HistoryEntry> =
        entries.values
            .filter { kind == null || it.kind == kind }
            .filter { includeDeleted || !it.isDeleted }
            .sortedWith(compareByDescending<HistoryEntry> { it.pinned }.thenByDescending { it.createdAt })

    override fun find(id: String): HistoryEntry? = entries[id]

    override fun annotate(id: String, text: String?, at: Instant): HistoryEntry? =
        update(id) { it.copy(note = text?.takeIf(String::isNotBlank)?.let { body -> Note(body, at) }) }

    override fun setPinned(id: String, pinned: Boolean): HistoryEntry? =
        update(id) { it.copy(pinned = pinned) }

    override fun delete(id: String, at: Instant): HistoryEntry? =
        update(id) { if (it.pinned || it.isDeleted) it else it.copy(deletedAt = at) }

    override fun restore(id: String): HistoryEntry? = update(id) { it.copy(deletedAt = null) }

    override fun purge(id: String): Boolean = entries.remove(id) != null

    override fun purgeDeletedBefore(before: Instant): Int {
        val doomed = entries.values
            .filter { it.deletedAt?.isBefore(before) == true }
            .map { it.id }
        doomed.forEach { entries.remove(it) }
        return doomed.size
    }

    override fun clear(kind: InteractionKind?, at: Instant): Int {
        val targets = entries.values
            .filter { (kind == null || it.kind == kind) && !it.pinned && !it.isDeleted }
            .map { it.id }
        targets.forEach { id -> update(id) { it.copy(deletedAt = at) } }
        return targets.size
    }

    private fun update(id: String, transform: (HistoryEntry) -> HistoryEntry): HistoryEntry? {
        val current = entries[id] ?: return null
        val updated = transform(current)
        entries[id] = updated
        return updated
    }
}
