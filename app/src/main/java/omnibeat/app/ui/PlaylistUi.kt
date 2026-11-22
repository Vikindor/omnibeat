package omnibeat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import omnibeat.app.R
import omnibeat.app.model.Playlist
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH
import omnibeat.app.model.Station
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.ui.appStringResource as stringResource

enum class PlaylistMembershipMode {
    Add,
    Remove,
}

@Composable
fun PlaylistTitleDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.title.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RadioSpacing.large).widthIn(max = 560.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(if (playlist == null) R.string.playlist_create else R.string.playlist_rename)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(STATION_TITLE_MAX_LENGTH) },
                label = { EditableFieldLabel(stringResource(R.string.station_editor_title_label)) },
                minLines = 1,
                maxLines = 3,
                colors = omniTextFieldColors(),
                modifier = Modifier.heightIn(min = RadioSizes.textFieldMinHeight).fillMaxWidth(),
            )
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OmniSecondaryButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                OmniPrimaryButton(
                    text = stringResource(R.string.station_editor_save),
                    enabled = title.isNotBlank(),
                    onClick = { onSave(title.trim()) },
                )
            }
        },
        dismissButton = {},
        containerColor = RadioSurface,
        titleContentColor = RadioText,
        textContentColor = RadioTextMuted,
    )
}

@Composable
fun PlaylistMembershipPage(
    stations: List<Station>,
    playlist: Playlist,
    mode: PlaylistMembershipMode,
    showArtwork: Boolean,
    dividerStyle: StationDividerStyle,
    selectedStationId: String?,
    onStationClick: (Station) -> Unit,
    onMembershipChange: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val members = playlist.stationIds.toSet()
    if (stations.isEmpty()) {
        PlaylistEmptyState()
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(stations, key = { _, station -> station.id }) { index, station ->
                val added = station.id in members
                StationListItem(
                    title = station.title,
                    tags = station.tags,
                    imageUrl = station.imageUrl,
                    showArtwork = showArtwork,
                    selected = selectedStationId == station.id,
                    enabled = true,
                    onClick = { onStationClick(station) },
                    showDivider = index < stations.lastIndex,
                    dividerStyle = dividerStyle,
                    trailingContent = {
                        OmniIconButton(
                            painter = painterResource(
                                if (mode == PlaylistMembershipMode.Remove) R.drawable.ic_remove
                                else if (added) R.drawable.ic_check else R.drawable.ic_add,
                            ),
                            enabled = mode == PlaylistMembershipMode.Remove || !added,
                            tint = when {
                                mode == PlaylistMembershipMode.Remove -> RadioDanger
                                added -> RadioPrimary
                                else -> RadioText
                            },
                            disabledTint = if (added) RadioPrimary else RadioTextMuted,
                            onClick = { onMembershipChange(station.id) },
                            modifier = Modifier.padding(start = RadioSpacing.medium),
                        )
                    },
                )
            }
        }
        OmniLazyListScrollIndicator(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = RadioSpacing.extraSmall),
        )
    }
}

@Composable
fun PlaylistEmptyState() {
    Box(Modifier.fillMaxSize().padding(RadioSpacing.page), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.playlist_empty),
            color = RadioTextMuted,
            fontSize = RadioTextSizes.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
