package com.vaibhav.emicalc.core.loan

import com.vaibhav.emicalc.core.toPaise
import java.math.BigDecimal

/** What a prepayment is applied to. */
enum class PrepaymentMode {
    /** Keep the EMI, shorten the loan. Saves the most interest. */
    REDUCE_TENURE,

    /** Keep the tenure, lower the EMI. Eases monthly cash flow. */
    REDUCE_EMI,
}

/**
 * A lump-sum payment over and above the scheduled instalment, applied at the end of
 * [afterInstalment] (1-based).
 */
data class Prepayment(
    val afterInstalment: Int,
    val amount: BigDecimal,
    val mode: PrepaymentMode = PrepaymentMode.REDUCE_TENURE,
) {
    init {
        require(afterInstalment >= 1) { "afterInstalment must be >= 1, was $afterInstalment" }
        require(amount.signum() > 0) { "prepayment amount must be positive, was $amount" }
    }
}

/**
 * A rate revision taking effect from [fromInstalment] (1-based), as happens on
 * repo-linked floating loans. The lender either holds the EMI and moves the tenure,
 * or holds the tenure and re-computes the EMI; [mode] selects which.
 */
data class RateReset(
    val fromInstalment: Int,
    val annualRatePercent: BigDecimal,
    val mode: PrepaymentMode = PrepaymentMode.REDUCE_EMI,
) {
    init {
        require(fromInstalment >= 1) { "fromInstalment must be >= 1, was $fromInstalment" }
        require(annualRatePercent.signum() >= 0) { "rate cannot be negative, was $annualRatePercent" }
    }
}

/**
 * A single loan on a monthly reducing-balance basis, which is how retail lenders in
 * India quote home, car and personal loans.
 */
data class Loan(
    val name: String,
    val principal: BigDecimal,
    val annualRatePercent: BigDecimal,
    val tenureMonths: Int,
    val prepayments: List<Prepayment> = emptyList(),
    val rateResets: List<RateReset> = emptyList(),
) {
    init {
        require(principal.signum() > 0) { "principal must be positive, was $principal" }
        require(annualRatePercent.signum() >= 0) { "rate cannot be negative, was $annualRatePercent" }
        require(tenureMonths in 1..600) { "tenureMonths must be 1..600, was $tenureMonths" }
        require(prepayments.map { it.afterInstalment }.distinct().size == prepayments.size) {
            "at most one prepayment per instalment"
        }
    }

    val normalisedPrincipal: BigDecimal get() = principal.toPaise()
}

/** One row of an amortisation schedule. */
data class Instalment(
    val number: Int,
    val payment: BigDecimal,
    val interest: BigDecimal,
    val principal: BigDecimal,
    val prepayment: BigDecimal,
    val closingBalance: BigDecimal,
)

/** The result of amortising a [Loan]. */
data class AmortisationResult(
    val instalments: List<Instalment>,
    val scheduledEmi: BigDecimal,
    val finalEmi: BigDecimal,
    val totalInterest: BigDecimal,
    val totalPrepaid: BigDecimal,
) {
    val months: Int get() = instalments.size
    val totalPaid: BigDecimal get() = instalments.fold(BigDecimal.ZERO) { a, i -> a + i.payment + i.prepayment }
}
