package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.model.Station

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.net.HttpURLConnection
import java.net.URL

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
@Suppress("FrequentlyChangedStateReadInComposition")
fun StationList(
    stations: List<Station>,
    selectedIndex: Int,
    scrollToSelectedRequest: Int,
    scrollToStationId: String?,
    showArtwork: Boolean,
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
    val stationOrderSignature = stations.joinToString(separator = "|") { it.id }
    val previousStationOrderSignature = remember { mutableStateOf(stationOrderSignature) }
    val wasReordering = remember { mutableStateOf(reordering) }

    val enteringReorder = reordering && !wasReordering.value
    if ((!reordering || enteringReorder) && previousStationOrderSignature.value != stationOrderSignature) {
        listState.requestScrollToItem(
            index = listState.firstVisibleItemIndex.coerceIn(0, stations.lastIndex.coerceAtLeast(0)),
            scrollOffset = listState.firstVisibleItemScrollOffset,
        )
    }

    SideEffect {
        previousStationOrderSignature.value = stationOrderSignature
        wasReordering.value = reordering
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
                        showArtwork = showArtwork,
                        dragging = isDragging,
                        modifier = Modifier.animateItem(),
                        dragHandleModifier = if (reordering) {
                            Modifier.draggableHandle()
                        } else {
                            Modifier
                        },
                        onFavoriteClick = { onFavoriteClick(index, station) },
                        onClick = { onStationClick(index, station) },
                        onLongClick = { onStationEdit(index, station) },
                    )
                }
            }
        }
        OmniLazyListScrollIndicator(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationListItem(
    title: String,
    tags: List<String>,
    imageUrl: String?,
    showArtwork: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    startPadding: androidx.compose.ui.unit.Dp = 20.dp,
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
            .padding(start = startPadding, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        leadingContent?.invoke()
        if (showArtwork) {
            StationArtwork(
                imageUrl = imageUrl,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = RadioText,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StationTagPills(tags = tags, selected = selected)
        }
        trailingContent?.invoke()
    }
}

@Composable
fun StationTagPills(
    tags: List<String>,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) {
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
    ) {
        tags.forEach { tag ->
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

@Composable
private fun StationArtwork(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val normalizedUrl = imageUrl?.takeIf { it.isNotBlank() }
    var imageBitmap by remember(normalizedUrl) { mutableStateOf(StationArtworkMemoryCache[normalizedUrl]) }

    LaunchedEffect(normalizedUrl) {
        if (normalizedUrl == null || imageBitmap != null) return@LaunchedEffect
        imageBitmap = loadStationArtwork(normalizedUrl)?.also { bitmap ->
            StationArtworkMemoryCache[normalizedUrl] = bitmap
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(RadioSurfaceHigh.copy(alpha = 0.72f)),
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_radio_button_unchecked),
                contentDescription = null,
                tint = RadioTextMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private object StationArtworkMemoryCache {
    private val images = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

    operator fun get(url: String?): androidx.compose.ui.graphics.ImageBitmap? {
        if (url == null) return null
        return synchronized(images) { images[url] }
    }

    operator fun set(url: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        synchronized(images) { images[url] = bitmap }
    }
}

private suspend fun loadStationArtwork(imageUrl: String): androidx.compose.ui.graphics.ImageBitmap? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "OmniBeat Android")
            try {
                connection.inputStream.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationRow(
    station: Station,
    selected: Boolean,
    enabled: Boolean,
    reordering: Boolean,
    showArtwork: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    StationListItem(
        title = station.title,
        tags = station.tags,
        imageUrl = station.imageUrl,
        showArtwork = showArtwork,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        startPadding = if (reordering) 8.dp else 20.dp,
        leadingContent = if (reordering) {
            {
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
                    modifier = Modifier.size(24.dp),
                )
            }
            }
        } else {
            null
        },
        trailingContent = {
            OmniListActionIconButton(
                painter = painterResource(
                    if (station.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                ),
                contentDescription = if (station.isFavorite) "Remove from favorites" else "Add to favorites",
                enabled = enabled && !reordering,
                onClick = onFavoriteClick,
                tint = if (station.isFavorite) RadioPrimary else RadioText,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
        modifier = modifier,
    )
}
