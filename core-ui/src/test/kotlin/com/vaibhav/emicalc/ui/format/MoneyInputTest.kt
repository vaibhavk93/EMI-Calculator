package com.vaibhav.emicalc.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyInputTest {

    private fun amount(raw: String): BigDecimal {
        val result = MoneyInput.parseAmount(raw)
        assertInstanceOf(InputResult.Valid::class.java, result, "expected '$raw' to parse")
        @Suppress("UNCHECKED_CAST")
        return (result as InputResult.Valid<BigDecimal>).value
    }

    private fun invalid(raw: String) =
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseAmount(raw), "expected '$raw' to fail")

    @Test
    @DisplayName("figures pasted off a payslip parse without being cleaned up first")
    fun tolerantOfRealInput() {
        assertEquals(0, amount("8000000").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("80,00,000").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("₹80,00,000").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("Rs. 80,00,000").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("  80 00 000  ").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("66915.50").compareTo(BigDecimal("66915.50")))
    }

    @Test
    @DisplayName("lakh, crore and thousand shorthands are understood")
    fun shorthands() {
        assertEquals(0, amount("80L").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("80 lakh").compareTo(BigDecimal("8000000")))
        assertEquals(0, amount("1.5Cr").compareTo(BigDecimal("15000000")))
        assertEquals(0, amount("2 crore").compareTo(BigDecimal("20000000")))
        assertEquals(0, amount("50k").compareTo(BigDecimal("50000")))
        assertEquals(0, amount("12 lac").compareTo(BigDecimal("1200000")))
    }

    @Test
    fun emptyIsNotInvalid() {
        assertEquals(InputResult.Empty, MoneyInput.parseAmount(""))
        assertEquals(InputResult.Empty, MoneyInput.parseAmount("   "))
        assertEquals(InputResult.Empty, MoneyInput.parseRate(""))
    }

    @Test
    fun rejectsNonsense() {
        invalid("abc")
        invalid("8,00,00,0.0.0")
        invalid("-5000")
        invalid("L")
    }

    @Test
    @DisplayName("zero is a valid amount but not a valid principal")
    fun zeroHandling() {
        assertEquals(0, amount("0").compareTo(BigDecimal.ZERO))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parsePositiveAmount("0"))
    }

    @Test
    fun rates() {
        val ok = MoneyInput.parseRate("8.5%")
        @Suppress("UNCHECKED_CAST")
        assertEquals(0, (ok as InputResult.Valid<BigDecimal>).value.compareTo(BigDecimal("8.5")))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseRate("-1"))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseRate("150"))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseRate("eight"))
    }

    @Test
    @DisplayName("tenure accepts bare months or a years shorthand")
    fun tenures() {
        fun months(raw: String): Int {
            val r = MoneyInput.parseTenureMonths(raw)
            assertInstanceOf(InputResult.Valid::class.java, r, "expected '$raw' to parse")
            @Suppress("UNCHECKED_CAST")
            return (r as InputResult.Valid<Int>).value
        }
        assertEquals(240, months("240"))
        assertEquals(240, months("20y"))
        assertEquals(240, months("20 years"))
        assertEquals(60, months("5 yr"))
        assertEquals(18, months("18 months"))
        assertEquals(18, months("18mo"))

        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseTenureMonths("0"))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseTenureMonths("601"))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseTenureMonths("soon"))
        assertEquals(InputResult.Empty, MoneyInput.parseTenureMonths(""))
    }

    @Test
    fun days() {
        assertInstanceOf(InputResult.Valid::class.java, MoneyInput.parseDays("120"))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseDays("-1"))
        assertInstanceOf(InputResult.Invalid::class.java, MoneyInput.parseDays("99999"))
    }

    @Test
    @DisplayName("what the formatter writes, the parser reads back unchanged")
    fun roundTrip() {
        listOf("0", "100", "66915", "8000000", "80000000", "1000000000").forEach { raw ->
            val formatted = MoneyFormat.group(BigDecimal(raw))
            assertEquals(0, amount(formatted).compareTo(BigDecimal(raw)), "round trip failed for $raw")
        }
    }
}
