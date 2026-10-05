package com.vaibhav.emicalc.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class MoneyFormatTest {

    private fun bd(v: String) = BigDecimal(v)

    @Test
    @DisplayName("digits group the Indian way: last three, then pairs")
    fun indianGrouping() {
        assertEquals("0", MoneyFormat.group(bd("0")))
        assertEquals("7", MoneyFormat.group(bd("7")))
        assertEquals("100", MoneyFormat.group(bd("100")))
        assertEquals("1,000", MoneyFormat.group(bd("1000")))
        assertEquals("66,915", MoneyFormat.group(bd("66915")))
        assertEquals("1,00,000", MoneyFormat.group(bd("100000")))
        assertEquals("50,00,000", MoneyFormat.group(bd("5000000")))
        assertEquals("80,00,000", MoneyFormat.group(bd("8000000")))
        assertEquals("1,00,00,000", MoneyFormat.group(bd("10000000")))
        assertEquals("8,00,00,000", MoneyFormat.group(bd("80000000")))
        assertEquals("1,00,00,00,000", MoneyFormat.group(bd("1000000000")))
    }

    @Test
    @DisplayName("the sign sits outside the digits, and outside the rupee symbol")
    fun negatives() {
        assertEquals("-50,000", MoneyFormat.group(bd("-50000")))
        assertEquals("-₹50,000", MoneyFormat.rupees(bd("-50000")))
        assertEquals("₹50,000", MoneyFormat.rupees(bd("50000")))
    }

    @Test
    @DisplayName("amounts round to whole rupees for display")
    fun rounding() {
        assertEquals("₹67,036", MoneyFormat.rupees(bd("67035.95")))
        assertEquals("₹1,000", MoneyFormat.rupees(bd("999.50")))
    }

    @Test
    @DisplayName("paise are kept where a row actually has them")
    fun paise() {
        assertEquals("₹67,035.95", MoneyFormat.rupeesWithPaise(bd("67035.95")))
        assertEquals("₹1,00,000.00", MoneyFormat.rupeesWithPaise(bd("100000")))
        assertEquals("-₹121.76", MoneyFormat.rupeesWithPaise(bd("-121.76")))
    }

    @Test
    @DisplayName("headline figures shorten to lakh and crore, but small ones stay in full")
    fun compact() {
        assertEquals("₹66,915", MoneyFormat.compact(bd("66915")), "below a lakh stays written out")
        assertEquals("₹1.0 L", MoneyFormat.compact(bd("100000")))
        assertEquals("₹80.0 L", MoneyFormat.compact(bd("8000000")))
        assertEquals("₹1.0 Cr", MoneyFormat.compact(bd("10000000")))
        assertEquals("₹8.2 Cr", MoneyFormat.compact(bd("82000000")))
        assertEquals("-₹1.0 Cr", MoneyFormat.compact(bd("-10000000")))
    }

    @Test
    @DisplayName("grouping is self-consistent for every digit length up to a thousand crore")
    fun groupingIsConsistent() {
        var digits = "1"
        repeat(12) {
            val grouped = MoneyFormat.groupDigits(digits)
            assertEquals(
                digits,
                grouped.replace(",", ""),
                "grouping lost or added digits for '$digits'",
            )
            digits += "0"
        }
    }

    @Test
    fun tenures() {
        assertEquals("0 mo", TenureFormat.short(0))
        assertEquals("1 mo", TenureFormat.short(1))
        assertEquals("11 mo", TenureFormat.short(11))
        assertEquals("1 yr", TenureFormat.short(12))
        assertEquals("1 yr 9 mo", TenureFormat.short(21))
        assertEquals("20 yr", TenureFormat.short(240))
        assertEquals("15 yr 9 mo", TenureFormat.short(189))

        assertEquals("1 year", TenureFormat.long(12))
        assertEquals("1 year 1 month", TenureFormat.long(13))
        assertEquals("20 years", TenureFormat.long(240))
        assertEquals("5 months", TenureFormat.long(5))
    }

    @Test
    fun tenuresRejectNegatives() {
        assertThrows<IllegalArgumentException> { TenureFormat.short(-1) }
        assertThrows<IllegalArgumentException> { TenureFormat.long(-1) }
    }

    @Test
    @DisplayName("rates drop meaningless trailing zeros but keep real decimals")
    fun rates() {
        assertEquals("8%", RateFormat.percent(bd("8")))
        assertEquals("8%", RateFormat.percent(bd("8.00")))
        assertEquals("8.5%", RateFormat.percent(bd("8.50")))
        assertEquals("9.25%", RateFormat.percent(bd("9.25")))
        assertEquals("0%", RateFormat.percent(bd("0")))
        assertEquals("0%", RateFormat.percent(bd("0.00")))
    }
}
