package com.vaibhav.emicalc.core.runway

import com.vaibhav.emicalc.core.loan.Loan
import com.vaibhav.emicalc.core.loan.PayoffStrategy
import com.vaibhav.emicalc.core.loan.Portfolio
import com.vaibhav.emicalc.core.assertMoney
import com.vaibhav.emicalc.core.money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RunwayTest {

    private fun rate(v: String) = BigDecimal(v)

    private val home = Loan("Home", money(5_000_000), rate("8.5"), 240)
    private val car = Loan("Car", money(800_000), rate("9.5"), 60)
    private val personal = Loan("Personal", money(300_000), rate("16"), 36)
    private val portfolio = Portfolio(listOf(home, car, personal))

    // ------------------------------------------------------------ portfolio view

    @Test
    @DisplayName("the portfolio aggregates what single-loan calculators cannot")
    fun aggregates() {
        assertMoney("6100000", portfolio.totalOutstanding)
        assertEquals(240, portfolio.debtFreeInMonths, "driven by the longest loan")
        assertTrue(portfolio.totalMonthlyEmi > money(60_000))
        assertEquals(3, portfolio.summaries.size)
    }

    @Test
    @DisplayName("avalanche ranks by interest actually saved, not by headline rate")
    fun avalancheRanking() {
        val options = portfolio.payoffOptions(money(300_000), PayoffStrategy.AVALANCHE)
        assertEquals(3, options.size)
        // Sorted best-first, and every option saves something.
        assertTrue(options.first().interestSaved >= options.last().interestSaved)
        assertTrue(options.all { it.interestSaved.signum() > 0 })
    }

    @Test
    @DisplayName("snowball ranks by smallest balance instead")
    fun snowballRanking() {
        val options = portfolio.payoffOptions(money(300_000), PayoffStrategy.SNOWBALL)
        assertEquals("Personal", options.first().loanName, "smallest balance first")
    }

    @Test
    @DisplayName("a lump sum never applies more than a loan actually owes")
    fun lumpSumIsCapped() {
        val options = portfolio.payoffOptions(money(10_000_000))
        assertTrue(options.all { it.amountApplied <= it.let { o -> portfolio.loans.first { l -> l.name == o.loanName }.normalisedPrincipal } })
    }

    // ----------------------------------------------------------------- runway

    @Test
    @DisplayName("runway is settlement plus savings divided by monthly burn, floored")
    fun basicRunway() {
        val result = RunwayCalculator.compute(
            RunwayInput(
                netSettlement = money(600_000),
                liquidSavings = money(400_000),
                monthlyLivingCost = money(50_000),
            ),
            Portfolio(listOf(Loan("Car", money(500_000), rate("9"), 60))),
        )
        // 10,00,000 available; EMI ~10,379 + 50,000 living = ~60,379/month -> 16 months.
        assertMoney("1000000", result.totalAvailable)
        assertEquals(16, result.months)
        assertTrue(result.trailingDays in 0..30)
        assertTrue(!result.indefinite)
    }

    @Test
    @DisplayName("months are floored, never rounded up")
    fun flooredNotRounded() {
        val result = RunwayCalculator.compute(
            RunwayInput(netSettlement = money(599_000), monthlyLivingCost = money(100_000)),
            Portfolio(emptyList()),
        )
        assertEquals(5, result.months, "5.99 months must report as 5, not 6")
        assertTrue(result.trailingDays > 0, "the part month is reported separately")
    }

    @Test
    @DisplayName("income covering the burn means the runway does not run out")
    fun indefiniteRunway() {
        val result = RunwayCalculator.compute(
            RunwayInput(
                netSettlement = money(500_000),
                monthlyLivingCost = money(40_000),
                expectedMonthlyIncome = money(200_000),
            ),
            Portfolio(listOf(car)),
        )
        assertTrue(result.indefinite)
        assertMoney("0", result.netMonthlyBurn)
        assertEquals(0, result.months)
    }

    @Test
    @DisplayName("EMI-only runway is reported separately, for the worst case")
    fun emiOnlyRunway() {
        val result = RunwayCalculator.compute(
            RunwayInput(netSettlement = money(1_000_000), monthlyLivingCost = money(50_000)),
            portfolio,
        )
        assertTrue(
            result.emiOnlyMonths > result.months,
            "covering only EMIs must last longer than covering EMIs and living costs",
        )
    }

    @Test
    @DisplayName("the runway suggests the best use of the payout")
    fun suggestsPrepayment() {
        val result = RunwayCalculator.compute(
            RunwayInput(netSettlement = money(300_000), monthlyLivingCost = money(50_000)),
            portfolio,
        )
        assertTrue(result.bestPrepayment!!.interestSaved.signum() > 0)
    }

    @Test
    @DisplayName("no loans and no payout degrade gracefully")
    fun degenerate() {
        val result = RunwayCalculator.compute(
            RunwayInput(netSettlement = BigDecimal.ZERO),
            Portfolio(emptyList()),
        )
        assertEquals(0, result.months)
        assertEquals(0, result.emiOnlyMonths)
        assertNull(result.bestPrepayment)
        assertTrue(result.indefinite, "nothing to pay means nothing runs out")
    }
}
