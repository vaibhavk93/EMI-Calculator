package com.vaibhav.emicalc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.vaibhav.emicalc.core.fnf.CompanyPolicy
import com.vaibhav.emicalc.core.fnf.ExitDetails
import com.vaibhav.emicalc.core.fnf.PerDayDivisor
import com.vaibhav.emicalc.core.fnf.SalaryBase
import com.vaibhav.emicalc.core.fnf.SalaryStructure
import com.vaibhav.emicalc.core.fnf.SettlementCalculator
import com.vaibhav.emicalc.core.fnf.StatutoryConfig
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.components.AmountRow
import com.vaibhav.emicalc.ui.components.AssumptionPicker
import com.vaibhav.emicalc.ui.components.HeadlineFigure
import com.vaibhav.emicalc.ui.components.ParsedField
import com.vaibhav.emicalc.ui.components.RulesAsOfBadge
import com.vaibhav.emicalc.ui.components.SectionHeader
import com.vaibhav.emicalc.ui.components.WorkingCard
import com.vaibhav.emicalc.ui.format.InputResult
import com.vaibhav.emicalc.ui.format.MoneyFormat
import com.vaibhav.emicalc.ui.format.MoneyInput
import com.vaibhav.emicalc.ui.viewmodel.rememberFnfViewModel
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Works out a full and final settlement.
 *
 * The form is long because the inputs genuinely are, and because the assumptions are
 * shown rather than hidden. Employers differ on almost everything that is not fixed by
 * statute — the per-day divisor, whether encashment runs on basic or gross, what notice
 * recovery is calculated on — so those are pickers with defaults, not constants. A
 * calculator that bakes in one company's policy is wrong for everybody else.
 */
@Composable
fun FnfScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val config = remember { StatutoryConfig.DEFAULT }
    val viewModel = rememberFnfViewModel(container)
    val restoredDraft by viewModel.draft.collectAsState()
    val saved by viewModel.saved.collectAsState()

    var basic by remember { mutableStateOf("") }
    var da by remember { mutableStateOf("") }
    var allowances by remember { mutableStateOf("") }
    var serviceYears by remember { mutableStateOf("") }
    var unpaidDays by remember { mutableStateOf("") }
    var leaveDays by remember { mutableStateOf("") }
    var noticeRequired by remember { mutableStateOf("") }
    var noticeServed by remember { mutableStateOf("") }
    var reimbursements by remember { mutableStateOf("") }
    var loanOutstanding by remember { mutableStateOf("") }
    var clawbacks by remember { mutableStateOf("") }
    var fixedTerm by remember { mutableStateOf(false) }

    var divisor by remember { mutableStateOf(PerDayDivisor.DAYS_30) }
    var encashmentBase by remember { mutableStateOf(SalaryBase.BASIC_PLUS_DA) }
    var noticeBase by remember { mutableStateOf(SalaryBase.GROSS) }

    // Fields are persisted as a unit-separator-joined list; the order below is the
    // schema, so new fields go on the end rather than in the middle.
    val fields = listOf(
        basic, da, allowances, serviceYears, unpaidDays, leaveDays,
        noticeRequired, noticeServed, reimbursements, loanOutstanding, clawbacks,
    )
    var draftApplied by remember { mutableStateOf(false) }

    LaunchedEffect(restoredDraft) {
        val raw = restoredDraft
        if (!draftApplied && raw != null) {
            draftApplied = true
            val parts = raw.split('\u001F')
            if (parts.size >= 11) {
                basic = parts[0]; da = parts[1]; allowances = parts[2]
                serviceYears = parts[3]; unpaidDays = parts[4]; leaveDays = parts[5]
                noticeRequired = parts[6]; noticeServed = parts[7]
                reimbursements = parts[8]; loanOutstanding = parts[9]; clawbacks = parts[10]
            }
        }
    }

    LaunchedEffect(fields) {
        if (fields.any { it.isNotBlank() }) {
            viewModel.saveDraft(fields.joinToString("\u001F"))
        }
    }

    fun amount(raw: String): BigDecimal =
        (MoneyInput.parseAmount(raw) as? InputResult.Valid)?.value ?: BigDecimal.ZERO

    fun days(raw: String): BigDecimal =
        (MoneyInput.parseDays(raw) as? InputResult.Valid)?.value ?: BigDecimal.ZERO

    fun int(raw: String): Int = raw.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0

    val basicAmount = amount(basic)
    val policy = CompanyPolicy(
        perDayDivisor = divisor,
        leaveEncashmentBase = encashmentBase,
        leaveEncashmentDivisor = divisor,
        noticeRecoveryBase = noticeBase,
        noticeRecoveryDivisor = divisor,
    )

    val settlement = remember(
        basic, da, allowances, serviceYears, unpaidDays, leaveDays,
        noticeRequired, noticeServed, reimbursements, loanOutstanding, clawbacks,
        fixedTerm, divisor, encashmentBase, noticeBase,
    ) {
        if (basicAmount.signum() <= 0) {
            null
        } else {
            runCatching {
                val salary = SalaryStructure(
                    basic = basicAmount,
                    dearnessAllowance = amount(da),
                    otherAllowances = amount(allowances),
                )
                val years = int(serviceYears).coerceAtLeast(0)
                val last = LocalDate.now()
                SettlementCalculator.compute(
                    ExitDetails(
                        joinedOn = last.minusMonths(years * 12L),
                        lastWorkingDay = last,
                        salary = salary,
                        averageMonthlySalaryLast10Months = salary.base(encashmentBase),
                        unpaidDaysInFinalMonth = int(unpaidDays),
                        earnedLeaveBalanceDays = days(leaveDays),
                        noticeDaysRequired = int(noticeRequired),
                        noticeDaysServed = int(noticeServed),
                        pendingReimbursements = amount(reimbursements),
                        outstandingEmployeeLoan = amount(loanOutstanding),
                        clawbacks = amount(clawbacks),
                        isFixedTermContract = fixedTerm,
                    ),
                    config,
                    policy,
                )
            }.getOrNull()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Exit settlement", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        RulesAsOfBadge(config.label)

        if (settlement != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    HeadlineFigure(
                        caption = if (settlement.netPayable.signum() < 0) "You would owe" else "Net payable",
                        value = MoneyFormat.rupees(settlement.netPayable.abs()),
                        detail = if (settlement.netPayable.signum() < 0) {
                            "Deductions exceed what you are owed"
                        } else {
                            null
                        },
                    )
                }
            }
        }

        SectionHeader("Your monthly pay")
        ParsedField("Basic", basic, { basic = it }, MoneyInput::parsePositiveAmount)
        ParsedField("Dearness allowance", da, { da = it }, MoneyInput::parseAmount)
        ParsedField(
            "Other allowances",
            allowances,
            { allowances = it },
            MoneyInput::parseAmount,
            supporting = "HRA, conveyance and the rest",
        )

        settlement?.let { computed ->
            val salary = SalaryStructure(
                basic = basicAmount,
                dearnessAllowance = amount(da),
                otherAllowances = amount(allowances),
            )
            if (salary.wageFloorApplied) {
                Spacer(Modifier.height(8.dp))
                WorkingCard(
                    title = "Your wage base was lifted",
                    formula = "wages = max(basic + DA, 50% of total pay)",
                    lines = listOf(
                        "Basic + DA" to MoneyFormat.rupees(salary.namedWages),
                        "Total pay" to MoneyFormat.rupees(salary.totalRemuneration),
                        "Wages used" to MoneyFormat.rupees(salary.statutoryWages),
                    ),
                    source = "Under the Code on Wages, in force since 21 November 2025, basic " +
                        "plus DA must be at least half of total pay. Your structure is " +
                        "allowance-heavy, so the excess is added back — which raises your " +
                        "gratuity. Many older calculators still miss this.",
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Gratuity is computed on ${MoneyFormat.rupees(computed.gratuity.wageBase)} per month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("Service and exit")
        OutlinedTextField(
            value = serviceYears,
            onValueChange = { serviceYears = it },
            label = { Text("Complete years of service") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fixed-term contract", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Fixed-term staff qualify for gratuity after one year, not five",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = fixedTerm, onCheckedChange = { fixedTerm = it })
        }
        ParsedField(
            "Unpaid days in your final month",
            unpaidDays,
            { unpaidDays = it },
            { MoneyInput.parseDays(it, max = 31) },
            keyboardType = KeyboardType.Number,
        )
        ParsedField(
            "Earned leave balance (days)",
            leaveDays,
            { leaveDays = it },
            { MoneyInput.parseDays(it, max = 999) },
        )
        ParsedField(
            "Notice period required (days)",
            noticeRequired,
            { noticeRequired = it },
            { MoneyInput.parseDays(it, max = 365) },
            keyboardType = KeyboardType.Number,
        )
        ParsedField(
            "Notice days you will serve",
            noticeServed,
            { noticeServed = it },
            { MoneyInput.parseDays(it, max = 365) },
            keyboardType = KeyboardType.Number,
        )

        SectionHeader("Other amounts")
        ParsedField("Pending reimbursements", reimbursements, { reimbursements = it }, MoneyInput::parseAmount)
        ParsedField("Outstanding company loan", loanOutstanding, { loanOutstanding = it }, MoneyInput::parseAmount)
        ParsedField(
            "Clawbacks",
            clawbacks,
            { clawbacks = it },
            MoneyInput::parseAmount,
            supporting = "Joining bonus, relocation or retention being recovered",
        )

        SectionHeader("Your company's policy")
        Text(
            "These are not fixed by law and differ between employers. Check your offer " +
                "letter or HR policy, because they change the figures.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        AssumptionPicker(
            label = "Per-day divisor",
            options = PerDayDivisor.entries.toList(),
            selected = divisor,
            onSelect = { divisor = it },
            render = {
                when (it) {
                    PerDayDivisor.DAYS_26 -> "26 working days"
                    PerDayDivisor.DAYS_30 -> "30-day month"
                    PerDayDivisor.ACTUAL_DAYS -> "Actual days in the month"
                }
            },
            explanation = "A 26-day divisor gives a higher daily rate than a 30-day one.",
        )
        Spacer(Modifier.height(8.dp))
        AssumptionPicker(
            label = "Leave encashment on",
            options = listOf(SalaryBase.BASIC, SalaryBase.BASIC_PLUS_DA, SalaryBase.GROSS),
            selected = encashmentBase,
            onSelect = { encashmentBase = it },
            render = ::renderBase,
        )
        Spacer(Modifier.height(8.dp))
        AssumptionPicker(
            label = "Notice recovery on",
            options = listOf(SalaryBase.BASIC, SalaryBase.BASIC_PLUS_DA, SalaryBase.GROSS),
            selected = noticeBase,
            onSelect = { noticeBase = it },
            render = ::renderBase,
        )

        if (settlement != null) {
            SectionHeader("Statement")
            settlement.earnings.forEach { line ->
                AmountRow(line.label, line.amount, basis = line.basis)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            AmountRow("Gross earnings", settlement.grossEarnings, emphasis = true)

            if (settlement.deductions.isNotEmpty()) {
                SectionHeader("Deductions")
                settlement.deductions.forEach { line ->
                    AmountRow(line.label, line.amount.negate(), basis = line.basis)
                }
            }

            SectionHeader("Tax")
            AmountRow("Estimated tax", settlement.tax.total.negate())
            Spacer(Modifier.height(8.dp))
            WorkingCard(
                title = "How the tax was estimated",
                formula = "slab tax - 87A rebate - marginal relief + surcharge + 4% cess",
                lines = listOf(
                    "Taxable income" to MoneyFormat.rupees(settlement.tax.taxableIncome),
                    "Slab tax" to MoneyFormat.rupees(settlement.tax.slabTax),
                    "87A rebate" to MoneyFormat.rupees(settlement.tax.rebate87A),
                    "Marginal relief" to MoneyFormat.rupees(settlement.tax.marginalRelief),
                    "Surcharge" to MoneyFormat.rupees(settlement.tax.surcharge),
                    "Cess" to MoneyFormat.rupees(settlement.tax.cess),
                ),
                source = "An estimate only. Your employer deducts TDS against your projected " +
                    "income for the whole year, which this cannot see. Note that marginal " +
                    "relief is given before cess, so the total just above 12 lakh rises " +
                    "slightly faster than the extra income.",
            )

            Spacer(Modifier.height(8.dp))
            WorkingCard(
                title = "Gratuity",
                formula = "wages / 26 x 15 x completed years",
                lines = listOf(
                    "Eligible" to if (settlement.gratuity.eligible) "Yes" else "No",
                    "Reason" to settlement.gratuity.reason,
                    "Completed years" to settlement.gratuity.completedYears.toString(),
                    "Payable" to MoneyFormat.rupees(settlement.gratuity.payable),
                    "Tax free" to MoneyFormat.rupees(settlement.gratuity.exempt),
                    "Taxable" to MoneyFormat.rupees(settlement.gratuity.taxable),
                ),
                source = "Code on Social Security, 2020 s.53. Exemption capped at 20 lakh " +
                    "under s.10(10) of the Income-tax Act.",
            )

            if (settlement.leaveEncashment.payable.signum() > 0) {
                Spacer(Modifier.height(8.dp))
                WorkingCard(
                    title = "Leave encashment",
                    formula = "exempt = least of four limits",
                    lines = settlement.leaveEncashment.exemptionLimits.map { (label, value) ->
                        label to MoneyFormat.rupees(value)
                    } + listOf(
                        "Exempt" to MoneyFormat.rupees(settlement.leaveEncashment.exempt),
                        "Taxable" to MoneyFormat.rupees(settlement.leaveEncashment.taxable),
                    ),
                    source = "s.10(10AA)(ii). The 25 lakh ceiling is a lifetime limit across " +
                        "every employer, not a per-job one.",
                )
            }

            Spacer(Modifier.height(16.dp))
            AmountRow("Net payable", settlement.netPayable, emphasis = true)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.save(
                        label = "Settlement, ${settlement.gratuity.completedYears} yrs service",
                        netPayable = MoneyFormat.rupees(settlement.netPayable),
                        payload = fields.joinToString("\u001F"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saved) "Saved to history" else "Save this settlement")
            }
            if (saved) {
                LaunchedEffect(Unit) { viewModel.acknowledgeSaved() }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

private fun renderBase(base: SalaryBase): String = when (base) {
    SalaryBase.BASIC -> "Basic only"
    SalaryBase.BASIC_PLUS_DA -> "Basic + DA"
    SalaryBase.STATUTORY_WAGES -> "Statutory wages"
    SalaryBase.GROSS -> "Gross pay"
}
