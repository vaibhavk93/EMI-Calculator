package com.vaibhav.emicalc.core.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    private fun ok(expr: String): BigDecimal {
        val result = CalculatorEngine.evaluate(expr)
        assertInstanceOf(CalcResult.Ok::class.java, result, "expected '$expr' to evaluate")
        return (result as CalcResult.Ok).value
    }

    private fun err(expr: String): CalcResult.Error {
        val result = CalculatorEngine.evaluate(expr)
        assertInstanceOf(CalcResult.Error::class.java, result, "expected '$expr' to fail")
        return result as CalcResult.Error
    }

    @Test
    fun arithmetic() {
        assertEquals(BigDecimal("4"), ok("2+2"))
        assertEquals(BigDecimal("6"), ok("2*3"))
        assertEquals(BigDecimal("2.5"), ok("5/2"))
        assertEquals(BigDecimal("-3"), ok("2-5"))
    }

    @Test
    @DisplayName("multiplication binds tighter than addition")
    fun precedence() {
        assertEquals(BigDecimal("14"), ok("2+3*4"))
        assertEquals(BigDecimal("20"), ok("(2+3)*4"))
        assertEquals(BigDecimal("2"), ok("2+3*4-4*3"))
    }

    @Test
    @DisplayName("percent is postfix and divides by one hundred")
    fun percent() {
        assertEquals(BigDecimal("0.05"), ok("5%"))
        assertEquals(BigDecimal("60"), ok("1200*5%"))
        assertEquals(BigDecimal("7500"), ok("50000*15%"))
    }

    @Test
    @DisplayName("unary minus works, including stacked and after an operator")
    fun unaryMinus() {
        assertEquals(BigDecimal("-5"), ok("-5"))
        assertEquals(BigDecimal("5"), ok("--5"))
        assertEquals(BigDecimal("3"), ok("8+-5"))
        assertEquals(BigDecimal("-20"), ok("-(4*5)"))
    }

    @Test
    @DisplayName("grouping separators people actually type are ignored")
    fun separators() {
        assertEquals(BigDecimal("8000000"), ok("80,00,000"))
        assertEquals(BigDecimal("150000"), ok("1,00,000 + 50,000"))
    }

    @Test
    @DisplayName("keypad multiplication and division signs are accepted")
    fun keypadGlyphs() {
        assertEquals(BigDecimal("12"), ok("3×4"))
        assertEquals(BigDecimal("4"), ok("12÷3"))
    }

    @Test
    @DisplayName("nested parentheses evaluate correctly")
    fun nesting() {
        assertEquals(BigDecimal("54"), ok("((2+4)*(3+6))"))
    }

    @Test
    @DisplayName("decimal arithmetic is exact, unlike binary floating point")
    fun exactDecimals() {
        // 0.1 + 0.2 is 0.30000000000000004 in a Double. It must be exactly 0.3 here.
        assertEquals(BigDecimal("0.3"), ok("0.1+0.2"))
        assertEquals(0, ok("0.1+0.2").compareTo(BigDecimal("0.3")))
    }

    @Test
    fun errorsAreValuesNotExceptions() {
        assertTrue(err("5/0").message.contains("zero", ignoreCase = true))
        assertEquals(0, err("").position)
        err("2+")
        err("(2+3")
        err("2+3)")
        err("2**3")
        err("%5")
        err("2 @ 3")
    }

    @Test
    @DisplayName("an error reports where it happened so the caret can be placed")
    fun errorPosition() {
        assertEquals(4, err("12+3@").position)
    }

    @Test
    @DisplayName("money helper rounds to paise and returns null on error")
    fun moneyHelper() {
        assertEquals(BigDecimal("33333.33"), CalculatorEngine.evaluateToMoney("100000/3"))
        assertEquals(null, CalculatorEngine.evaluateToMoney("1/0"))
    }
}
