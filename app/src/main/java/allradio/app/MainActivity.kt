package allradio.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private val Context.stationDataStore by preferencesDataStore(name = "stations")
private val stationsJsonKey = stringPreferencesKey("stations_json")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllRadioApp()
        }
    }
}

private data class Station(
    val id: String,
    val name: String,
    val formatLabel: String,
    val sourceUrl: String,
)

private data class StationEditorState(
    val stationIndex: Int?,
    val name: String,
    val sourceUrl: String,
)

private val RadioBackground = Color(0xFF0D0D10)
private val RadioSurface = Color(0xFF18181D)
private val RadioSurfaceHigh = Color(0xFF24242B)
private val RadioOutline = Color(0xFF353540)
private val RadioPrimary = Color(0xFF8F5CFF)
private val RadioPrimaryDark = Color(0xFF3F236E)
private val RadioText = Color(0xFFF4F1FA)
private val RadioTextMuted = Color(0xFFB7B2C3)

private val RadioColorScheme = darkColorScheme(
    primary = RadioPrimary,
    onPrimary = Color.White,
    secondary = RadioPrimary,
    onSecondary = Color.White,
    background = RadioBackground,
    onBackground = RadioText,
    surface = RadioSurface,
    onSurface = RadioText,
    surfaceVariant = RadioSurfaceHigh,
    onSurfaceVariant = RadioTextMuted,
    outline = RadioOutline,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllRadioApp() {
    MaterialTheme(colorScheme = RadioColorScheme) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val player = remember {
            ExoPlayer.Builder(context).build()
        }

        var stations by remember { mutableStateOf(emptyList<Station>()) }
        var selectedIndex by remember { mutableIntStateOf(-1) }
        var selectedStation by remember { mutableStateOf<Station?>(null) }
        var playableUrl by remember { mutableStateOf("") }
        var trackText by remember { mutableStateOf("No stream is playing") }
        var resolving by remember { mutableStateOf(false) }
        var buffering by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf<String?>(null) }
        var appVolume by remember { mutableFloatStateOf(0.75f) }
        var isPlaying by remember { mutableStateOf(false) }
        var editorState by remember { mutableStateOf<StationEditorState?>(null) }

        LaunchedEffect(context) {
            context.applicationContext.seedTestStationsForPrototype()
            context.applicationContext.stationDataStore.data
                .map { preferences -> preferences[stationsJsonKey].orEmpty() }
                .collect { stationsJson ->
                    stations = decodeStations(stationsJson)
                    if (selectedIndex !in stations.indices) {
                        selectedIndex = -1
                        selectedStation = null
                    }
                }
        }

        LaunchedEffect(player, appVolume) {
            player.volume = appVolume
        }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == Player.STATE_BUFFERING
                    isPlaying = player.isPlaying
                }

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title?.toString().orEmpty()
                    val artist = mediaMetadata.artist?.toString().orEmpty()
                    if (title.isNotBlank()) {
                        trackText = if (artist.isNotBlank()) "$artist - $title" else title
                    }
                }

                override fun onMetadata(metadata: Metadata) {
                    repeat(metadata.length()) { index ->
                        val streamTitle = readIcyStreamTitle(metadata[index].toString())
                        if (!streamTitle.isNullOrBlank()) {
                            trackText = streamTitle
                            return
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    errorText = error.message ?: "Playback error"
                    resolving = false
                    buffering = false
                }
            }

            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }

        LaunchedEffect(errorText) {
            errorText?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                DrawerContent()
            },
        ) {
            Scaffold(
                containerColor = RadioBackground,
                topBar = {
                    TopAppBar(
                        title = { Text("AllRadio", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = RadioBackground,
                            titleContentColor = RadioText,
                            navigationIconContentColor = RadioText,
                        ),
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                },
                bottomBar = {
                    PlayerPanel(
                        station = selectedStation,
                        playableUrl = playableUrl,
                        trackText = errorText ?: trackText,
                        loading = resolving || buffering,
                        resolving = resolving,
                        isPlaying = isPlaying,
                        appVolume = appVolume,
                        onPlayPause = {
                            if (player.isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.play()
                            }
                        },
                        onVolumeChange = { appVolume = it },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
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
                        IconButton(
                            onClick = {
                                editorState = StationEditorState(
                                    stationIndex = null,
                                    name = "",
                                    sourceUrl = "",
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCircleOutline,
                                contentDescription = "Add station",
                                tint = RadioText,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    StationList(
                        stations = stations,
                        selectedIndex = selectedIndex,
                        onStationEdit = { index, station ->
                            editorState = StationEditorState(
                                stationIndex = index,
                                name = station.name,
                                sourceUrl = station.sourceUrl,
                            )
                        },
                        onStationClick = { index, station ->
                            selectedIndex = index
                            selectedStation = station
                            playableUrl = station.sourceUrl
                            trackText = "Resolving stream..."
                            errorText = null
                            resolving = true
                            buffering = false
                            scope.launch { drawerState.close() }
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        resolvePlayableUrl(station.sourceUrl)
                                    }
                                }.onSuccess { resolvedUrl ->
                                    resolving = false
                                    playableUrl = resolvedUrl
                                    trackText = "Waiting for metadata..."
                                    player.setMediaItem(MediaItem.fromUri(Uri.parse(resolvedUrl)))
                                    player.prepare()
                                    player.play()
                                }.onFailure { error ->
                                    resolving = false
                                    buffering = false
                                    errorText = "Could not resolve stream: ${error.message}"
                                }
                            }
                        },
                    )
                    if (stations.isEmpty()) {
                        EmptyStationsState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                        )
                    }
                }
            }
        }
        editorState?.let { state ->
            StationEditorDialog(
                state = state,
                showDelete = state.stationIndex != null,
                onDismiss = { editorState = null },
                onDelete = {
                    val index = state.stationIndex ?: return@StationEditorDialog
                    val nextStations = stations.toMutableList().also { it.removeAt(index) }
                    stations = nextStations
                    if (selectedIndex == index) {
                        player.stop()
                        selectedIndex = -1
                        selectedStation = null
                        playableUrl = ""
                        trackText = "No stream is playing"
                        isPlaying = false
                    } else if (selectedIndex > index) {
                        selectedIndex -= 1
                    }
                    scope.launch { context.applicationContext.saveStations(nextStations) }
                    editorState = null
                },
                onSave = { name, sourceUrl ->
                    val updatedStation = Station(
                        id = state.stationIndex?.let { stations[it].id } ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        formatLabel = state.stationIndex?.let { stations[it].formatLabel } ?: "Custom stream",
                        sourceUrl = sourceUrl.trim(),
                    )
                    val nextStations = stations.toMutableList().also { list ->
                        val index = state.stationIndex
                        if (index == null) {
                            list.add(updatedStation)
                        } else {
                            list[index] = updatedStation
                        }
                    }
                    stations = nextStations
                    if (state.stationIndex == selectedIndex) {
                        selectedStation = updatedStation
                        playableUrl = updatedStation.sourceUrl
                    }
                    scope.launch { context.applicationContext.saveStations(nextStations) }
                    editorState = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyStationsState(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Text(
            text = "No stations yet. Press + to add one.",
            color = RadioTextMuted,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun StationList(
    stations: List<Station>,
    selectedIndex: Int,
    onStationEdit: (Int, Station) -> Unit,
    onStationClick: (Int, Station) -> Unit,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
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
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
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
        drawRect(
            color = RadioOutline.copy(alpha = 0.55f),
            size = size,
        )
        drawRect(
            color = RadioPrimary,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = 0f,
                y = size.height * thumbTopFraction,
            ),
            size = androidx.compose.ui.geometry.Size(
                width = size.width,
                height = size.height * thumbHeightFraction,
            ),
        )
    }
}

@Composable
private fun DrawerContent() {
    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = RadioSurface,
        drawerContentColor = RadioText,
        modifier = Modifier
            .width(304.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp),
        ) {
            Text("AllRadio", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Prototype",
                color = RadioTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            DrawerLine("Stations")
            DrawerLine("Formats: direct, PLS, M3U, HLS")
            DrawerLine("Theme: dark Material 3")
        }
    }
}

@Composable
private fun DrawerLine(text: String) {
    NavigationDrawerItem(
        label = { Text(text) },
        selected = false,
        onClick = {},
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = RadioText,
        ),
    )
}

@Composable
private fun StationEditorDialog(
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

@Composable
private fun PlayerPanel(
    station: Station?,
    playableUrl: String,
    trackText: String,
    loading: Boolean,
    resolving: Boolean,
    isPlaying: Boolean,
    appVolume: Float,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioSurface)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DividerDefaults.Thickness)
                .background(RadioOutline),
        )
        Text(
            text = station?.name ?: "Choose a station",
            color = RadioText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = trackText,
            color = RadioTextMuted,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (playableUrl.isNotBlank()) {
            Text(
                text = playableUrl,
                color = RadioTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            FilledTonalButton(
                enabled = station != null && !resolving,
                onClick = onPlayPause,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = RadioSurfaceHigh,
                    contentColor = RadioText,
                    disabledContainerColor = RadioSurfaceHigh,
                    disabledContentColor = RadioTextMuted,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .width(132.dp)
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = RadioPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(28.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = RadioPrimary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            VolumeSlider(
                volume = appVolume,
                onVolumeChange = onVolumeChange,
            )
        }
    }
}

@Composable
private fun VolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(132.dp)
            .height(48.dp)
            .background(RadioSurfaceHigh, RoundedCornerShape(percent = 50))
            .padding(start = 14.dp, end = 10.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Volume",
            tint = RadioPrimary,
            modifier = Modifier.size(20.dp),
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = RadioText,
                activeTrackColor = RadioPrimary,
                inactiveTrackColor = RadioOutline,
            ),
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
        )
    }
}

@Throws(IOException::class)
private fun resolvePlayableUrl(sourceUrl: String): String {
    val lower = sourceUrl.lowercase(Locale.US)
    return when {
        ".pls" in lower -> resolvePls(sourceUrl)
        ".m3u" in lower && ".m3u8" !in lower -> resolveM3u(sourceUrl)
        else -> sourceUrl
    }
}

@Throws(IOException::class)
private fun resolvePls(sourceUrl: String): String {
    readRemoteText(sourceUrl).forEach { line ->
        val trimmed = line.trim()
        val equals = trimmed.indexOf('=')
        if (equals > 0 && trimmed.substring(0, equals).lowercase(Locale.US).startsWith("file")) {
            val url = trimmed.substring(equals + 1).trim()
            if (url.startsWith("http")) {
                return url
            }
        }
    }
    throw IOException("PLS playlist has no stream URL")
}

@Throws(IOException::class)
private fun resolveM3u(sourceUrl: String): String {
    readRemoteText(sourceUrl).forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            return URL(URL(sourceUrl), trimmed).toString()
        }
    }
    throw IOException("M3U playlist has no stream URL")
}

@Throws(IOException::class)
private fun readRemoteText(sourceUrl: String): List<String> {
    val connection = URL(sourceUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "AllRadio/1.0")
    return try {
        BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.lineSequence().toList()
        }
    } finally {
        connection.disconnect()
    }
}

private fun readIcyStreamTitle(metadataText: String): String? {
    val marker = "StreamTitle="
    var start = metadataText.indexOf(marker)
    if (start < 0) {
        return null
    }
    start += marker.length
    while (start < metadataText.length && metadataText[start] in charArrayOf('\'', '"', ' ')) {
        start++
    }
    var end = metadataText.indexOf(';', start)
    if (end < 0) {
        end = metadataText.length
    }
    return metadataText
        .substring(start, end)
        .trim()
        .trimEnd('\'', '"')
}

private suspend fun Context.saveStations(stations: List<Station>) {
    stationDataStore.edit { preferences ->
        preferences[stationsJsonKey] = encodeStations(stations)
    }
}

private suspend fun Context.seedTestStationsForPrototype() {
    stationDataStore.edit { preferences ->
        if (preferences[stationsJsonKey].isNullOrBlank()) {
            val stationsJson = resources
                .openRawResource(R.raw.test_stations)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            preferences[stationsJsonKey] = encodeStations(decodeStations(stationsJson))
        }
    }
}

private fun encodeStations(stations: List<Station>): String {
    val items = JSONArray()
    stations.forEach { station ->
        items.put(
            JSONObject()
                .put("id", station.id)
                .put("name", station.name)
                .put("formatLabel", station.formatLabel)
                .put("sourceUrl", station.sourceUrl),
        )
    }
    return items.toString()
}

private fun decodeStations(stationsJson: String): List<Station> {
    if (stationsJson.isBlank()) {
        return emptyList()
    }
    return runCatching {
        val items = JSONArray(stationsJson)
        buildList {
            repeat(items.length()) { index ->
                val item = items.getJSONObject(index)
                val name = item.optString("name").trim()
                val sourceUrl = item.optString("sourceUrl").trim()
                if (name.isNotEmpty() && sourceUrl.isNotEmpty()) {
                    add(
                        Station(
                            id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                            name = name,
                            formatLabel = item.optString("formatLabel", "Custom stream"),
                            sourceUrl = sourceUrl,
                        ),
                    )
                }
            }
        }
    }.getOrElse {
        emptyList()
    }
}
