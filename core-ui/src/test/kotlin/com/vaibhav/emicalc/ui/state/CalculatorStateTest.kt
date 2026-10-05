package com.vaibhav.emicalc.ui.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CalculatorStateTest {

    /** Feeds a string of keys through the reducer, as a user tapping the keypad would. */
    private fun type(keys: String, from: CalculatorState = CalculatorState()): CalcTransition {
        var transition = CalcTransition(from)
        for (c in keys) {
            val key = when {
                c.isDigit() -> CalcKey.Digit(c)
                c == '.' -> CalcKey.Dot
                c in "+-*/" -> CalcKey.Operator(c)
                c == '(' -> CalcKey.OpenParen
                c == ')' -> CalcKey.CloseParen
                c == '%' -> CalcKey.Percent
                c == '=' -> CalcKey.Equals
                c == '<' -> CalcKey.Backspace
                c == 'C' -> CalcKey.Clear
                else -> error("unsupported test key '$c'")
            }
            transition = CalculatorReducer.reduce(transition.state, key)
        }
        return transition
    }

    @Test
    @DisplayName("the preview updates live as a valid expression is typed")
    fun livePreview() {
        assertEquals("2", type("2").state.preview)
        assertEquals("", type("2+").state.preview, "an incomplete expression previews nothing")
        assertEquals("5", type("2+3").state.preview)
        assertEquals("14", type("2+3*4").state.preview)
    }

    @Test
    @DisplayName("an incomplete expression is not an error until = is pressed")
    fun errorsOnlyOnEquals() {
        assertNull(type("2+").state.error, "mid-typing is not an error state")
        assertNotNull(type("2+=").state.error, "pressing = on an incomplete expression is")
    }

    @Test
    @DisplayName("= produces an answer, formats it, and hands it back to be saved")
    fun equalsCommits() {
        val transition = type("1200*5%=")
        assertEquals(0, transition.state.lastAnswer!!.compareTo(BigDecimal("60")))
        assertEquals("60", transition.state.expression)
        assertTrue(transition.state.justEvaluated)

        val committed = transition.committed
        assertNotNull(committed)
        assertEquals("1200*5%", committed!!.expression, "history records what was typed")
        assertEquals(0, committed.result.compareTo(BigDecimal("60")))
    }

    @Test
    @DisplayName("large answers are grouped the Indian way")
    fun answerFormatting() {
        assertEquals("80,00,000", type("8000000=").state.expression)
        assertEquals("33,333.333333333333", type("100000/3=").state.expression)
    }

    @Test
    @DisplayName("typing a digit after = starts fresh, but an operator continues from the answer")
    fun continuingAfterEquals() {
        val answered = type("100=")

        val restarted = type("5", answered.state)
        assertEquals("5", restarted.state.expression, "a digit starts a new calculation")

        val continued = type("+5=", answered.state)
        assertEquals(
            0,
            continued.state.lastAnswer!!.compareTo(BigDecimal("105")),
            "an operator keeps the previous answer as the left operand",
        )
    }

    @Test
    @DisplayName("pressing two operators in a row replaces rather than stacks")
    fun operatorReplacement() {
        assertEquals("2*", type("2+*").state.expression)
        assertEquals("2/", type("2+-/").state.expression)
    }

    @Test
    @DisplayName("a minus after times or divide is kept, since it means a negative operand")
    fun negativeOperand() {
        assertEquals("2*-", type("2*-").state.expression)
        assertEquals(0, type("2*-3=").state.lastAnswer!!.compareTo(BigDecimal("-6")))
    }

    @Test
    fun backspaceAndClear() {
        assertEquals("12", type("123<").state.expression)
        assertEquals("", type("123<<<").state.expression)
        assertEquals("", type("123<<<<<").state.expression, "backspace past empty is harmless")

        val cleared = type("123=C")
        assertEquals("", cleared.state.expression)
        assertNull(cleared.state.error)
        assertEquals(
            0,
            cleared.state.lastAnswer!!.compareTo(BigDecimal("123")),
            "clear wipes the expression but keeps the last answer available",
        )
    }

    @Test
    @DisplayName("= on an empty expression does nothing and commits nothing")
    fun equalsOnEmpty() {
        val transition = type("=")
        assertEquals("", transition.state.expression)
        assertNull(transition.committed)
        assertNull(transition.state.error)
    }

    @Test
    @DisplayName("a division by zero reports an error and commits nothing")
    fun divisionByZero() {
        val transition = type("5/0=")
        assertNotNull(transition.state.error)
        assertNull(transition.committed, "a failed calculation must not reach history")
    }

    @Test
    @DisplayName("typing after an error clears it")
    fun errorClearsOnTyping() {
        val errored = type("2+=")
        assertNotNull(errored.state.error)
        assertNull(type("3", errored.state).state.error)
    }

    @Test
    @DisplayName("parentheses nest and preview correctly")
    fun parentheses() {
        assertEquals("54", type("((2+4)*(3+6))").state.preview)
        assertEquals(0, type("(2+3)*4=").state.lastAnswer!!.compareTo(BigDecimal("20")))
    }

    @Test
    fun canEvaluateReflectsContent() {
        assertTrue(!CalculatorState().canEvaluate)
        assertTrue(type("1").state.canEvaluate)
    }
}
