package com.vaibhav.emicalc.core

import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal

/**
 * Asserts two money values are equal **by value**.
 *
 * [BigDecimal.equals] is scale-sensitive, so `60000` and `60000.00` are not equal to it
 * even though they are the same amount. Tests care about the amount, not the scale, so
 * they compare with [BigDecimal.compareTo] via this helper.
 */
fun assertMoney(expected: String, actual: BigDecimal, message: String? = null) {
    val want = BigDecimal(expected)
    if (want.compareTo(actual) != 0) {
        assertEquals(want.toPlainString(), actual.toPlainString(), message)
    }
}

fun assertMoney(expected: BigDecimal, actual: BigDecimal, message: String? = null) =
    assertMoney(expected.toPlainString(), actual, message)
