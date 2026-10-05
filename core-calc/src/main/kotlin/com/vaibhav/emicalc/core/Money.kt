package com.vaibhav.emicalc.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money helpers.
 *
 * Every monetary value in this module is a [BigDecimal]. Using [Double] for money is a
 * correctness bug, not a style preference: binary floating point cannot represent most
 * decimal fractions, so interest accrual drifts and schedules fail to close at zero.
 */

/** Scale used for intermediate money values (paise). */
const val MONEY_SCALE: Int = 2

/** Scale used for rate arithmetic, where premature rounding would compound. */
const val RATE_SCALE: Int = 12

internal val HALF_UP: RoundingMode = RoundingMode.HALF_UP

/** Rounds to paise. Used for interest accrual and anything shown with decimals. */
fun BigDecimal.toPaise(): BigDecimal = setScale(MONEY_SCALE, HALF_UP)

/** Rounds to whole rupees. Lenders quote EMIs in whole rupees. */
fun BigDecimal.toRupees(): BigDecimal = setScale(0, HALF_UP)

/** Convenience for building money from literals in a readable way. */
fun money(value: String): BigDecimal = BigDecimal(value).toPaise()

fun money(value: Int): BigDecimal = BigDecimal(value).toPaise()

fun money(value: Long): BigDecimal = BigDecimal(value).toPaise()

internal val ZERO: BigDecimal = BigDecimal.ZERO.toPaise()

/** Never-negative clamp, for recoveries and taxable amounts that cannot go below zero. */
fun BigDecimal.coerceAtLeastZero(): BigDecimal = if (signum() < 0) ZERO else this

/** Divides with rate precision, avoiding non-terminating-expansion exceptions. */
internal fun BigDecimal.divideRate(divisor: BigDecimal): BigDecimal =
    divide(divisor, RATE_SCALE, HALF_UP)
