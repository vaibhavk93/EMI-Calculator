package com.vaibhav.emicalc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaibhav.emicalc.core.session.HistoryEntry
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.components.SectionHeader
import com.vaibhav.emicalc.ui.state.CalcKey
import com.vaibhav.emicalc.ui.viewmodel.CalculatorViewModel
import com.vaibhav.emicalc.ui.viewmodel.rememberCalculatorViewModel

/**
 * A scratch pad for the figures people work out while filling in the other screens.
 *
 * It earns its place by being *here*: every result lands in history with the expression
 * that produced it, so "where did that 1,37,500 come from" is answerable a week later.
 */
@Composable
fun CalculatorScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: CalculatorViewModel = rememberCalculatorViewModel(container)
    val state by viewModel.state.collectAsState()
    val tape by viewModel.tape.collectAsState()
    val noteTarget by viewModel.noteTarget.collectAsState()

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {

        // The tape sits above the keypad and scrolls, so prior work stays in view.
        SectionHeader("Recent")
        Box(Modifier.weight(1f)) {
            if (tape.isEmpty()) {
                Text(
                    "Calculations you run here are kept, and you can add a note to any of them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tape, key = { it.id }) { entry ->
                        TapeRow(
                            entry = entry,
                            onReuse = { viewModel.reuse(entry) },
                            onNote = { viewModel.promptNote(entry.id) },
                        )
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(
            state.expression.ifBlank { "0" },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
        Text(
            state.error ?: state.preview.let { if (it.isBlank()) "" else "= $it" },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = if (state.error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(Modifier.height(8.dp))
        Keypad(onKey = viewModel::onKey, modifier = Modifier.padding(bottom = 12.dp))
    }

    noteTarget?.let { id ->
        val existing = tape.firstOrNull { it.id == id }?.note?.text.orEmpty()
        NoteDialog(
            initial = existing,
            onDismiss = viewModel::dismissNote,
            onSave = { text -> viewModel.saveNote(id, text) },
        )
    }
}

@Composable
private fun TapeRow(
    entry: HistoryEntry,
    onReuse: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onReuse),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(
                    "= ${entry.summary}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                entry.note?.let {
                    Text(
                        it.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onNote) {
                Icon(Icons.Filled.NoteAdd, contentDescription = "Add a note")
            }
        }
    }
}

private val keypadRows: List<List<String>> = listOf(
    listOf("C", "(", ")", "%"),
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "⌫", "+"),
)

@Composable
private fun Keypad(onKey: (CalcKey) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keypadRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { label -> KeypadButton(label, onKey, Modifier.weight(1f)) }
            }
        }
        Button(
            onClick = { onKey(CalcKey.Equals) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("=", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun KeypadButton(label: String, onKey: (CalcKey) -> Unit, modifier: Modifier = Modifier) {
    val key = when (label) {
        "C" -> CalcKey.Clear
        "⌫" -> CalcKey.Backspace
        "(" -> CalcKey.OpenParen
        ")" -> CalcKey.CloseParen
        "%" -> CalcKey.Percent
        "." -> CalcKey.Dot
        "÷" -> CalcKey.Operator('/')
        "×" -> CalcKey.Operator('*')
        "+" -> CalcKey.Operator('+')
        "-" -> CalcKey.Operator('-')
        else -> CalcKey.Digit(label.first())
    }
    val isOperator = label in setOf("÷", "×", "+", "-", "C", "(", ")", "%", "⌫")
    TextButton(
        onClick = { onKey(key) },
        modifier = modifier.aspectRatio(1.4f),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isOperator) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        if (label == "⌫") {
            Icon(Icons.Filled.Backspace, contentDescription = "Backspace")
        } else {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
