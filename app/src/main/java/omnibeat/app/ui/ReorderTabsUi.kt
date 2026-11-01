package omnibeat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import omnibeat.app.R
import omnibeat.app.model.MainPage
import omnibeat.app.model.Playlist
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.ui.appStringResource as stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReorderTabsPage(
    tabs: List<MainPage>,
    playlists: List<Playlist>,
    dividerStyle: StationDividerStyle,
    onMove: (Int, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }
    Box(Modifier.fillMaxSize().background(RadioBackground)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tabs, key = { _, page -> page.name }) { index, page ->
                ReorderableItem(reorderState, key = page.name) { dragging ->
                    StationListItem(
                        title = (page as? MainPage.Playlist)?.let { tab ->
                            playlists.firstOrNull { it.id == tab.id }?.title
                        } ?: stringResource(page.titleRes()),
                        tags = emptyList(),
                        imageUrl = null,
                        showArtwork = false,
                        selected = dragging,
                        enabled = true,
                        onClick = {},
                        startPadding = RadioSpacing.small,
                        showDivider = index < tabs.lastIndex,
                        dividerStyle = dividerStyle,
                        modifier = Modifier.animateItem(),
                        leadingContent = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(end = RadioSpacing.small)
                                    .size(RadioSizes.iconLarge)
                                    .draggableHandle(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_indicator),
                                    contentDescription = stringResource(R.string.action_reorder_tabs),
                                    tint = if (dragging) RadioPrimary else RadioTextMuted,
                                    modifier = Modifier.size(RadioSizes.icon),
                                )
                            }
                        },
                    )
                }
            }
        }
        OmniLazyListScrollIndicator(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = RadioSpacing.extraSmall),
        )
    }
}
