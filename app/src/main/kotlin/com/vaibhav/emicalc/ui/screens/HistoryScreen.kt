package com.vaibhav.emicalc.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin as PushPinOutlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vaibhav.emicalc.core.session.HistoryEntry
import com.vaibhav.emicalc.core.session.InteractionKind
import com.vaibhav.emicalc.core.session.Note
import com.vaibhav.emicalc.di.AppContainer
import com.vaibhav.emicalc.ui.components.SectionHeader
import com.vaibhav.emicalc.ui.viewmodel.rememberHistoryViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/**
 * Everything the app has remembered.
 *
 * Deletion here is reversible: a deleted entry moves to a recently-deleted list for a
 * month before being swept. A settlement can take half an hour to fill in, so a stray
 * tap must not destroy it. Pinning both floats an entry and protects it from deletion.
 */
@Composable
fun HistoryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel = rememberHistoryViewModel(container)
    val entries by viewModel.entries.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var noteTarget by remember { mutableStateOf<HistoryEntry?>(null) }

    val shown = remember(entries, filter) {
        if (filter == null) entries else entries.filter { it.kind == filter }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "History",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All") },
                )
                InteractionKind.entries.forEach { kind ->
                    FilterChip(
                        selected = filter == kind,
                        onClick = { viewModel.setFilter(kind) },
                        label = { Text(label(kind)) },
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    "Nothing saved yet. Calculations, loans and settlements you work out " +
                        "will appear here, and you can add a note to any of them.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(shown, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onNote = { noteTarget = entry },
                    onPin = { viewModel.setPinned(entry.id, !entry.pinned) },
                    onDelete = { viewModel.delete(entry.id) },
                )
            }
        }

        if (deleted.isNotEmpty()) {
            item { SectionHeader("Recently deleted") }
            item {
                Text(
                    "Kept for 30 days, then removed for good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(deleted, key = { "deleted-${it.id}" }) { entry ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewModel.restore(entry.id) }) { Text("Restore") }
                        TextButton(onClick = { viewModel.purge(entry.id) }) { Text("Delete now") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    noteTarget?.let { entry ->
        NoteDialog(
            initial = entry.note?.text.orEmpty(),
            onDismiss = { noteTarget = null },
            onSave = { text ->
                viewModel.annotate(entry.id, text)
                noteTarget = null
            },
        )
    }
}

@Composable
private fun EntryCard(
    entry: HistoryEntry,
    onNote: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label(entry.kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "  ·  ${stamp.format(entry.createdAt.atZone(ZoneId.systemDefault()))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(entry.summary, style = MaterialTheme.typography.titleMedium)
                entry.note?.let {
                    Text(
                        it.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column {
                IconButton(onClick = onPin) {
                    Icon(
                        if (entry.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPinOutlined,
                        contentDescription = if (entry.pinned) "Unpin" else "Pin",
                        tint = if (entry.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onNote) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = "Add a note")
                }
                if (!entry.pinned) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun NoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= Note.MAX_LENGTH) text = it },
                    label = { Text("What is this figure?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${text.length} / ${Note.MAX_LENGTH}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun label(kind: InteractionKind) = when (kind) {
    InteractionKind.CALCULATION -> "Calculation"
    InteractionKind.LOAN -> "Loan"
    InteractionKind.SETTLEMENT -> "Settlement"
    InteractionKind.RUNWAY -> "Runway"
}
