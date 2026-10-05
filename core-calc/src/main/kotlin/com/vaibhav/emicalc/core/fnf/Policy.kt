package com.vaibhav.emicalc.core.fnf

import com.vaibhav.emicalc.core.divideRate
import com.vaibhav.emicalc.core.toPaise
import java.math.BigDecimal
import java.time.LocalDate

/**
 * How a per-day rate is derived from a monthly figure.
 *
 * This is the single largest source of disagreement in a settlement, and it is company
 * policy rather than statute. Hardcoding any one of these makes the output wrong for
 * everybody else, so it is an input.
 */
enum class PerDayDivisor {
    /** 26 working days. The divisor the Payment of Gratuity Act uses. */
    DAYS_26,

    /** A flat 30-day month. Common for leave encashment and notice pay. */
    DAYS_30,

    /** The actual number of calendar days in the month in question. */
    ACTUAL_DAYS,
    ;

    fun divisorFor(month: LocalDate): BigDecimal = when (this) {
        DAYS_26 -> BigDecimal(26)
        DAYS_30 -> BigDecimal(30)
        ACTUAL_DAYS -> BigDecimal(month.lengthOfMonth())
    }
}

/** Which slice of pay a given component is computed on. Again: policy, not statute. */
enum class SalaryBase {
    BASIC,
    BASIC_PLUS_DA,
    STATUTORY_WAGES,
    GROSS,
}

/**
 * An employee's monthly pay, split so the statutory wage floor can be applied.
 *
 * [otherAllowances] holds the components excluded from "wages" by Section 2(y) of the
 * Code on Wages — HRA, conveyance, and similar.
 */
data class SalaryStructure(
    val basic: BigDecimal,
    val dearnessAllowance: BigDecimal = BigDecimal.ZERO,
    val retainingAllowance: BigDecimal = BigDecimal.ZERO,
    val otherAllowances: BigDecimal = BigDecimal.ZERO,
    val commission: BigDecimal = BigDecimal.ZERO,
) {
    init {
        require(basic.signum() > 0) { "basic must be positive, was $basic" }
        require(
            listOf(dearnessAllowance, retainingAllowance, otherAllowances, commission)
                .all { it.signum() >= 0 },
        ) { "salary components cannot be negative" }
    }

    /** Everything the employer pays monthly. */
    val totalRemuneration: BigDecimal
        get() = (basic + dearnessAllowance + retainingAllowance + otherAllowances).toPaise()

    /** The included components, before the floor is applied. */
    val namedWages: BigDecimal
        get() = (basic + dearnessAllowance + retainingAllowance).toPaise()

    /**
     * Wages under Section 2(y) of the Code on Wages, in force since 21 November 2025.
     *
     * Where the excluded components exceed half of total remuneration, the excess is
     * added back into wages. That reduces algebraically to a floor at half of total
     * remuneration, which is how this is implemented.
     *
     * Per the Ministry of Labour FAQ dated 16 March 2026, gratuity is computed on this
     * revised base with effect from 21 November 2025.
     */
    val statutoryWages: BigDecimal
        get() = namedWages.max(totalRemuneration.divideRate(BigDecimal(2)).toPaise())

    /** True when the floor actually bit, i.e. the structure was allowance-heavy. */
    val wageFloorApplied: Boolean get() = statutoryWages > namedWages

    fun base(which: SalaryBase): BigDecimal = when (which) {
        SalaryBase.BASIC -> basic.toPaise()
        SalaryBase.BASIC_PLUS_DA -> (basic + dearnessAllowance).toPaise()
        SalaryBase.STATUTORY_WAGES -> statutoryWages
        SalaryBase.GROSS -> totalRemuneration
    }
}

/**
 * The employer-specific assumptions behind a settlement. Every field here varies between
 * companies, so all of it is surfaced to the user with a default rather than buried.
 */
data class CompanyPolicy(
    val perDayDivisor: PerDayDivisor = PerDayDivisor.DAYS_30,
    val leaveEncashmentBase: SalaryBase = SalaryBase.BASIC_PLUS_DA,
    val leaveEncashmentDivisor: PerDayDivisor = PerDayDivisor.DAYS_30,
    val noticeRecoveryBase: SalaryBase = SalaryBase.GROSS,
    val noticeRecoveryDivisor: PerDayDivisor = PerDayDivisor.DAYS_30,
    val unpaidSalaryBase: SalaryBase = SalaryBase.GROSS,
    /**
     * Months of continuous service before gratuity vests. 60 (five years) is the
     * settled reading of Section 4 of the Payment of Gratuity Act. Some employers and
     * some High Court decisions accept 56 months ("four years and 240 days"); that is
     * contested, so it is an opt-in rather than the default.
     */
    val gratuityVestingMonths: Int = 60,
)

/** Versioned statutory constants. Shipped as remote config so a Budget does not need an app release. */
data class StatutoryConfig(
    val rulesAsOf: LocalDate,
    val label: String,
    val gratuityDaysPerYear: Int = 15,
    val gratuityDivisor: Int = 26,
    val gratuityExemptionCap: BigDecimal = BigDecimal("2000000"),
    val leaveEncashmentExemptionCap: BigDecimal = BigDecimal("2500000"),
    val leaveEncashmentExemptionDaysPerYear: Int = 30,
    val leaveEncashmentAverageMonths: Int = 10,
    val standardDeduction: BigDecimal = BigDecimal("75000"),
    val rebate87AIncomeLimit: BigDecimal = BigDecimal("1200000"),
    val rebate87AMaxAmount: BigDecimal = BigDecimal("60000"),
    val cessPercent: BigDecimal = BigDecimal("4"),
    val slabs: List<TaxSlab> = NEW_REGIME_FY_2026_27,
    val surcharges: List<SurchargeBand> = NEW_REGIME_SURCHARGES,
) {
    companion object {
        /** New regime, financial year 2026-27. */
        val NEW_REGIME_FY_2026_27: List<TaxSlab> = listOf(
            TaxSlab(BigDecimal("400000"), BigDecimal("0")),
            TaxSlab(BigDecimal("800000"), BigDecimal("5")),
            TaxSlab(BigDecimal("1200000"), BigDecimal("10")),
            TaxSlab(BigDecimal("1600000"), BigDecimal("15")),
            TaxSlab(BigDecimal("2000000"), BigDecimal("20")),
            TaxSlab(BigDecimal("2400000"), BigDecimal("25")),
            TaxSlab(null, BigDecimal("30")),
        )

        val NEW_REGIME_SURCHARGES: List<SurchargeBand> = listOf(
            SurchargeBand(BigDecimal("5000000"), BigDecimal("10")),
            SurchargeBand(BigDecimal("10000000"), BigDecimal("15")),
            SurchargeBand(BigDecimal("20000000"), BigDecimal("25")),
        )

        /** The default shipped with the app; overridden by remote config at runtime. */
        val DEFAULT: StatutoryConfig = StatutoryConfig(
            rulesAsOf = LocalDate.of(2026, 10, 5),
            label = "FY 2026-27 new regime; Labour Codes in force 21 Nov 2025",
        )
    }
}

/** A slab boundary. [upTo] is null for the open-ended top slab. */
data class TaxSlab(val upTo: BigDecimal?, val ratePercent: BigDecimal)

/** Surcharge applying to income above [above]. */
data class SurchargeBand(val above: BigDecimal, val ratePercent: BigDecimal)
