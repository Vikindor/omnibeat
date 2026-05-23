package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.model.STATION_STREAM_URL_MAX_LENGTH
import omnibeat.app.model.STATION_TAGS_INPUT_MAX_LENGTH
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH

import androidx.core.net.toUri
import omnibeat.app.model.StationEditorState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun StationEditorDialog(
    state: StationEditorState,
    showDelete: Boolean,
    confirmStationDeletion: Boolean,
    onDismiss: () -> Unit,
    onSyncArtwork: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember(state) { mutableStateOf(state.title) }
    var streamUrl by remember(state) { mutableStateOf(state.streamUrl) }
    var tags by remember(state) { mutableStateOf(state.tags) }
    var confirmDelete by remember(state) { mutableStateOf(false) }
    var showUrlError by remember(state) { mutableStateOf(false) }
    val trimmedTitle = title.trim()
    val trimmedStreamUrl = streamUrl.trim()
    val hasValidStreamUrl = isValidStreamUrl(trimmedStreamUrl)
    val textFieldColors = omniTextFieldColors()
    val formattedDateAdded = remember(state.dateAdded) { formatDateAdded(state.dateAdded) }

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
                if (onSyncArtwork != null) {
                    IconButton(onClick = onSyncArtwork) {
                        Icon(
                            painter = painterResource(R.drawable.ic_image_search),
                            contentDescription = "Sync station artwork",
                            tint = RadioPrimary,
                        )
                    }
                }
                if (showDelete) {
                    IconButton(
                        onClick = {
                            if (confirmStationDeletion) {
                                confirmDelete = true
                            } else {
                                onDelete()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "Delete station",
                            tint = RadioDanger,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(STATION_TITLE_MAX_LENGTH) },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    label = { Text("Title") },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it.take(STATION_STREAM_URL_MAX_LENGTH)
                        showUrlError = false
                    },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    label = { Text("Stream URL") },
                    placeholder = { Text("https://...") },
                    isError = showUrlError && !hasValidStreamUrl,
                    supportingText = if (showUrlError && !hasValidStreamUrl) {
                        { Text("A valid http or https URL is required") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(STATION_TAGS_INPUT_MAX_LENGTH) },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    label = { Text("Tags") },
                    placeholder = { Text("Comma separated") },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                formattedDateAdded?.let { dateAdded ->
                    OutlinedTextField(
                        value = dateAdded,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        singleLine = false,
                        minLines = 1,
                        maxLines = 2,
                        label = { Text("Date added") },
                        colors = omniDisabledTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OmniSecondaryButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                OmniPrimaryButton(
                    text = "Save",
                    onClick = {
                        if (hasValidStreamUrl) {
                            onSave(trimmedTitle, trimmedStreamUrl, tags)
                        } else {
                            showUrlError = true
                        }
                    },
                )
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
            text = { Text("This station will be removed from your library") },
            confirmButton = {
                OmniDangerButton(
                    text = "Delete",
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                )
            },
            dismissButton = {
                OmniSecondaryButton(text = "Cancel", onClick = { confirmDelete = false })
            },
            containerColor = RadioSurface,
            titleContentColor = RadioText,
            textContentColor = RadioTextMuted,
        )
    }
}

private fun isValidStreamUrl(streamUrl: String): Boolean {
    val uri = streamUrl.toUri()
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

private fun formatDateAdded(dateAdded: String?): String? {
    val value = dateAdded?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault(value)
}
