package com.vaibhav.emicalc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vaibhav.emicalc.core.loan.AmortisationResult
import com.vaibhav.emicalc.core.loan.EmiCalculator
import com.vaibhav.emicalc.core.loan.Loan
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.components.AmountRow
import com.vaibhav.emicalc.ui.components.HeadlineFigure
import com.vaibhav.emicalc.ui.components.MoneyText
import com.vaibhav.emicalc.ui.components.ParsedField
import com.vaibhav.emicalc.ui.components.SectionHeader
import com.vaibhav.emicalc.ui.components.WorkingCard
import com.vaibhav.emicalc.ui.format.InputResult
import com.vaibhav.emicalc.ui.format.MoneyFormat
import com.vaibhav.emicalc.ui.format.MoneyInput
import com.vaibhav.emicalc.ui.format.RateFormat
import com.vaibhav.emicalc.ui.format.TenureFormat
import com.vaibhav.emicalc.ui.viewmodel.rememberLoansViewModel

/** Every loan in one place, which is the view single-loan bank calculators cannot give. */
@Composable
fun LoansScreen(
    container: AppContainer,
    onAddLoan: () -> Unit,
    onEditLoan: (String) -> Unit,
    onViewSchedule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberLoansViewModel(container)
    val stored by viewModel.stored.collectAsState()
    val portfolio by viewModel.portfolio.collectAsState()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddLoan) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add loan", Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (stored.isEmpty()) {
                item {
                    Text(
                        "Add your loans to see what they cost together, and what a lump sum " +
                            "would do if you paid one of them down.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        HeadlineFigure(
                            caption = "Total monthly EMI",
                            value = MoneyFormat.rupees(portfolio.totalMonthlyEmi),
                            detail = "Debt free in ${TenureFormat.long(portfolio.debtFreeInMonths)}",
                        )
                        Spacer(Modifier.height(12.dp))
                        AmountRow("Outstanding", portfolio.totalOutstanding)
                        AmountRow("Interest still to pay", portfolio.totalInterest)
                    }
                }
                item { SectionHeader("Loans") }
                items(stored, key = { it.id }) { row ->
                    LoanCard(
                        loan = row.loan,
                        onClick = { onViewSchedule(row.id) },
                        onEdit = { onEditLoan(row.id) },
                        onDelete = { viewModel.delete(row.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun LoanCard(
    loan: Loan,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emi = remember(loan) {
        EmiCalculator.emi(loan.principal, loan.annualRatePercent, loan.tenureMonths)
    }
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(loan.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${MoneyFormat.compact(loan.principal)} at " +
                        "${RateFormat.percent(loan.annualRatePercent)} for " +
                        TenureFormat.short(loan.tenureMonths),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                MoneyText(emi, emphasis = true)
            }
            Column {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${loan.name}")
                }
                OutlinedButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
}

/**
 * Add or edit a loan.
 *
 * The form keeps a draft as it is typed, so closing the app halfway does not lose it.
 */
@Composable
fun LoanEditScreen(
    container: AppContainer,
    loanId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberLoansViewModel(container)

    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var tenure by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(loanId == null) }

    LaunchedEffect(loanId) {
        if (loanId != null) {
            viewModel.find(loanId)?.let { existing ->
                name = existing.name
                principal = existing.principal.toPlainString()
                rate = existing.annualRatePercent.toPlainString()
                tenure = existing.tenureMonths.toString()
            }
            loaded = true
        } else {
            // Offer back an abandoned draft rather than a blank form.
            viewModel.loadDraft()?.let { point ->
                point.draft.split('\u001F').let { parts ->
                    if (parts.size == 4) {
                        name = parts[0]; principal = parts[1]; rate = parts[2]; tenure = parts[3]
                    }
                }
            }
        }
    }

    // Persist the draft whenever a field settles.
    LaunchedEffect(name, principal, rate, tenure) {
        if (loanId == null && (name.isNotBlank() || principal.isNotBlank())) {
            viewModel.saveDraft(
                route = "loans/edit",
                draft = listOf(name, principal, rate, tenure).joinToString("\u001F"),
                entryId = null,
                focusField = null,
            )
        }
    }

    val parsedPrincipal = MoneyInput.parsePositiveAmount(principal)
    val parsedRate = MoneyInput.parseRate(rate)
    val parsedTenure = MoneyInput.parseTenureMonths(tenure)

    val loan: Loan? = remember(name, parsedPrincipal, parsedRate, parsedTenure) {
        val p = (parsedPrincipal as? InputResult.Valid)?.value
        val r = (parsedRate as? InputResult.Valid)?.value
        val t = (parsedTenure as? InputResult.Valid)?.value
        if (p != null && r != null && t != null && name.isNotBlank()) {
            runCatching { Loan(name.trim(), p, r, t) }.getOrNull()
        } else {
            null
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (loanId == null) "Add a loan" else "Edit loan",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ParsedField(
            label = "Loan amount",
            value = principal,
            onValueChange = { principal = it },
            parse = MoneyInput::parsePositiveAmount,
            supporting = "You can type 80L, 1.5Cr or 80,00,000",
        )
        ParsedField(
            label = "Interest rate",
            value = rate,
            onValueChange = { rate = it },
            parse = MoneyInput::parseRate,
            supporting = "Annual, reducing balance",
        )
        ParsedField(
            label = "Tenure",
            value = tenure,
            onValueChange = { tenure = it },
            parse = MoneyInput::parseTenureMonths,
            supporting = "Months, or type 20y",
            keyboardType = KeyboardType.Text,
        )

        Spacer(Modifier.height(12.dp))

        if (loan != null) {
            val emi = EmiCalculator.emi(loan.principal, loan.annualRatePercent, loan.tenureMonths)
            WorkingCard(
                title = "EMI ${MoneyFormat.rupees(emi)}",
                formula = "P x r x (1+r)^n / ((1+r)^n - 1)",
                lines = listOf(
                    "P (principal)" to MoneyFormat.rupees(loan.principal),
                    "r (monthly rate)" to "${loan.annualRatePercent.toPlainString()}% / 12",
                    "n (instalments)" to loan.tenureMonths.toString(),
                ),
                source = "Monthly reducing balance, EMI rounded to whole rupees as lenders quote it.",
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { loan?.let { viewModel.save(loanId, it); onDone() } },
            enabled = loan != null && loaded,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loanId == null) "Save loan" else "Save changes")
        }
    }
}

/** The month-by-month schedule, which is what people check against their statement. */
@Composable
fun AmortisationScreen(
    container: AppContainer,
    loanId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberLoansViewModel(container)
    var loan by remember { mutableStateOf<Loan?>(null) }

    LaunchedEffect(loanId) {
        loan = loanId?.let { viewModel.find(it) }
    }

    val result: AmortisationResult? = remember(loan) { loan?.let { EmiCalculator.amortise(it) } }
    val current = loan
    val schedule = result

    if (current == null || schedule == null) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            Text("Loan not found.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text(current.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                HeadlineFigure(
                    caption = "EMI",
                    value = MoneyFormat.rupees(schedule.scheduledEmi),
                    detail = "${schedule.months} instalments, " +
                        "${MoneyFormat.compact(schedule.totalInterest)} interest in total",
                )
                if (schedule.finalEmi.compareTo(schedule.scheduledEmi) != 0) {
                    Spacer(Modifier.height(8.dp))
                    WorkingCard(
                        title = "Final instalment differs",
                        formula = "last instalment = outstanding balance + final month's interest",
                        lines = listOf(
                            "Scheduled EMI" to MoneyFormat.rupees(schedule.scheduledEmi),
                            "Final instalment" to MoneyFormat.rupeesWithPaise(schedule.finalEmi),
                        ),
                        source = "The EMI is quoted in whole rupees, so a small residue is left " +
                            "over. Lenders put it in the last instalment rather than adding an " +
                            "extra month, and so does this schedule.",
                    )
                }
            }
        }
        item {
            SectionHeader("Schedule")
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("#", "Interest", "Principal", "Balance").forEachIndexed { index, header ->
                    Text(
                        header,
                        Modifier.weight(if (index == 0) 0.5f else 1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
        itemsIndexed(schedule.instalments, key = { _, row -> row.number }) { _, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    row.number.toString(),
                    Modifier.weight(0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                listOf(row.interest, row.principal, row.closingBalance).forEach { value ->
                    Text(
                        MoneyFormat.group(value),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
