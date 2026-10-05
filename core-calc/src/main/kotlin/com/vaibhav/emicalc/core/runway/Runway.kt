package com.vaibhav.emicalc.core.runway

import com.vaibhav.emicalc.core.coerceAtLeastZero
import com.vaibhav.emicalc.core.divideRate
import com.vaibhav.emicalc.core.loan.PayoffOption
import com.vaibhav.emicalc.core.loan.PayoffStrategy
import com.vaibhav.emicalc.core.loan.Portfolio
import com.vaibhav.emicalc.core.toPaise
import java.math.BigDecimal
import java.math.RoundingMode

/** What the user has to live on, and what it has to cover. */
data class RunwayInput(
    val netSettlement: BigDecimal,
    val liquidSavings: BigDecimal = BigDecimal.ZERO,
    val monthlyLivingCost: BigDecimal = BigDecimal.ZERO,
    /** Pay from a new job, if one is already lined up. */
    val expectedMonthlyIncome: BigDecimal = BigDecimal.ZERO,
) {
    init {
        require(netSettlement.signum() >= 0) { "netSettlement cannot be negative" }
        require(liquidSavings.signum() >= 0) { "liquidSavings cannot be negative" }
        require(monthlyLivingCost.signum() >= 0) { "monthlyLivingCost cannot be negative" }
    }
}

/**
 * How long the money lasts.
 *
 * [months] is deliberately whole months floored, not rounded: telling somebody between
 * jobs they have "6 months" when they have five and a half is the one rounding error
 * here with real consequences.
 */
data class RunwayResult(
    val totalAvailable: BigDecimal,
    val monthlyEmi: BigDecimal,
    val monthlyLivingCost: BigDecimal,
    val expectedMonthlyIncome: BigDecimal,
    val netMonthlyBurn: BigDecimal,
    val months: Int,
    val trailingDays: Int,
    val indefinite: Boolean,
    val emiOnlyMonths: Int,
    val bestPrepayment: PayoffOption?,
)

/**
 * The screen that makes a loan calculator and a settlement calculator one product:
 * the payout on one side, the obligations it has to cover on the other.
 */
object RunwayCalculator {

    private val DAYS_PER_MONTH = BigDecimal(30)

    fun compute(
        input: RunwayInput,
        portfolio: Portfolio,
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
    ): RunwayResult {
        val available = (input.netSettlement + input.liquidSavings).toPaise()
        val emi = portfolio.totalMonthlyEmi
        val burn = (emi + input.monthlyLivingCost - input.expectedMonthlyIncome)
            .toPaise()
            .coerceAtLeastZero()

        val indefinite = burn.signum() == 0
        val exactMonths = if (indefinite) BigDecimal.ZERO else available.divideRate(burn)
        val whole = if (indefinite) 0 else exactMonths.setScale(0, RoundingMode.FLOOR).toInt()
        val trailingDays = if (indefinite) {
            0
        } else {
            exactMonths.subtract(BigDecimal(whole)).multiply(DAYS_PER_MONTH)
                .setScale(0, RoundingMode.FLOOR).toInt()
        }

        val emiOnly = if (emi.signum() == 0) 0 else available.divideRate(emi)
            .setScale(0, RoundingMode.FLOOR).toInt()

        val best = if (available.signum() > 0 && portfolio.loans.isNotEmpty()) {
            portfolio.payoffOptions(available, strategy).firstOrNull()
        } else {
            null
        }

        return RunwayResult(
            totalAvailable = available,
            monthlyEmi = emi,
            monthlyLivingCost = input.monthlyLivingCost.toPaise(),
            expectedMonthlyIncome = input.expectedMonthlyIncome.toPaise(),
            netMonthlyBurn = burn,
            months = whole,
            trailingDays = trailingDays,
            indefinite = indefinite,
            emiOnlyMonths = emiOnly,
            bestPrepayment = best,
        )
    }
}
