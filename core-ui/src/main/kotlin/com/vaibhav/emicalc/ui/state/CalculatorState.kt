package com.vaibhav.emicalc.ui.state

import com.vaibhav.emicalc.core.calc.CalcResult
import com.vaibhav.emicalc.core.calc.CalculatorEngine
import com.vaibhav.emicalc.ui.format.MoneyFormat
import java.math.BigDecimal

/** Every key the calculator keypad can send. */
sealed interface CalcKey {
    data class Digit(val value: Char) : CalcKey
    data object Dot : CalcKey
    data class Operator(val symbol: Char) : CalcKey
    data object OpenParen : CalcKey
    data object CloseParen : CalcKey
    data object Percent : CalcKey
    data object Backspace : CalcKey
    data object Clear : CalcKey
    data object Equals : CalcKey
}

/**
 * What the calculator screen shows.
 *
 * [preview] is the live result of a well-formed expression, shown greyed above the
 * keypad so the answer appears as you type rather than only on `=`. [error] is only set
 * once the user presses `=`, because flagging an error mid-typing — when the expression
 * is merely incomplete — is noise.
 */
data class CalculatorState(
    val expression: String = "",
    val preview: String = "",
    val error: String? = null,
    val lastAnswer: BigDecimal? = null,
    val justEvaluated: Boolean = false,
) {
    val canEvaluate: Boolean get() = expression.isNotBlank()
}

/** The result of pressing a key: the new state, plus anything the host should persist. */
data class CalcTransition(
    val state: CalculatorState,
    /** Set when `=` produced an answer, so the host can write it to history. */
    val committed: CommittedCalculation? = null,
)

data class CommittedCalculation(val expression: String, val result: BigDecimal)

/**
 * The calculator's behaviour, as a pure function of state and key.
 *
 * Keeping this out of the Compose layer means the fiddly parts — what `=` does to a
 * result you then keep typing on, whether an operator replaces the previous one — are
 * unit-tested rather than discovered by hand on a device.
 */
object CalculatorReducer {

    fun reduce(state: CalculatorState, key: CalcKey): CalcTransition = when (key) {
        is CalcKey.Digit -> typed(state, key.value.toString())
        CalcKey.Dot -> typed(state, ".")
        CalcKey.OpenParen -> typed(state, "(")
        CalcKey.CloseParen -> append(state, ")")
        CalcKey.Percent -> append(state, "%")

        is CalcKey.Operator -> {
            // Continuing from a result keeps it as the left operand.
            val base = if (state.justEvaluated) {
                state.copy(justEvaluated = false)
            } else {
                state
            }
            val trimmed = base.expression.trimEnd()
            val replaced = if (trimmed.isNotEmpty() && trimmed.last() in "+-*/") {
                // Pressing two operators in a row replaces rather than stacks, except
                // for a minus after * or /, which is a legitimate negative operand.
                if (key.symbol == '-' && trimmed.last() in "*/") {
                    trimmed + key.symbol
                } else {
                    trimmed.dropLast(1) + key.symbol
                }
            } else {
                trimmed + key.symbol
            }
            CalcTransition(recompute(base.copy(expression = replaced, error = null)))
        }

        CalcKey.Backspace -> {
            val next = state.expression.dropLast(1)
            CalcTransition(recompute(state.copy(expression = next, error = null, justEvaluated = false)))
        }

        CalcKey.Clear -> CalcTransition(CalculatorState(lastAnswer = state.lastAnswer))

        CalcKey.Equals -> {
            if (state.expression.isBlank()) {
                CalcTransition(state)
            } else {
                when (val result = CalculatorEngine.evaluate(state.expression)) {
                    is CalcResult.Ok -> CalcTransition(
                        state = CalculatorState(
                            // Same formatter as the live preview, so the number does not
                            // change shape the moment = is pressed.
                            expression = format(result.value),
                            preview = "",
                            error = null,
                            lastAnswer = result.value,
                            justEvaluated = true,
                        ),
                        committed = CommittedCalculation(state.expression, result.value),
                    )
                    is CalcResult.Error -> CalcTransition(state.copy(error = result.message))
                }
            }
        }
    }

    /** Typing a value right after `=` starts a new calculation rather than appending. */
    private fun typed(state: CalculatorState, text: String): CalcTransition {
        val base = if (state.justEvaluated) CalculatorState(lastAnswer = state.lastAnswer) else state
        return append(base, text)
    }

    private fun append(state: CalculatorState, text: String): CalcTransition =
        CalcTransition(
            recompute(
                state.copy(
                    expression = state.expression + text,
                    error = null,
                    justEvaluated = false,
                ),
            ),
        )

    /** Refreshes the live preview, leaving it blank while the expression is incomplete. */
    private fun recompute(state: CalculatorState): CalculatorState {
        if (state.expression.isBlank()) return state.copy(preview = "")
        return when (val result = CalculatorEngine.evaluate(state.expression)) {
            is CalcResult.Ok -> state.copy(preview = format(result.value))
            is CalcResult.Error -> state.copy(preview = "")
        }
    }

    private fun format(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            MoneyFormat.group(stripped)
        } else {
            val whole = stripped.toBigInteger().toString()
            val fraction = stripped.toPlainString().substringAfter('.', "")
            val sign = if (stripped.signum() < 0) "-" else ""
            val digits = MoneyFormat.groupDigits(whole.removePrefix("-"))
            if (fraction.isEmpty()) "$sign$digits" else "$sign$digits.$fraction"
        }
    }
}
