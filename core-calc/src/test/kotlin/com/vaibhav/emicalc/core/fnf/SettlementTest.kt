package com.vaibhav.emicalc.core.fnf

import com.vaibhav.emicalc.core.assertMoney
import com.vaibhav.emicalc.core.money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SettlementTest {

    private val config = StatutoryConfig.DEFAULT
    private val policy = CompanyPolicy()

    // ------------------------------------------- Code on Wages s.2(y) wage floor

    @Test
    @DisplayName("an allowance-heavy structure is lifted to half of total remuneration")
    fun wageFloorBites() {
        val salary = SalaryStructure(basic = money(30_000), otherAllowances = money(70_000))
        assertMoney("100000", salary.totalRemuneration)
        assertMoney("30000", salary.namedWages)
        assertMoney("50000", salary.statutoryWages, "floor is half of total remuneration")
        assertTrue(salary.wageFloorApplied)
    }

    @Test
    @DisplayName("a basic-heavy structure is left alone by the floor")
    fun wageFloorDormant() {
        val salary = SalaryStructure(basic = money(60_000), otherAllowances = money(40_000))
        assertMoney("60000", salary.statutoryWages)
        assertFalse(salary.wageFloorApplied)
    }

    // ------------------------------------------------------------------ gratuity

    @Test
    @DisplayName("15 years on wages of 60,000 gives gratuity of 5,19,231")
    fun gratuityGolden() {
        val details = exit(years = 15, salary = SalaryStructure(money(60_000), otherAllowances = money(40_000)))
        val result = SettlementCalculator.gratuity(details, config, policy)
        assertTrue(result.eligible)
        assertEquals(15, result.completedYears)
        assertMoney("519231", result.payable)
        assertMoney("519231", result.exempt, "well under the 20L cap")
        assertMoney("0", result.taxable)
    }

    @Test
    @DisplayName("gratuity exemption is capped at 20 lakh and the excess is taxable")
    fun gratuityExemptionCap() {
        val details = exit(years = 25, salary = SalaryStructure(money(240_000)))
        val result = SettlementCalculator.gratuity(details, config, policy)
        assertMoney("3461538", result.payable)
        assertMoney("2000000", result.exempt)
        assertMoney("1461538", result.taxable)
    }

    @Test
    @DisplayName("gratuity does not vest below five years")
    fun gratuityNotVested() {
        val result = SettlementCalculator.gratuity(exit(years = 4), config, policy)
        assertFalse(result.eligible)
        assertMoney("0", result.payable)
    }

    @Test
    @DisplayName("a part-year above six months counts as a whole year (PGA s.4(2))")
    fun sixMonthRule() {
        val salary = SalaryStructure(money(60_000))
        val joined = LocalDate.of(2018, 1, 1)
        // 7 years 7 months rounds up to 8; 7 years 5 months stays at 7.
        val up = ExitDetails(joined, LocalDate.of(2025, 8, 1), salary, money(60_000))
        val down = ExitDetails(joined, LocalDate.of(2025, 6, 1), salary, money(60_000))
        assertEquals(8, up.gratuityYears)
        assertEquals(7, down.gratuityYears)
    }

    @Test
    @DisplayName("a fixed-term employee vests at one year")
    fun fixedTermVestsEarly() {
        val details = exit(years = 1).copy(isFixedTermContract = true)
        assertTrue(SettlementCalculator.gratuity(details, config, policy).eligible)
        assertFalse(SettlementCalculator.gratuity(details.copy(isFixedTermContract = false), config, policy).eligible)
    }

    // --------------------------------------------------- leave encashment s.10(10AA)

    @Test
    @DisplayName("the 30-days-per-year limit binds and is reported as the binding limit")
    fun leaveEncashmentLeastOfFour() {
        val details = exit(years = 20, salary = SalaryStructure(money(50_000), money(5_000)))
            .copy(earnedLeaveBalanceDays = BigDecimal("120"), averageMonthlySalaryLast10Months = money(55_000))
        val result = SettlementCalculator.leaveEncashment(details, config, policy)

        assertMoney("220000", result.payable, "120 days at 55,000/30 per day")
        assertMoney("550000", result.exemptionLimits.getValue("10 months average salary"))
        assertMoney("220000", result.exempt)
        assertMoney("0", result.taxable)
    }

    @Test
    @DisplayName("a long leave balance on short service is capped at 30 days per year")
    fun leaveEncashmentDaysCap() {
        val details = exit(years = 5, salary = SalaryStructure(money(100_000)))
            .copy(earnedLeaveBalanceDays = BigDecimal("300"), averageMonthlySalaryLast10Months = money(100_000))
        val result = SettlementCalculator.leaveEncashment(details, config, policy)
        // Entitlement limit: 30 x 5 = 150 days, not the 300 actually held.
        assertMoney("500000", result.exemptionLimits.getValue("30 days per completed year"))
        assertMoney("500000", result.exempt)
        assertTrue(result.taxable.signum() > 0, "the uncapped half is taxable")
    }

    @Test
    @DisplayName("exemption already claimed in earlier years reduces the lifetime cap")
    fun lifetimeCap() {
        val details = exit(years = 30, salary = SalaryStructure(money(500_000)))
            .copy(
                earnedLeaveBalanceDays = BigDecimal("300"),
                averageMonthlySalaryLast10Months = money(500_000),
                leaveExemptionAlreadyUsed = money(2_400_000),
            )
        val result = SettlementCalculator.leaveEncashment(details, config, policy)
        assertMoney("100000", result.exemptionLimits.getValue("Statutory cap (net of prior claims)"))
        assertMoney("100000", result.exempt)
    }

    // ----------------------------------------------------------- notice recovery

    @Test
    @DisplayName("60 days of notice shortfall on 1,00,000 gross recovers 2,00,000")
    fun noticeRecovery() {
        val details = exit(years = 6, salary = SalaryStructure(money(100_000)))
            .copy(noticeDaysRequired = 90, noticeDaysServed = 30)
        assertMoney("200000", SettlementCalculator.noticeShortfallRecovery(details, policy))
    }

    @Test
    @DisplayName("serving the full notice recovers nothing, and over-serving does not go negative")
    fun noticeNoRecovery() {
        val served = exit(years = 6).copy(noticeDaysRequired = 90, noticeDaysServed = 90)
        val over = exit(years = 6).copy(noticeDaysRequired = 30, noticeDaysServed = 90)
        assertMoney("0", SettlementCalculator.noticeShortfallRecovery(served, policy))
        assertMoney("0", SettlementCalculator.noticeShortfallRecovery(over, policy))
    }

    @Test
    @DisplayName("the per-day divisor changes the answer, which is why it is an input")
    fun divisorIsPolicy() {
        val details = exit(years = 6, salary = SalaryStructure(money(100_000)))
            .copy(noticeDaysRequired = 60, noticeDaysServed = 0)
        val on30 = SettlementCalculator.noticeShortfallRecovery(details, policy)
        val on26 = SettlementCalculator.noticeShortfallRecovery(
            details,
            policy.copy(noticeRecoveryDivisor = PerDayDivisor.DAYS_26),
        )
        assertMoney("200000", on30)
        assertMoney("230769", on26)
        assertTrue(on26 > on30, "a 26-day divisor recovers more per day")
    }

    // ------------------------------------------------------------- whole settlement

    @Test
    @DisplayName("a full settlement reconciles gross, deductions, tax and net")
    fun fullSettlement() {
        val details = exit(years = 8, salary = SalaryStructure(money(80_000), otherAllowances = money(70_000)))
            .copy(
                unpaidDaysInFinalMonth = 10,
                earnedLeaveBalanceDays = BigDecimal("24"),
                averageMonthlySalaryLast10Months = money(80_000),
                noticeDaysRequired = 60,
                noticeDaysServed = 45,
                pendingReimbursements = money(12_000),
                outstandingEmployeeLoan = money(40_000),
            )
        val settlement = SettlementCalculator.compute(details, config, policy)

        assertTrue(settlement.earnings.any { it.label == "Gratuity" })
        assertTrue(settlement.earnings.any { it.label == "Leave encashment" })
        assertTrue(settlement.deductions.any { it.label == "Notice period shortfall" })
        assertTrue(settlement.deductions.any { it.label == "Employee loan / advance" })

        val expectedNet = settlement.grossEarnings -
            settlement.totalDeductionsBeforeTax -
            settlement.tax.total
        assertMoney(expectedNet.setScale(0, java.math.RoundingMode.HALF_UP), settlement.netPayable)
        assertTrue(settlement.netPayable.signum() > 0)
    }

    @Test
    @DisplayName("deductions can exceed earnings, and the net then goes negative")
    fun employeeOwesMoney() {
        val details = exit(years = 2, salary = SalaryStructure(money(100_000)))
            .copy(noticeDaysRequired = 90, noticeDaysServed = 0, clawbacks = money(300_000))
        val settlement = SettlementCalculator.compute(details, config, policy)
        assertTrue(settlement.netPayable.signum() < 0, "a recoverable settlement must not clamp to zero")
    }

    private fun exit(
        years: Int,
        salary: SalaryStructure = SalaryStructure(money(60_000)),
    ): ExitDetails {
        val last = LocalDate.of(2026, 10, 1)
        return ExitDetails(
            joinedOn = last.minusYears(years.toLong()),
            lastWorkingDay = last,
            salary = salary,
            averageMonthlySalaryLast10Months = salary.namedWages,
        )
    }
}
