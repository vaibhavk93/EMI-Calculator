package com.vaibhav.emicalc.ui.format

import java.math.BigDecimal

/** The outcome of reading a value a user typed into a field. */
sealed interface InputResult<out T> {
    data class Valid<T>(val value: T) : InputResult<T>
    data class Invalid(val reason: String) : InputResult<Nothing>
    /** The field is empty, which is different from being wrong. */
    data object Empty : InputResult<Nothing>
}

/**
 * Parses what people actually type into money and rate fields.
 *
 * Users paste figures off payslips and bank statements, so the input arrives with rupee
 * signs, commas, spaces and occasionally a `L` or `Cr` suffix. Rejecting all of that and
 * demanding bare digits is the kind of friction that gets an app deleted.
 */
object MoneyInput {

    private val LAKH = BigDecimal("100000")
    private val CRORE = BigDecimal("10000000")

    fun parseAmount(raw: String): InputResult<BigDecimal> {
        var text = raw.trim()
            .removePrefix("₹")
            .removePrefix("Rs.")
            .removePrefix("Rs")
            .trim()
            .replace(",", "")
            .replace("_", "")
            .replace(" ", "")
        if (text.isEmpty()) return InputResult.Empty

        var multiplier = BigDecimal.ONE
        val upper = text.uppercase()
        when {
            upper.endsWith("CR") -> { multiplier = CRORE; text = text.dropLast(2) }
            upper.endsWith("CRORE") -> { multiplier = CRORE; text = text.dropLast(5) }
            upper.endsWith("LAKH") -> { multiplier = LAKH; text = text.dropLast(4) }
            upper.endsWith("LAC") -> { multiplier = LAKH; text = text.dropLast(3) }
            upper.endsWith("L") -> { multiplier = LAKH; text = text.dropLast(1) }
            upper.endsWith("K") -> { multiplier = BigDecimal("1000"); text = text.dropLast(1) }
        }
        text = text.trim()
        if (text.isEmpty()) return InputResult.Invalid("Enter an amount")

        val parsed = try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            return InputResult.Invalid("Not a number")
        }
        if (parsed.signum() < 0) return InputResult.Invalid("Cannot be negative")

        return InputResult.Valid(parsed.multiply(multiplier))
    }

    /** Like [parseAmount] but rejects zero, for fields where zero makes no sense. */
    fun parsePositiveAmount(raw: String): InputResult<BigDecimal> =
        when (val result = parseAmount(raw)) {
            is InputResult.Valid ->
                if (result.value.signum() <= 0) InputResult.Invalid("Must be more than zero") else result
            else -> result
        }

    fun parseRate(raw: String): InputResult<BigDecimal> {
        val text = raw.trim().removeSuffix("%").trim()
        if (text.isEmpty()) return InputResult.Empty
        val parsed = try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            return InputResult.Invalid("Not a number")
        }
        return when {
            parsed.signum() < 0 -> InputResult.Invalid("Cannot be negative")
            parsed > BigDecimal("100") -> InputResult.Invalid("Rate looks too high")
            else -> InputResult.Valid(parsed)
        }
    }

    /** Reads a tenure, accepting either months or a `"5y"` / `"5 years"` shorthand. */
    fun parseTenureMonths(raw: String): InputResult<Int> {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return InputResult.Empty

        val years = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:y|yr|yrs|year|years)$").find(text)
        val months = Regex("^(\\d+)\\s*(?:m|mo|month|months)?$").find(text)

        val value = when {
            years != null -> BigDecimal(years.groupValues[1]).multiply(BigDecimal(12)).toInt()
            months != null -> months.groupValues[1].toIntOrNull() ?: return InputResult.Invalid("Not a number")
            else -> return InputResult.Invalid("Enter months, or e.g. 20y")
        }
        return when {
            value < 1 -> InputResult.Invalid("Must be at least 1 month")
            value > 600 -> InputResult.Invalid("Cannot exceed 600 months")
            else -> InputResult.Valid(value)
        }
    }

    fun parseDays(raw: String, max: Int = 3650): InputResult<BigDecimal> {
        val text = raw.trim()
        if (text.isEmpty()) return InputResult.Empty
        val parsed = try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            return InputResult.Invalid("Not a number")
        }
        return when {
            parsed.signum() < 0 -> InputResult.Invalid("Cannot be negative")
            parsed > BigDecimal(max) -> InputResult.Invalid("Cannot exceed $max days")
            else -> InputResult.Valid(parsed)
        }
    }
}
