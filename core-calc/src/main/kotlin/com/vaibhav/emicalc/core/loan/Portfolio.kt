package com.vaibhav.emicalc.core.loan

import com.vaibhav.emicalc.core.toPaise
import java.math.BigDecimal

/** A loan plus its amortisation, as the portfolio view needs it. */
data class LoanSummary(
    val loan: Loan,
    val emi: BigDecimal,
    val totalInterest: BigDecimal,
    val months: Int,
) {
    val outstanding: BigDecimal get() = loan.normalisedPrincipal
}

/** Which debt to attack first with a lump sum. */
enum class PayoffStrategy {
    /** Highest interest rate first. Mathematically optimal. */
    AVALANCHE,

    /** Smallest balance first. Slower, but clears individual loans sooner. */
    SNOWBALL,
}

/** What prepaying a given loan would save. */
data class PayoffOption(
    val loanName: String,
    val amountApplied: BigDecimal,
    val interestSaved: BigDecimal,
    val monthsSaved: Int,
)

/** Several loans considered together, which is the view single-loan calculators miss. */
data class Portfolio(val loans: List<Loan>) {

    val summaries: List<LoanSummary> by lazy {
        loans.map { loan ->
            val result = EmiCalculator.amortise(loan)
            LoanSummary(loan, result.scheduledEmi, result.totalInterest, result.months)
        }
    }

    val totalOutstanding: BigDecimal
        get() = summaries.fold(BigDecimal.ZERO) { a, s -> a + s.outstanding }.toPaise()

    val totalMonthlyEmi: BigDecimal
        get() = summaries.fold(BigDecimal.ZERO) { a, s -> a + s.emi }.toPaise()

    val totalInterest: BigDecimal
        get() = summaries.fold(BigDecimal.ZERO) { a, s -> a + s.totalInterest }.toPaise()

    /** Months until the last loan closes. */
    val debtFreeInMonths: Int get() = summaries.maxOfOrNull { it.months } ?: 0

    /**
     * Ranks what a lump sum would achieve on each loan, best first.
     *
     * The saving is computed by actually re-amortising each loan with the prepayment
     * applied rather than by a rate-of-interest heuristic, because the answer depends on
     * how far through its term each loan already is.
     */
    fun payoffOptions(
        lumpSum: BigDecimal,
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
    ): List<PayoffOption> {
        require(lumpSum.signum() > 0) { "lumpSum must be positive, was $lumpSum" }

        val options = loans.map { loan ->
            val before = EmiCalculator.amortise(loan)
            val applied = lumpSum.toPaise().min(loan.normalisedPrincipal)
            val after = EmiCalculator.amortise(
                loan.copy(prepayments = loan.prepayments + Prepayment(1, applied, PrepaymentMode.REDUCE_TENURE)),
            )
            PayoffOption(
                loanName = loan.name,
                amountApplied = applied,
                interestSaved = (before.totalInterest - after.totalInterest).toPaise(),
                monthsSaved = before.months - after.months,
            )
        }

        return when (strategy) {
            PayoffStrategy.AVALANCHE -> options.sortedByDescending { it.interestSaved }
            PayoffStrategy.SNOWBALL -> options.sortedBy { option ->
                loans.first { it.name == option.loanName }.normalisedPrincipal
            }
        }
    }
}
