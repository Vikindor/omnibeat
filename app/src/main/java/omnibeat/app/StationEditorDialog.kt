package omnibeat.app

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun StationEditorDialog(
    state: StationEditorState,
    showDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(state) { mutableStateOf(state.name) }
    var sourceUrl by remember(state) { mutableStateOf(state.sourceUrl) }
    var confirmDelete by remember(state) { mutableStateOf(false) }
    val trimmedName = name.trim()
    val trimmedUrl = sourceUrl.trim()
    val canSave = trimmedName.isNotEmpty() && isValidStreamUrl(trimmedUrl)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.stationIndex == null) "Add station" else "Edit station",
                    modifier = Modifier.weight(1f),
                )
                if (showDelete) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete station",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Station name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    label = { Text("Stream URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = RadioSurfaceHigh,
                        contentColor = RadioText,
                    ),
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = canSave,
                    onClick = { onSave(trimmedName, trimmedUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadioPrimary,
                        contentColor = RadioText,
                    ),
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {},
        containerColor = RadioSurface,
        titleContentColor = RadioText,
        textContentColor = RadioTextMuted,
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete station?") },
            text = { Text("This station will be removed from your list") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = RadioSurfaceHigh,
                        contentColor = RadioText,
                    ),
                ) {
                    Text("Cancel")
                }
            },
            containerColor = RadioSurface,
            titleContentColor = RadioText,
            textContentColor = RadioTextMuted,
        )
    }
}

private fun isValidStreamUrl(sourceUrl: String): Boolean {
    val uri = Uri.parse(sourceUrl)
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}
