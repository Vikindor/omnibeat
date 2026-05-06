package omnibeat.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun StationHeader(onAddStation: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = "Stations",
            color = RadioText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAddStation) {
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
                contentDescription = "Add station",
                tint = RadioText,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun EmptyStationsState(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("No stations here yet. Tap", color = RadioTextMuted, fontSize = 16.sp)
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
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
fun StationList(
    stations: List<Station>,
    selectedIndex: Int,
    onStationEdit: (Int, Station) -> Unit,
    onStationClick: (Int, Station) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, stations.size) {
        if (selectedIndex in stations.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = stations,
                key = { _, station -> station.id },
            ) { index, station ->
                SwipeEditStationRow(
                    station = station,
                    onEdit = { onStationEdit(index, station) },
                ) {
                    StationRow(
                        station = station,
                        selected = selectedIndex == index,
                        onClick = { onStationClick(index, station) },
                        onLongClick = { onStationEdit(index, station) },
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
private fun SwipeEditStationRow(
    station: Station,
    onEdit: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(station.id) { Animatable(0f) }
    var rowWidthPx by remember(station.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioPrimaryDark)
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit station",
                tint = RadioText,
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(station.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val editThresholdPx = rowWidthPx * 0.15f
                            val shouldEdit = rowWidthPx > 0f && offsetX.value <= -editThresholdPx
                            if (shouldEdit) {
                                onEdit()
                            }
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            val maxRevealPx = rowWidthPx * 0.25f
                            val nextOffset = (offsetX.value + dragAmount).coerceIn(-maxRevealPx, 0f)
                            scope.launch { offsetX.snapTo(nextOffset) }
                            change.consume()
                        },
                    )
                },
        ) {
            content()
        }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) RadioSurfaceHigh else RadioBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = station.name,
            color = RadioText,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = station.formatLabel,
            color = RadioTextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
