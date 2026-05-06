package omnibeat.app

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = { DrawerContent() },
        ) {
            Scaffold(
                containerColor = RadioBackground,
                topBar = {
                    TopAppBar(
                        title = { Text("OmniBeat", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        .padding(padding),
                ) {
                    StationHeader(
                        onAddStation = {
                            editorState = StationEditorState(
                                stationIndex = null,
                                name = "",
                                sourceUrl = "",
                            )
                        },
                    )
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
                            },
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
