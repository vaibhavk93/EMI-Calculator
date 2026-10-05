package com.vaibhav.emicalc.core.fnf

import com.vaibhav.emicalc.core.assertMoney
import com.vaibhav.emicalc.core.money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class IncomeTaxTest {

    private val config = StatutoryConfig.DEFAULT

    @Test
    @DisplayName("12.75L gross is tax free: 75,000 standard deduction then the 87A rebate")
    fun zeroTaxAtTheRebateCeiling() {
        val tax = IncomeTax.compute(money(1_275_000), config)
        assertEquals(money(1_200_000), tax.taxableIncome)
        assertMoney("60000", tax.slabTax)
        assertMoney("60000", tax.rebate87A)
        assertMoney("0", tax.total)
    }

    @Test
    @DisplayName("12L gross is tax free, with the rebate only partly used")
    fun belowTheCeiling() {
        val tax = IncomeTax.compute(money(1_200_000), config)
        assertMoney("52500", tax.slabTax)
        assertMoney("0", tax.total)
    }

    @Test
    @DisplayName("marginal relief caps the tax at the income earned above 12L")
    fun marginalRelief() {
        // Taxable 12.10L: slab tax is 61,500 but only 10,000 was earned above the
        // threshold, so relief brings the liability down to that 10,000 plus cess.
        val tax = IncomeTax.compute(money(1_285_000), config)
        assertEquals(money(1_210_000), tax.taxableIncome)
        assertMoney("61500", tax.slabTax)
        assertTrue(tax.marginalRelief.signum() > 0, "relief must apply just above the threshold")
        assertMoney("10400", tax.total, "10,000 plus 4% cess")
    }

    @Test
    @DisplayName("crossing the rebate threshold never costs more than the extra income")
    fun noCliffAtTheThreshold() {
        var previous = BigDecimal.ZERO
        var gross = money(1_270_000)
        val step = money(5_000)
        repeat(30) {
            val computed = IncomeTax.compute(gross, config)
            // Relief is granted on tax before cess, so the pre-cess liability is the
            // figure the no-cliff guarantee actually applies to.
            val beforeCess = computed.total - computed.cess
            assertTrue(
                beforeCess - previous <= step,
                "pre-cess tax jumped by more than the income did at gross $gross",
            )
            previous = beforeCess
            gross = (gross + step)
        }
    }

    @Test
    @DisplayName("tax rises monotonically with income across a wide sweep")
    fun monotonic() {
        var previous = BigDecimal.valueOf(-1)
        var gross = money(100_000)
        while (gross <= money(30_000_000)) {
            val tax = IncomeTax.compute(gross, config).total
            assertTrue(tax >= previous, "tax fell as income rose at $gross")
            previous = tax
            gross = (gross + money(100_000))
        }
    }

    @Test
    @DisplayName("slab arithmetic matches a hand computation at 16L taxable")
    fun slabGolden() {
        // 4-8L at 5% = 20,000; 8-12L at 10% = 40,000; 12-16L at 15% = 60,000.
        assertMoney("120000", IncomeTax.slabTax(money(1_600_000), config.slabs))
    }

    @Test
    @DisplayName("surcharge applies above 50L")
    fun surchargeApplies() {
        val below = IncomeTax.compute(money(5_000_000), config)
        val above = IncomeTax.compute(money(6_000_000), config)
        assertMoney("0", below.surcharge)
        assertTrue(above.surcharge.signum() > 0)
    }

    @Test
    @DisplayName("zero and tiny incomes produce no tax and do not go negative")
    fun degenerateIncomes() {
        assertMoney("0", IncomeTax.compute(BigDecimal.ZERO, config).total)
        assertMoney("0", IncomeTax.compute(money(50_000), config).total)
        assertMoney("0", IncomeTax.compute(money(60_000), config).taxableIncome)
    }
}
