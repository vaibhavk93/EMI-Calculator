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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaibhav.emicalc.core.loan.PayoffStrategy
import com.vaibhav.emicalc.core.runway.RunwayCalculator
import com.vaibhav.emicalc.core.runway.RunwayInput
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.components.AmountRow
import com.vaibhav.emicalc.ui.components.HeadlineFigure
import com.vaibhav.emicalc.ui.components.ParsedField
import com.vaibhav.emicalc.ui.components.SectionHeader
import com.vaibhav.emicalc.ui.components.WorkingCard
import com.vaibhav.emicalc.ui.format.InputResult
import com.vaibhav.emicalc.ui.format.MoneyFormat
import com.vaibhav.emicalc.ui.format.MoneyInput
import com.vaibhav.emicalc.ui.format.TenureFormat
import com.vaibhav.emicalc.ui.viewmodel.rememberRunwayViewModel
import java.math.BigDecimal

/**
 * The screen the whole app exists for.
 *
 * A settlement figure on its own does not answer the question people actually have when
 * they resign, which is whether they can afford the gap. This puts the payout on one
 * side and the obligations it has to cover on the other, and says how many months that
 * buys. Months are floored, never rounded up: telling someone between jobs they have
 * "six months" when they have five and a half is the one rounding error here that does
 * real harm.
 */
@Composable
fun RunwayScreen(
    container: AppContainer,
    onAddLoan: () -> Unit,
    onOpenExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberRunwayViewModel(container)
    val portfolio by viewModel.portfolio.collectAsState()

    var settlement by remember { mutableStateOf("") }
    var savings by remember { mutableStateOf("") }
    var living by remember { mutableStateOf("") }
    var newSalary by remember { mutableStateOf("") }

    fun amount(raw: String): BigDecimal =
        (MoneyInput.parseAmount(raw) as? InputResult.Valid)?.value ?: BigDecimal.ZERO

    val input = RunwayInput(
        netSettlement = amount(settlement),
        liquidSavings = amount(savings),
        monthlyLivingCost = amount(living),
        expectedMonthlyIncome = amount(newSalary),
    )
    val result = remember(input, portfolio) {
        RunwayCalculator.compute(input, portfolio, PayoffStrategy.AVALANCHE)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Runway", style = MaterialTheme.typography.headlineLarge)
        Text(
            "If you left your job, how long would the money last?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                when {
                    result.indefinite && result.netMonthlyBurn.signum() == 0 &&
                        result.totalAvailable.signum() == 0 ->
                        HeadlineFigure(
                            caption = "Runway",
                            value = "—",
                            detail = "Add your loans and a payout figure to see this.",
                        )
                    result.indefinite ->
                        HeadlineFigure(
                            caption = "Runway",
                            value = "No end",
                            detail = "Your expected income covers the EMIs and living costs.",
                        )
                    else ->
                        HeadlineFigure(
                            caption = "Runway",
                            value = TenureFormat.short(result.months),
                            detail = if (result.trailingDays > 0) {
                                "plus about ${result.trailingDays} days"
                            } else {
                                null
                            },
                        )
                }
                if (!result.indefinite && result.emiOnlyMonths > result.months) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Covering only the EMIs and nothing else, it would stretch to " +
                            TenureFormat.long(result.emiOnlyMonths) + ".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        SectionHeader("What you have")
        ParsedField(
            label = "Net settlement from your exit",
            value = settlement,
            onValueChange = { settlement = it },
            parse = MoneyInput::parseAmount,
            supporting = "Work this out on the Exit tab, then bring the net figure here",
        )
        ParsedField(
            label = "Liquid savings",
            value = savings,
            onValueChange = { savings = it },
            parse = MoneyInput::parseAmount,
        )
        OutlinedButton(onClick = onOpenExit, modifier = Modifier.fillMaxWidth()) {
            Text("Work out my exit settlement")
        }

        SectionHeader("What it has to cover")
        AmountRow(
            "Monthly EMIs",
            portfolio.totalMonthlyEmi,
            basis = if (portfolio.loans.isEmpty()) {
                "No loans added yet"
            } else {
                "${portfolio.loans.size} loans"
            },
        )
        if (portfolio.loans.isEmpty()) {
            OutlinedButton(onClick = onAddLoan, modifier = Modifier.fillMaxWidth()) {
                Text("Add a loan")
            }
        }
        ParsedField(
            label = "Monthly living costs",
            value = living,
            onValueChange = { living = it },
            parse = MoneyInput::parseAmount,
            supporting = "Rent, food, bills, school fees",
        )
        ParsedField(
            label = "Expected monthly income",
            value = newSalary,
            onValueChange = { newSalary = it },
            parse = MoneyInput::parseAmount,
            supporting = "Leave blank if nothing is lined up",
        )

        if (result.netMonthlyBurn.signum() > 0) {
            Spacer(Modifier.height(12.dp))
            WorkingCard(
                title = "How this is worked out",
                formula = "runway = (settlement + savings) / (EMIs + living - income)",
                lines = listOf(
                    "Available" to MoneyFormat.rupees(result.totalAvailable),
                    "Monthly EMIs" to MoneyFormat.rupees(result.monthlyEmi),
                    "Living costs" to MoneyFormat.rupees(result.monthlyLivingCost),
                    "Expected income" to MoneyFormat.rupees(result.expectedMonthlyIncome),
                    "Net burn" to MoneyFormat.rupees(result.netMonthlyBurn),
                ),
                source = "Whole months only; part months are shown separately rather than " +
                    "rounded up.",
            )
        }

        result.bestPrepayment?.let { best ->
            Spacer(Modifier.height(12.dp))
            SectionHeader("If you paid down a loan instead")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(best.loanName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Putting ${MoneyFormat.compact(best.amountApplied)} into this loan " +
                            "would save ${MoneyFormat.compact(best.interestSaved)} in interest " +
                            "and finish it ${TenureFormat.long(best.monthsSaved)} sooner.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(
                        "Worth weighing against keeping the cash as runway. Clearing a loan " +
                            "lowers your monthly burn, but spending the payout leaves less to " +
                            "live on while you look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
