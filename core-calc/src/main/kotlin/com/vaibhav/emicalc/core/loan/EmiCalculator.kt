package com.vaibhav.emicalc.core.loan

import com.vaibhav.emicalc.core.divideRate
import com.vaibhav.emicalc.core.toPaise
import com.vaibhav.emicalc.core.toRupees
import java.math.BigDecimal

/**
 * Monthly reducing-balance EMI and amortisation.
 *
 * Two conventions here are deliberate, because they are what makes output match a real
 * bank statement rather than merely being arithmetically defensible:
 *
 *  1. **The EMI is quoted in whole rupees.** Lenders do this, and it is why a schedule
 *     built from an unrounded EMI drifts away from the statement.
 *
 *  2. **The schedule is capped at the contracted number of instalments, and the final
 *     instalment absorbs whatever residue the rounding in (1) leaves behind.** Without
 *     this, a 240-month loan produces a spurious 241st instalment of a few rupees — the
 *     single most common reason a calculator disagrees with a lender.
 */
object EmiCalculator {

    private val MONTHS_PER_YEAR_PERCENT = BigDecimal("1200")

    /** Monthly rate as a fraction, e.g. 8% p.a. -> 0.00666666666667. */
    internal fun monthlyRate(annualRatePercent: BigDecimal): BigDecimal =
        annualRatePercent.divideRate(MONTHS_PER_YEAR_PERCENT)

    /**
     * The equated monthly instalment, rounded to whole rupees.
     *
     * `EMI = P * r * (1+r)^n / ((1+r)^n - 1)`, degenerating to `P / n` at zero interest.
     */
    fun emi(principal: BigDecimal, annualRatePercent: BigDecimal, tenureMonths: Int): BigDecimal {
        require(tenureMonths >= 1) { "tenureMonths must be >= 1, was $tenureMonths" }
        require(principal.signum() > 0) { "principal must be positive, was $principal" }

        val r = monthlyRate(annualRatePercent)
        if (r.signum() == 0) {
            return principal.divideRate(BigDecimal(tenureMonths)).toRupees()
        }
        val growth = (BigDecimal.ONE + r).pow(tenureMonths)
        return principal
            .multiply(r)
            .multiply(growth)
            .divideRate(growth - BigDecimal.ONE)
            .toRupees()
    }

    /**
     * Builds the full amortisation schedule, applying any prepayments and rate resets.
     *
     * A [PrepaymentMode.REDUCE_TENURE] prepayment holds the EMI, so the balance simply
     * runs out sooner. A [PrepaymentMode.REDUCE_EMI] prepayment re-solves the EMI over
     * the instalments still remaining in the original tenure.
     */
    fun amortise(loan: Loan): AmortisationResult {
        val rateByInstalment = loan.rateResets.associateBy { it.fromInstalment }
        val prepaymentByInstalment = loan.prepayments.associateBy { it.afterInstalment }

        var balance = loan.normalisedPrincipal
        var annualRate = loan.annualRatePercent
        var emi = emi(balance, annualRate, loan.tenureMonths)
        val scheduledEmi = emi

        var totalInterest = BigDecimal.ZERO.toPaise()
        var totalPrepaid = BigDecimal.ZERO.toPaise()
        val rows = ArrayList<Instalment>(loan.tenureMonths)

        // `horizon` is the contracted last instalment number. REDUCE_TENURE prepayments
        // let us finish early; they never push us past it.
        var horizon = loan.tenureMonths
        var n = 0

        while (balance.signum() > 0 && n < horizon) {
            n++

            rateByInstalment[n]?.let { reset ->
                annualRate = reset.annualRatePercent
                val remaining = horizon - n + 1
                if (reset.mode == PrepaymentMode.REDUCE_EMI && remaining >= 1) {
                    emi = emi(balance, annualRate, remaining)
                }
            }

            val interest = balance.multiply(monthlyRate(annualRate)).toPaise()

            val isFinal = n == horizon
            var principalPart: BigDecimal
            var payment: BigDecimal
            if (isFinal || emi - interest >= balance) {
                // Final instalment (or one large enough to close the loan): absorb the
                // residue instead of spilling into an extra month.
                principalPart = balance
                payment = (balance + interest).toPaise()
            } else {
                principalPart = (emi - interest).toPaise()
                payment = emi
            }

            balance = (balance - principalPart).toPaise()
            totalInterest = (totalInterest + interest).toPaise()

            var applied = BigDecimal.ZERO.toPaise()
            val prepayment = prepaymentByInstalment[n]
            if (prepayment != null && balance.signum() > 0) {
                applied = prepayment.amount.toPaise().coerceAtMost(balance)
                balance = (balance - applied).toPaise()
                totalPrepaid = (totalPrepaid + applied).toPaise()

                val remaining = horizon - n
                if (prepayment.mode == PrepaymentMode.REDUCE_EMI && balance.signum() > 0 && remaining >= 1) {
                    emi = emi(balance, annualRate, remaining)
                }
            }

            rows += Instalment(
                number = n,
                payment = payment,
                interest = interest,
                principal = principalPart,
                prepayment = applied,
                closingBalance = balance,
            )
        }

        check(balance.signum() == 0) {
            "schedule did not close: residual balance $balance after $n instalments"
        }

        return AmortisationResult(
            instalments = rows,
            scheduledEmi = scheduledEmi,
            finalEmi = rows.last().payment,
            totalInterest = totalInterest,
            totalPrepaid = totalPrepaid,
        )
    }
}
