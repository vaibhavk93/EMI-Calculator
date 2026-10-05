package com.vaibhav.emicalc.core.fnf

import com.vaibhav.emicalc.core.coerceAtLeastZero
import com.vaibhav.emicalc.core.divideRate
import com.vaibhav.emicalc.core.toPaise
import com.vaibhav.emicalc.core.toRupees
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Everything needed to compute a settlement. */
data class ExitDetails(
    val joinedOn: LocalDate,
    val lastWorkingDay: LocalDate,
    val salary: SalaryStructure,
    /** Average of (basic + DA + commission) over the last 10 months, for Section 10(10AA). */
    val averageMonthlySalaryLast10Months: BigDecimal,
    val unpaidDaysInFinalMonth: Int = 0,
    val earnedLeaveBalanceDays: BigDecimal = BigDecimal.ZERO,
    val noticeDaysRequired: Int = 0,
    val noticeDaysServed: Int = 0,
    val pendingReimbursements: BigDecimal = BigDecimal.ZERO,
    val pendingBonus: BigDecimal = BigDecimal.ZERO,
    val outstandingEmployeeLoan: BigDecimal = BigDecimal.ZERO,
    /** Joining bonus, relocation or retention amounts being clawed back. */
    val clawbacks: BigDecimal = BigDecimal.ZERO,
    val unreturnedAssetValue: BigDecimal = BigDecimal.ZERO,
    /** Exemption under Section 10(10AA) already used in earlier years; the cap is lifetime. */
    val leaveExemptionAlreadyUsed: BigDecimal = BigDecimal.ZERO,
    val isFixedTermContract: Boolean = false,
    /** Other salary income for the year, so the slab estimate lands in the right band. */
    val otherTaxableIncomeThisYear: BigDecimal = BigDecimal.ZERO,
) {
    init {
        require(!lastWorkingDay.isBefore(joinedOn)) { "lastWorkingDay cannot precede joinedOn" }
        require(noticeDaysServed >= 0 && noticeDaysRequired >= 0) { "notice days cannot be negative" }
        require(earnedLeaveBalanceDays.signum() >= 0) { "leave balance cannot be negative" }
        require(unpaidDaysInFinalMonth >= 0) { "unpaid days cannot be negative" }
    }

    val serviceMonths: Int get() = ChronoUnit.MONTHS.between(joinedOn, lastWorkingDay).toInt()

    /**
     * Completed years for gratuity. Section 4(2) of the Payment of Gratuity Act counts a
     * part-year of more than six months as a full year.
     */
    val gratuityYears: Int
        get() {
            val whole = serviceMonths / 12
            return if (serviceMonths % 12 > 6) whole + 1 else whole
        }
}

/** One line on the settlement statement. */
data class SettlementLine(
    val label: String,
    val amount: BigDecimal,
    val basis: String,
)

/** A fully computed settlement, with its working exposed. */
data class Settlement(
    val earnings: List<SettlementLine>,
    val deductions: List<SettlementLine>,
    val gratuity: GratuityResult,
    val leaveEncashment: LeaveEncashmentResult,
    val tax: TaxComputation,
    val config: StatutoryConfig,
    val policy: CompanyPolicy,
) {
    val grossEarnings: BigDecimal get() = earnings.sumOf()
    val totalDeductionsBeforeTax: BigDecimal get() = deductions.sumOf()
    val netPayable: BigDecimal
        get() = (grossEarnings - totalDeductionsBeforeTax - tax.total).toRupees()

    private fun List<SettlementLine>.sumOf(): BigDecimal =
        fold(BigDecimal.ZERO) { acc, line -> acc + line.amount }.toPaise()
}

data class GratuityResult(
    val eligible: Boolean,
    val reason: String,
    val completedYears: Int,
    val wageBase: BigDecimal,
    val payable: BigDecimal,
    val exempt: BigDecimal,
    val taxable: BigDecimal,
)

data class LeaveEncashmentResult(
    val days: BigDecimal,
    val perDay: BigDecimal,
    val payable: BigDecimal,
    val exempt: BigDecimal,
    val taxable: BigDecimal,
    /** The four Section 10(10AA) limits, so the UI can show which one bit. */
    val exemptionLimits: Map<String, BigDecimal>,
)

/**
 * Full and final settlement for a departing employee.
 *
 * Statutory rules come from [StatutoryConfig]; everything the employer gets to choose
 * comes from [CompanyPolicy]. Keeping those apart is what lets this produce a correct
 * number for more than one company.
 */
object SettlementCalculator {

    fun gratuity(
        details: ExitDetails,
        config: StatutoryConfig,
        policy: CompanyPolicy,
    ): GratuityResult {
        val years = details.gratuityYears
        val wageBase = details.salary.statutoryWages

        // Fixed-term employees vest at one year under the Code on Social Security;
        // everyone else at the policy's vesting period.
        val vestingMonths = if (details.isFixedTermContract) 12 else policy.gratuityVestingMonths
        val eligible = details.serviceMonths >= vestingMonths
        val reason = when {
            eligible && details.isFixedTermContract ->
                "Fixed-term contract of at least 1 year (Code on Social Security, 2020)"
            eligible -> "Completed $vestingMonths months of continuous service"
            else -> "Service of ${details.serviceMonths} months is below the $vestingMonths-month vesting period"
        }

        if (!eligible) {
            return GratuityResult(false, reason, years, wageBase, ZERO, ZERO, ZERO)
        }

        val payable = wageBase
            .divideRate(BigDecimal(config.gratuityDivisor))
            .multiply(BigDecimal(config.gratuityDaysPerYear))
            .multiply(BigDecimal(years))
            .toRupees()

        // Section 10(10): least of actual, the statutory formula, and the cap.
        val exempt = payable.min(config.gratuityExemptionCap)
        return GratuityResult(
            eligible = true,
            reason = reason,
            completedYears = years,
            wageBase = wageBase,
            payable = payable,
            exempt = exempt,
            taxable = (payable - exempt).coerceAtLeastZero(),
        )
    }

    fun leaveEncashment(
        details: ExitDetails,
        config: StatutoryConfig,
        policy: CompanyPolicy,
    ): LeaveEncashmentResult {
        val days = details.earnedLeaveBalanceDays
        val divisor = policy.leaveEncashmentDivisor.divisorFor(details.lastWorkingDay)
        val perDay = details.salary.base(policy.leaveEncashmentBase).divideRate(divisor).toPaise()
        val payable = perDay.multiply(days).toRupees()

        val avg = details.averageMonthlySalaryLast10Months
        val cap = (config.leaveEncashmentExemptionCap - details.leaveExemptionAlreadyUsed)
            .coerceAtLeastZero()
        val tenMonths = avg.multiply(BigDecimal(config.leaveEncashmentAverageMonths)).toPaise()
        val cappedDays = days.min(
            BigDecimal(config.leaveEncashmentExemptionDaysPerYear * details.gratuityYears),
        )
        val byEntitlement = avg.divideRate(BigDecimal(30)).multiply(cappedDays).toPaise()

        val limits = linkedMapOf(
            "Actual received" to payable,
            "Statutory cap (net of prior claims)" to cap,
            "${config.leaveEncashmentAverageMonths} months average salary" to tenMonths,
            "${config.leaveEncashmentExemptionDaysPerYear} days per completed year" to byEntitlement,
        )
        val exempt = limits.values.reduce { a, b -> a.min(b) }.coerceAtLeastZero()

        return LeaveEncashmentResult(
            days = days,
            perDay = perDay,
            payable = payable,
            exempt = exempt,
            taxable = (payable - exempt).coerceAtLeastZero(),
            exemptionLimits = limits,
        )
    }

    fun noticeShortfallRecovery(details: ExitDetails, policy: CompanyPolicy): BigDecimal {
        val shortfall = (details.noticeDaysRequired - details.noticeDaysServed).coerceAtLeast(0)
        if (shortfall == 0) return ZERO
        val divisor = policy.noticeRecoveryDivisor.divisorFor(details.lastWorkingDay)
        return details.salary.base(policy.noticeRecoveryBase)
            .divideRate(divisor)
            .multiply(BigDecimal(shortfall))
            .toRupees()
    }

    fun compute(
        details: ExitDetails,
        config: StatutoryConfig = StatutoryConfig.DEFAULT,
        policy: CompanyPolicy = CompanyPolicy(),
    ): Settlement {
        val gratuity = gratuity(details, config, policy)
        val leave = leaveEncashment(details, config, policy)

        val unpaidDivisor = policy.perDayDivisor.divisorFor(details.lastWorkingDay)
        val unpaidSalary = details.salary.base(policy.unpaidSalaryBase)
            .divideRate(unpaidDivisor)
            .multiply(BigDecimal(details.unpaidDaysInFinalMonth))
            .toRupees()

        val earnings = buildList {
            if (unpaidSalary.signum() > 0) {
                add(SettlementLine("Unpaid salary", unpaidSalary, "${details.unpaidDaysInFinalMonth} days"))
            }
            if (leave.payable.signum() > 0) {
                add(SettlementLine("Leave encashment", leave.payable, "${leave.days} days at ${leave.perDay}/day"))
            }
            if (gratuity.payable.signum() > 0) {
                add(
                    SettlementLine(
                        "Gratuity",
                        gratuity.payable,
                        "${config.gratuityDaysPerYear}/${config.gratuityDivisor} x " +
                            "${gratuity.completedYears} yrs on wages ${gratuity.wageBase}",
                    ),
                )
            }
            if (details.pendingBonus.signum() > 0) {
                add(SettlementLine("Pending bonus / incentive", details.pendingBonus.toRupees(), "As declared"))
            }
            if (details.pendingReimbursements.signum() > 0) {
                add(SettlementLine("Reimbursements", details.pendingReimbursements.toRupees(), "As claimed"))
            }
        }

        val notice = noticeShortfallRecovery(details, policy)
        val deductions = buildList {
            if (notice.signum() > 0) {
                val short = details.noticeDaysRequired - details.noticeDaysServed
                add(SettlementLine("Notice period shortfall", notice, "$short days short"))
            }
            if (details.outstandingEmployeeLoan.signum() > 0) {
                add(SettlementLine("Employee loan / advance", details.outstandingEmployeeLoan.toRupees(), "Outstanding"))
            }
            if (details.clawbacks.signum() > 0) {
                add(SettlementLine("Clawbacks", details.clawbacks.toRupees(), "Joining / relocation / retention"))
            }
            if (details.unreturnedAssetValue.signum() > 0) {
                add(SettlementLine("Unreturned assets", details.unreturnedAssetValue.toRupees(), "At book value"))
            }
        }

        // Only the taxable slices of gratuity and leave encashment enter income, and the
        // notice recovery reduces taxable salary because that pay is never received.
        val taxableFromSettlement = (
            unpaidSalary + details.pendingBonus + gratuity.taxable + leave.taxable - notice
            ).coerceAtLeastZero()
        val tax = IncomeTax.compute(
            (taxableFromSettlement + details.otherTaxableIncomeThisYear).toPaise(),
            config,
        )

        return Settlement(earnings, deductions, gratuity, leave, tax, config, policy)
    }

    private val ZERO = BigDecimal.ZERO.toPaise()
}
