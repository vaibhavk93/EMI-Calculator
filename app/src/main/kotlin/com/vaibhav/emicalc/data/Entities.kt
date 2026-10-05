package com.vaibhav.emicalc.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Money and rates are stored as [String], not `REAL`.
 *
 * SQLite's REAL is a double, so storing a [java.math.BigDecimal] through it would throw
 * away the exactness the calculation core is built on — an 80 lakh principal could come
 * back a fraction of a paisa out, and amortisation schedules would stop closing at zero.
 * Text round-trips exactly.
 */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val principal: String,
    val annualRatePercent: String,
    val tenureMonths: Int,
    val createdAt: Long,
    val prepaymentsJson: String,
    val rateResetsJson: String,
)

@Entity(tableName = "history")
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val createdAt: Long,
    val title: String,
    val summary: String,
    val payload: String,
    val noteText: String?,
    val noteUpdatedAt: Long?,
    val pinned: Boolean,
    val deletedAt: Long?,
)

/** A saved settlement, kept whole as JSON because its shape will keep changing. */
@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey val id: String,
    val label: String,
    val createdAt: Long,
    val detailsJson: String,
    val policyJson: String,
)
