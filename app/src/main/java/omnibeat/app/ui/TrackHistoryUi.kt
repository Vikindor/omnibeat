package omnibeat.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import omnibeat.app.R
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.model.TrackHistoryEntry
import omnibeat.app.ui.appStringResource as stringResource
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val HistoryDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

@Composable
fun TrackHistoryActions(
    enabled: Boolean,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    OmniIconButton(
        painter = painterResource(R.drawable.ic_file_export),
        onClick = onExport,
        enabled = enabled,
    )
    OmniIconButton(
        painter = painterResource(R.drawable.ic_delete),
        onClick = { confirmClear = true },
        enabled = enabled,
        tint = RadioDanger,
    )

    if (confirmClear) {
        OmniConfirmDialog(
            title = stringResource(R.string.track_history_clear_title),
            text = stringResource(R.string.track_history_clear_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                onClear()
            },
        )
    }
}

@Composable
fun TrackHistoryPage(
    entries: List<TrackHistoryEntry>,
    dividerStyle: StationDividerStyle,
    copyStation: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.player_clipboard_track_label)
    val copiedText = stringResource(R.string.player_track_copied)
    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(RadioBackground)) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.track_history_empty),
                color = RadioTextMuted,
                fontSize = RadioTextSizes.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(RadioSpacing.page),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                    TrackHistoryRow(
                        entry = entry,
                        dividerStyle = dividerStyle,
                        showDivider = index < entries.lastIndex,
                        onClick = {
                            val text = if (copyStation) "${entry.trackText} | ${entry.stationTitle}" else entry.trackText
                            copyTextToClipboard(context, clipboardLabel, text)
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            OmniLazyListScrollIndicator(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun TrackHistoryRow(
    entry: TrackHistoryEntry,
    dividerStyle: StationDividerStyle,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(RadioSpacing.medium),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = RadioSpacing.page, vertical = RadioSpacing.rowVertical),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(RadioSpacing.extraSmall),
                modifier = Modifier.weight(1f),
            ) {
                Text(entry.trackText, color = RadioText, fontSize = RadioTextSizes.stationTitle)
                Text(entry.stationTitle, color = RadioTextMuted, fontSize = RadioTextSizes.bodySmall)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(RadioSpacing.extraSmall),
            ) {
                Text(entry.createdAt.format(HistoryTimeFormat), color = RadioTextMuted, fontSize = RadioTextSizes.caption)
                Text(entry.createdAt.format(HistoryDateFormat), color = RadioTextMuted, fontSize = RadioTextSizes.caption)
            }
        }
        if (showDivider) ListItemDivider(dividerStyle)
    }
}
