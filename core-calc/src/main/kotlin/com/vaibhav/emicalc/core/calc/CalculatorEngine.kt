package com.vaibhav.emicalc.core.calc

import com.vaibhav.emicalc.core.RATE_SCALE
import com.vaibhav.emicalc.core.toPaise
import java.math.BigDecimal
import java.math.RoundingMode

/** The outcome of evaluating an expression. Errors are values, not exceptions. */
sealed interface CalcResult {
    data class Ok(val value: BigDecimal) : CalcResult

    /** [position] is the character offset the parser stopped at, for caret placement. */
    data class Error(val message: String, val position: Int) : CalcResult
}

/**
 * A scratch calculator for the figures people work out while filling in a loan or a
 * settlement — "what is 12.5% of my basic", "what do these four allowances add up to".
 *
 * It exists because those numbers should flow into the forms and into history with a
 * note attached, not be lost in the phone's system calculator.
 *
 * Arithmetic is [BigDecimal] throughout, consistent with the rest of the module.
 * Supported: `+ - * /`, parentheses, unary minus, and a postfix `%` meaning "divide by
 * one hundred", so `1200*5%` is 60 and `5%` is 0.05.
 */
object CalculatorEngine {

    fun evaluate(input: String): CalcResult {
        val tokens = try {
            tokenise(input)
        } catch (e: ParseError) {
            return CalcResult.Error(e.message ?: "Invalid input", e.position)
        }
        if (tokens.isEmpty()) return CalcResult.Error("Nothing to calculate", 0)

        val parser = Parser(tokens)
        return try {
            val value = parser.parseExpression()
            parser.expectEnd()
            CalcResult.Ok(value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it })
        } catch (e: ParseError) {
            CalcResult.Error(e.message ?: "Invalid expression", e.position)
        }
    }

    /** Convenience for display: evaluates and rounds to paise, or null on error. */
    fun evaluateToMoney(input: String): BigDecimal? =
        (evaluate(input) as? CalcResult.Ok)?.value?.toPaise()

    // ------------------------------------------------------------- tokenising

    private class ParseError(override val message: String, val position: Int) : Exception(message)

    private sealed interface Token {
        val pos: Int

        data class Num(val value: BigDecimal, override val pos: Int) : Token
        data class Op(val symbol: Char, override val pos: Int) : Token
        data class Paren(val open: Boolean, override val pos: Int) : Token
        data class Percent(override val pos: Int) : Token
    }

    private fun tokenise(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() || c == ',' || c == '_' -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    val digits = StringBuilder()
                    var seenDot = false
                    while (i < input.length) {
                        val ch = input[i]
                        when {
                            ch.isDigit() -> { digits.append(ch); i++ }
                            ch == '.' && !seenDot -> { seenDot = true; digits.append(ch); i++ }
                            // A grouping separator inside a number, as in 80,00,000.
                            (ch == ',' || ch == '_') &&
                                i + 1 < input.length && input[i + 1].isDigit() -> i++
                            else -> break
                        }
                    }
                    val text = digits.toString()
                    if (text.isEmpty() || text == ".") throw ParseError("Expected a number", start)
                    tokens += Token.Num(BigDecimal(text), start)
                }
                c in "+-*/" -> { tokens += Token.Op(c, i); i++ }
                // Accept the characters phone keypads actually produce.
                c == '×' -> { tokens += Token.Op('*', i); i++ }
                c == '÷' -> { tokens += Token.Op('/', i); i++ }
                c == '%' -> { tokens += Token.Percent(i); i++ }
                c == '(' -> { tokens += Token.Paren(true, i); i++ }
                c == ')' -> { tokens += Token.Paren(false, i); i++ }
                else -> throw ParseError("Unexpected character '$c'", i)
            }
        }
        return tokens
    }

    // ---------------------------------------------------------------- parsing

    private class Parser(private val tokens: List<Token>) {
        private var index = 0

        private fun peek(): Token? = tokens.getOrNull(index)
        private fun endPos(): Int = tokens.lastOrNull()?.pos?.plus(1) ?: 0

        fun expectEnd() {
            peek()?.let { throw ParseError("Unexpected input", it.pos) }
        }

        /** expression := term (('+' | '-') term)* */
        fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.symbol == '+' || t.symbol == '-')) {
                    index++
                    val rhs = parseTerm()
                    value = if (t.symbol == '+') value + rhs else value - rhs
                } else {
                    return value
                }
            }
        }

        /** term := unary (('*' | '/') unary)* */
        private fun parseTerm(): BigDecimal {
            var value = parseUnary()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.symbol == '*' || t.symbol == '/')) {
                    index++
                    val rhs = parseUnary()
                    value = if (t.symbol == '*') {
                        value * rhs
                    } else {
                        if (rhs.signum() == 0) throw ParseError("Cannot divide by zero", t.pos)
                        value.divide(rhs, RATE_SCALE, RoundingMode.HALF_UP)
                    }
                } else {
                    return value
                }
            }
        }

        /** unary := ('+' | '-')* postfix */
        private fun parseUnary(): BigDecimal {
            val t = peek()
            if (t is Token.Op && (t.symbol == '-' || t.symbol == '+')) {
                index++
                val operand = parseUnary()
                return if (t.symbol == '-') operand.negate() else operand
            }
            return parsePostfix()
        }

        /** postfix := primary '%'* */
        private fun parsePostfix(): BigDecimal {
            var value = parsePrimary()
            while (peek() is Token.Percent) {
                index++
                value = value.divide(BigDecimal(100), RATE_SCALE, RoundingMode.HALF_UP)
            }
            return value
        }

        /** primary := number | '(' expression ')' */
        private fun parsePrimary(): BigDecimal {
            val t = peek() ?: throw ParseError("Expression is incomplete", endPos())
            return when (t) {
                is Token.Num -> { index++; t.value }
                is Token.Paren -> {
                    if (!t.open) throw ParseError("Unmatched ')'", t.pos)
                    index++
                    val inner = parseExpression()
                    val closing = peek()
                    if (closing !is Token.Paren || closing.open) {
                        throw ParseError("Missing ')'", closing?.pos ?: endPos())
                    }
                    index++
                    inner
                }
                is Token.Percent -> throw ParseError("'%' needs a number before it", t.pos)
                is Token.Op -> throw ParseError("Expected a number", t.pos)
            }
        }
    }
}
