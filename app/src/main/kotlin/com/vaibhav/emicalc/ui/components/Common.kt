package com.vaibhav.emicalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.vaibhav.emicalc.ui.format.InputResult
import com.vaibhav.emicalc.ui.format.MoneyFormat
import java.math.BigDecimal

/**
 * An amount, in a monospaced face so digits line up down a column.
 *
 * Comparing a schedule against a bank statement means reading a column of figures, and
 * proportional digits make that materially harder.
 */
@Composable
fun MoneyText(
    value: BigDecimal,
    modifier: Modifier = Modifier,
    withPaise: Boolean = false,
    emphasis: Boolean = false,
) {
    val negative = value.signum() < 0
    Text(
        text = if (withPaise) MoneyFormat.rupeesWithPaise(value) else MoneyFormat.rupees(value),
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
        style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/** A label on the left, an amount on the right. The workhorse row of every statement. */
@Composable
fun AmountRow(
    label: String,
    value: BigDecimal,
    modifier: Modifier = Modifier,
    basis: String? = null,
    emphasis: Boolean = false,
    withPaise: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            )
            if (basis != null) {
                Text(
                    basis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(value, emphasis = emphasis, withPaise = withPaise)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Shows the arithmetic behind a figure.
 *
 * This is the main trust mechanism in the app. An F&F number the user cannot reconcile
 * against their own payslip is worse than no number, so every computed amount can show
 * its formula, the inputs it used, and the rule it came from.
 */
@Composable
fun WorkingCard(
    title: String,
    formula: String,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    source: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                formula,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lines.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                lines.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            if (source != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tells the user which rule set produced the numbers, and when it was current. */
@Composable
fun RulesAsOfBadge(label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Rules applied", style = MaterialTheme.typography.labelMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "An estimate, not legal or tax advice. Check against your own offer " +
                    "letter and payslip before acting on it.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A text field that parses as you type and shows why an entry was rejected. */
@Composable
fun <T> ParsedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    parse: (String) -> InputResult<T>,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val result = parse(value)
    val error = (result as? InputResult.Invalid)?.reason
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = {
            val text = error ?: supporting
            if (text != null) Text(text, style = MaterialTheme.typography.bodySmall)
        },
    )
}

/** A dropdown for the company-policy assumptions the user is allowed to change. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> AssumptionPicker(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    render: (T) -> String,
    modifier: Modifier = Modifier,
    explanation: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = render(selected),
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(
                    androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                    true,
                ),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(render(option)) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
        if (explanation != null) {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

/** A labelled headline figure, for the top of the Runway and statement screens. */
@Composable
fun HeadlineFigure(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Monospace)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
