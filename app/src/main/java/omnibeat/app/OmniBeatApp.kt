package omnibeat.app

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniBeatApp() {
    OmniBeatTheme {
        val context = LocalContext.current
        val repository = remember(context) { StationRepository(context.applicationContext) }
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val player = remember { ExoPlayer.Builder(context).build() }

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
        var selectedTab by remember { mutableStateOf(MainTab.Stations) }

        LaunchedEffect(repository) {
            repository.seedTestStationsForPrototype()
            repository.stations.collect { savedStations ->
                stations = savedStations
                if (selectedIndex !in savedStations.indices) {
                    selectedIndex = -1
                    selectedStation = null
                }
            }
        }

        LaunchedEffect(repository) {
            repository.appVolume.collect { savedVolume ->
                appVolume = savedVolume
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
                        val streamTitle = IcyMetadataParser.readStreamTitle(metadata[index].toString())
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
            errorText?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }

        fun playStationAt(index: Int) {
            val station = stations.getOrNull(index) ?: return
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
                        StreamResolver.resolvePlayableUrl(station.sourceUrl)
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
        }

        fun playAdjacentStation(direction: Int) {
            if (stations.isEmpty()) return
            val nextIndex = if (selectedIndex in stations.indices) {
                (selectedIndex + direction + stations.size) % stations.size
            } else if (direction > 0) {
                0
            } else {
                stations.lastIndex
            }
            playStationAt(nextIndex)
        }

        fun playRandomStation() {
            if (stations.isEmpty()) return
            val nextIndex = if (stations.size == 1) {
                0
            } else {
                var randomIndex: Int
                do {
                    randomIndex = Random.nextInt(stations.size)
                } while (randomIndex == selectedIndex)
                randomIndex
            }
            playStationAt(nextIndex)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = { DrawerContent() },
        ) {
            Scaffold(
                containerColor = RadioBackground,
                topBar = {
                    MainTopBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onAddStation = {
                            editorState = StationEditorState(
                                stationIndex = null,
                                name = "",
                                sourceUrl = "",
                            )
                        },
                    )
                },
                bottomBar = {
                    PlayerPanel(
                        station = selectedStation,
                        trackText = errorText ?: trackText,
                        loading = resolving || buffering,
                        resolving = resolving,
                        isPlaying = isPlaying,
                        canNavigateStations = stations.isNotEmpty(),
                        appVolume = appVolume,
                        canPlay = stations.isNotEmpty(),
                        onPlayPause = {
                            if (player.isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else if (selectedStation == null) {
                                playStationAt(0)
                            } else {
                                player.play()
                            }
                        },
                        onPreviousStation = { playAdjacentStation(-1) },
                        onNextStation = { playAdjacentStation(1) },
                        onRandomStation = { playRandomStation() },
                        onVolumeChange = { volume ->
                            appVolume = volume
                            scope.launch { repository.saveAppVolume(volume) }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .pointerInput(selectedTab) {
                            var totalDragX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    totalDragX += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    val threshold = 80.dp.toPx()
                                    selectedTab = when {
                                        totalDragX < -threshold -> MainTab.Favorites
                                        totalDragX > threshold -> MainTab.Stations
                                        else -> selectedTab
                                    }
                                },
                            )
                        },
                ) {
                    when (selectedTab) {
                        MainTab.Stations -> {
                            if (stations.isEmpty()) {
                                EmptyStationsState(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 32.dp),
                                )
                            } else {
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
                                    onStationClick = { index, _ -> playStationAt(index) },
                                )
                            }
                        }

                        MainTab.Favorites -> {
                            EmptyFavoritesState(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp),
                            )
                        }
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
                    scope.launch { repository.saveStations(nextStations) }
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
                    scope.launch { repository.saveStations(nextStations) }
                    editorState = null
                },
            )
        }
    }
}

private enum class MainTab(val title: String) {
    Stations("Stations"),
    Favorites("Favorites"),
}

@Composable
private fun MainTopBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenDrawer: () -> Unit,
    onAddStation: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp)
            .padding(start = 4.dp, end = 8.dp),
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open drawer",
                tint = RadioText,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            MainTab.entries.forEach { tab ->
                Column(
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = tab.title,
                        color = if (selectedTab == tab) RadioText else RadioTextMuted,
                        fontSize = 18.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(72.dp)
                            .height(2.dp)
                            .background(if (selectedTab == tab) RadioPrimary else RadioOutline),
                    )
                }
            }
        }
        IconButton(onClick = onAddStation) {
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
                contentDescription = "Add station",
                tint = RadioText,
                modifier = Modifier.height(28.dp),
            )
        }
    }
}
