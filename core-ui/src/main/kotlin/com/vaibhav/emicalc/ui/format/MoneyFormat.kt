package com.vaibhav.emicalc.ui.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Indian-convention number formatting.
 *
 * Rupee amounts group the last three digits and then in pairs — 80,00,000 rather than
 * 8,000,000. `java.text.NumberFormat` with an Indian locale does this, but it drags in
 * locale data and behaves differently across JVM versions and Android API levels, so
 * the grouping is done here where it can be pinned by tests.
 */
object MoneyFormat {

    private const val RUPEE = "₹"

    /** `8000000` -> `"80,00,000"`. Negatives keep the sign outside the digits. */
    fun group(value: BigDecimal): String {
        val rounded = value.setScale(0, RoundingMode.HALF_UP)
        val negative = rounded.signum() < 0
        val digits = rounded.abs().toPlainString()
        return (if (negative) "-" else "") + groupDigits(digits)
    }

    /** `8000000` -> `"₹80,00,000"`. */
    fun rupees(value: BigDecimal): String {
        val grouped = group(value)
        return if (grouped.startsWith("-")) "-$RUPEE${grouped.substring(1)}" else "$RUPEE$grouped"
    }

    /** Keeps paise, for an amortisation row where the final instalment has them. */
    fun rupeesWithPaise(value: BigDecimal): String {
        val scaled = value.setScale(2, RoundingMode.HALF_UP)
        val negative = scaled.signum() < 0
        val plain = scaled.abs().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "00")
        val body = "$RUPEE${groupDigits(whole)}.$fraction"
        return if (negative) "-$body" else body
    }

    /**
     * Short form for headline figures: `"₹80.0 L"`, `"₹8.2 Cr"`.
     *
     * Thresholds follow how the amounts are actually spoken — anything under a lakh is
     * written out in full, because "₹0.7 L" is not how anyone says sixty-six thousand.
     */
    fun compact(value: BigDecimal): String {
        val negative = value.signum() < 0
        val abs = value.abs()
        val body = when {
            abs >= CRORE -> "$RUPEE${oneDecimal(abs.divide(CRORE, 2, RoundingMode.HALF_UP))} Cr"
            abs >= LAKH -> "$RUPEE${oneDecimal(abs.divide(LAKH, 2, RoundingMode.HALF_UP))} L"
            else -> rupees(abs)
        }
        return if (negative) "-$body" else body
    }

    private fun oneDecimal(value: BigDecimal): String =
        value.setScale(1, RoundingMode.HALF_UP).toPlainString()

    /**
     * Groups a run of digits: last three together, then pairs.
     * `"80000000"` -> `"8,00,00,000"`.
     */
    internal fun groupDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        // Chunk the remainder from the right, then put it back in reading order.
        val leading = digits.dropLast(3)
            .reversed()
            .chunked(2) { chunk -> chunk.reversed().toString() }
            .reversed()
        return "${leading.joinToString(",")},$last3"
    }

    private val LAKH = BigDecimal("100000")
    private val CRORE = BigDecimal("10000000")
}

/** Renders a month count the way people say it: `"1 yr 9 mo"`. */
object TenureFormat {

    fun short(months: Int): String {
        require(months >= 0) { "months cannot be negative, was $months" }
        if (months == 0) return "0 mo"
        val years = months / 12
        val rest = months % 12
        return when {
            years == 0 -> "$rest mo"
            rest == 0 -> "$years yr"
            else -> "$years yr $rest mo"
        }
    }

    fun long(months: Int): String {
        require(months >= 0) { "months cannot be negative, was $months" }
        if (months == 0) return "0 months"
        val years = months / 12
        val rest = months % 12
        val y = if (years == 1) "1 year" else "$years years"
        val m = if (rest == 1) "1 month" else "$rest months"
        return when {
            years == 0 -> m
            rest == 0 -> y
            else -> "$y $m"
        }
    }
}

/** Percentages, trimmed so `8.00` shows as `8%` but `8.45` keeps its digits. */
object RateFormat {
    fun percent(value: BigDecimal): String {
        val trimmed = value.stripTrailingZeros()
        val plain = if (trimmed.scale() < 0) trimmed.setScale(0).toPlainString() else trimmed.toPlainString()
        return "$plain%"
    }
}
