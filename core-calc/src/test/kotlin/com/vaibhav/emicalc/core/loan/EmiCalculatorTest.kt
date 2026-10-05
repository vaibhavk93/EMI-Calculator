package com.vaibhav.emicalc.core.loan

import com.vaibhav.emicalc.core.assertMoney
import com.vaibhav.emicalc.core.money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class EmiCalculatorTest {

    private fun rate(v: String) = BigDecimal(v)

    // ---------------------------------------------------------------- EMI value

    @Test
    @DisplayName("80L at 8% over 240 months is 66,915 (published reference value)")
    fun goldenEightyLakh() {
        assertEquals(
            BigDecimal("66915"),
            EmiCalculator.emi(money(8_000_000), rate("8"), 240),
        )
    }

    @Test
    @DisplayName("50L at 8.5% over 240 months is 43,391 (published reference value)")
    fun goldenFiftyLakh() {
        assertEquals(
            BigDecimal("43391"),
            EmiCalculator.emi(money(5_000_000), rate("8.5"), 240),
        )
    }

    @Test
    @DisplayName("a zero-interest loan divides evenly and does not divide by zero")
    fun zeroInterest() {
        assertEquals(
            BigDecimal("10000"),
            EmiCalculator.emi(money(120_000), rate("0"), 12),
        )
    }

    @Test
    @DisplayName("a single-instalment loan is principal plus one month of interest")
    fun singleInstalment() {
        val loan = Loan("one", money(100_000), rate("12"), 1)
        val result = EmiCalculator.amortise(loan)
        assertEquals(1, result.months)
        assertMoney("1000", result.instalments.single().interest)
        assertMoney("101000", result.instalments.single().payment)
    }

    @Test
    fun rejectsNonsenseInput() {
        assertThrows<IllegalArgumentException> { Loan("x", money(0), rate("8"), 120) }
        assertThrows<IllegalArgumentException> { Loan("x", money(100), rate("-1"), 120) }
        assertThrows<IllegalArgumentException> { Loan("x", money(100), rate("8"), 0) }
        assertThrows<IllegalArgumentException> { Loan("x", money(100), rate("8"), 601) }
    }

    // -------------------------------------------------------- schedule closure

    @Test
    @DisplayName("the schedule never exceeds the contracted tenure and closes at exactly zero")
    fun scheduleClosesExactly() {
        val loan = Loan("home", money(8_000_000), rate("8"), 240)
        val result = EmiCalculator.amortise(loan)

        assertEquals(240, result.months, "must not spill into a 241st instalment")
        assertMoney("0", result.instalments.last().closingBalance)
        assertEquals(BigDecimal("66915"), result.scheduledEmi)
        // The final instalment absorbs the rounding residue, so it differs from the EMI.
        assertTrue(result.finalEmi > result.scheduledEmi, "final instalment absorbs residue")
    }

    @Test
    @DisplayName("closure holds across a wide spread of rates and tenures")
    fun closureIsRobust() {
        val principals = listOf(50_000, 250_000, 1_000_000, 8_000_000, 50_000_000)
        val rates = listOf("0", "0.5", "7.35", "8", "9.25", "14", "24", "36")
        val tenures = listOf(1, 2, 3, 6, 12, 13, 59, 120, 240, 360)

        for (p in principals) for (r in rates) for (t in tenures) {
            val result = EmiCalculator.amortise(Loan("l", money(p), rate(r), t))
            assertTrue(result.months <= t, "p=$p r=$r t=$t produced ${result.months} instalments")
            assertMoney(
                "0",
                result.instalments.last().closingBalance,
                "p=$p r=$r t=$t did not close at zero",
            )
        }
    }

    @Test
    @DisplayName("principal repaid over the schedule equals the amount borrowed")
    fun principalReconciles() {
        val loan = Loan("home", money(8_000_000), rate("8"), 240)
        val result = EmiCalculator.amortise(loan)
        val repaid = result.instalments.fold(BigDecimal.ZERO) { a, i -> a + i.principal + i.prepayment }
        assertMoney("8000000", repaid)
    }

    // ------------------------------------------------------------- prepayments

    @Test
    @DisplayName("reducing tenure always saves more interest than reducing EMI")
    fun tenureBeatsEmi() {
        val stream = { mode: PrepaymentMode ->
            (12..239 step 12).map { Prepayment(it, money(100_000), mode) }
        }
        val base = EmiCalculator.amortise(Loan("b", money(8_000_000), rate("8"), 240))
        val byTenure = EmiCalculator.amortise(
            Loan("t", money(8_000_000), rate("8"), 240, stream(PrepaymentMode.REDUCE_TENURE)),
        )
        val byEmi = EmiCalculator.amortise(
            Loan("e", money(8_000_000), rate("8"), 240, stream(PrepaymentMode.REDUCE_EMI)),
        )

        val savedByTenure = base.totalInterest - byTenure.totalInterest
        val savedByEmi = base.totalInterest - byEmi.totalInterest

        assertTrue(savedByTenure > savedByEmi, "tenure saved $savedByTenure, emi saved $savedByEmi")
        assertTrue(byTenure.months < 240, "reducing tenure must finish early, got ${byTenure.months}")
        assertEquals(240, byEmi.months, "reducing EMI must keep the tenure")
        assertTrue(byEmi.instalments.last().payment < byEmi.scheduledEmi, "EMI must fall")
    }

    @Test
    @DisplayName("a prepayment larger than the balance closes the loan without overpaying")
    fun oversizedPrepayment() {
        val loan = Loan("p", money(500_000), rate("10"), 60, listOf(Prepayment(6, money(10_000_000))))
        val result = EmiCalculator.amortise(loan)
        assertEquals(6, result.months)
        assertMoney("0", result.instalments.last().closingBalance)
        assertTrue(result.totalPrepaid < money(10_000_000), "must only take what is owed")
    }

    @Test
    @DisplayName("prepaying earlier saves more than prepaying the same amount later")
    fun earlierIsBetter() {
        val early = EmiCalculator.amortise(
            Loan("a", money(8_000_000), rate("8"), 240, listOf(Prepayment(12, money(500_000)))),
        )
        val late = EmiCalculator.amortise(
            Loan("b", money(8_000_000), rate("8"), 240, listOf(Prepayment(120, money(500_000)))),
        )
        assertTrue(early.totalInterest < late.totalInterest)
    }

    // ------------------------------------------------------------ rate resets

    @Test
    @DisplayName("a rate rise increases total interest; holding EMI keeps the tenure")
    fun rateReset() {
        val flat = EmiCalculator.amortise(Loan("f", money(8_000_000), rate("8"), 240))
        val risen = EmiCalculator.amortise(
            Loan(
                "r", money(8_000_000), rate("8"), 240,
                rateResets = listOf(RateReset(25, rate("9.5"), PrepaymentMode.REDUCE_EMI)),
            ),
        )
        assertTrue(risen.totalInterest > flat.totalInterest)
        assertEquals(240, risen.months)
        assertEquals(BigDecimal("0.00"), risen.instalments.last().closingBalance)
    }
}
