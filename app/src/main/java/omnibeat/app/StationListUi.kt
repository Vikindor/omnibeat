package omnibeat.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EmptyStationsState(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("No stations here yet. Tap", color = RadioTextMuted, fontSize = 16.sp)
            Icon(
                painter = painterResource(R.drawable.ic_add_circle_outline),
                contentDescription = "Add station",
                tint = RadioTextMuted,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(18.dp),
            )
            Text("to add some", color = RadioTextMuted, fontSize = 16.sp)
        }
    }
}

@Composable
fun EmptyFavoritesState(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Text("No favorites yet", color = RadioTextMuted, fontSize = 16.sp)
    }
}

@Composable
fun StationList(
    stations: List<Station>,
    selectedIndex: Int,
    scrollToSelectedRequest: Int,
    scrollToStationId: String?,
    enabled: Boolean = true,
    reordering: Boolean = false,
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onFavoriteClick: (Int, Station) -> Unit,
    onStationEdit: (Int, Station) -> Unit,
    onStationClick: (Int, Station) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderableListState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }

    LaunchedEffect(scrollToSelectedRequest) {
        val targetIndex = stations.indexOfFirst { it.id == scrollToStationId }
        if (scrollToSelectedRequest > 0 && targetIndex in stations.indices) {
            val visibleIndexes = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (targetIndex !in visibleIndexes) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = enabled || reordering,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = stations,
                key = { _, station -> station.id },
            ) { index, station ->
                ReorderableItem(
                    state = reorderableListState,
                    key = station.id,
                    enabled = reordering,
                ) { isDragging ->
                    StationRow(
                        station = station,
                        selected = selectedIndex == index,
                        enabled = enabled,
                        reordering = reordering,
                        dragging = isDragging,
                        dragHandleModifier = if (reordering) {
                            Modifier.draggableHandle()
                        } else {
                            Modifier
                        },
                        onFavoriteClick = { onFavoriteClick(index, station) },
                        onClick = { onStationClick(index, station) },
                        onLongClick = { onStationEdit(index, station) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        StationScrollIndicator(
            listState = listState,
            itemCount = stations.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        )
    }
}

@Composable
private fun StationScrollIndicator(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (itemCount == 0 || visibleItems.isEmpty() || visibleItems.size >= itemCount) {
        return
    }

    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
    val averageItemHeight = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val contentHeight = averageItemHeight * itemCount
    val scrollOffset = listState.firstVisibleItemIndex * averageItemHeight + listState.firstVisibleItemScrollOffset
    val thumbHeightFraction = (viewportHeight / contentHeight).coerceIn(0.12f, 1f)
    val thumbTopFraction = (scrollOffset / (contentHeight - viewportHeight).coerceAtLeast(1f))
        .coerceIn(0f, 1f - thumbHeightFraction)

    Canvas(
        modifier = modifier
            .width(3.dp)
            .fillMaxHeight(),
    ) {
        drawRect(color = RadioOutline.copy(alpha = 0.55f), size = size)
        drawRect(
            color = RadioPrimary,
            topLeft = androidx.compose.ui.geometry.Offset(x = 0f, y = size.height * thumbTopFraction),
            size = androidx.compose.ui.geometry.Size(
                width = size.width,
                height = size.height * thumbHeightFraction,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationRow(
    station: Station,
    selected: Boolean,
    enabled: Boolean,
    reordering: Boolean,
    dragging: Boolean,
    dragHandleModifier: Modifier,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) RadioSurfaceHigh else RadioBackground)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = if (reordering) 8.dp else 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        if (reordering) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
                    .then(dragHandleModifier),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_indicator),
                    contentDescription = "Drag station",
                    tint = if (dragging) RadioPrimary else RadioTextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.title,
                color = RadioText,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (station.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                ) {
                    station.tags.forEach { tag ->
                        Text(
                            text = tag,
                            color = if (selected) RadioText else RadioTextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(
                                    color = if (selected) {
                                        RadioPrimary.copy(alpha = 0.30f)
                                    } else {
                                        RadioSurfaceHigh.copy(alpha = 0.72f)
                                    },
                                    shape = RoundedCornerShape(percent = 50),
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        IconButton(
            enabled = enabled && !reordering,
            onClick = onFavoriteClick,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(40.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (station.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                ),
                contentDescription = if (station.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (station.isFavorite) RadioPrimary else RadioText,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
