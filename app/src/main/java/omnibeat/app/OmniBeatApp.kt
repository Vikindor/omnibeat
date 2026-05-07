package omnibeat.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
        val playbackState by PlaybackService.state.collectAsState()

        var stations by remember { mutableStateOf(emptyList<Station>()) }
        var appVolume by remember { mutableFloatStateOf(0.75f) }
        var editorState by remember { mutableStateOf<StationEditorState?>(null) }
        var selectedTab by remember { mutableStateOf(MainTab.Stations) }

        LaunchedEffect(repository) {
            repository.seedTestStationsForPrototype()
            repository.stations.collect { savedStations ->
                stations = savedStations
            }
        }

        LaunchedEffect(repository) {
            repository.appVolume.collect { savedVolume ->
                appVolume = savedVolume
            }
        }

        LaunchedEffect(playbackState.errorText) {
            playbackState.errorText?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }

        fun playStationAt(index: Int) {
            scope.launch { drawerState.close() }
            PlaybackService.playStation(context, index)
        }

        fun playAdjacentStation(direction: Int) {
            if (stations.isEmpty()) return
            val currentIndex = playbackState.selectedIndex
            val nextIndex = if (currentIndex in stations.indices) {
                (currentIndex + direction + stations.size) % stations.size
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
                } while (randomIndex == playbackState.selectedIndex)
                randomIndex
            }
            playStationAt(nextIndex)
        }

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
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
                                title = "",
                                streamUrl = "",
                                tags = "",
                            )
                        },
                    )
                },
                bottomBar = {
                    PlayerPanel(
                        station = playbackState.selectedStation,
                        trackText = playbackState.errorText ?: playbackState.trackText,
                        loading = playbackState.resolving || playbackState.buffering,
                        resolving = playbackState.resolving,
                        isPlaying = playbackState.isPlaying,
                        canNavigateStations = stations.isNotEmpty(),
                        appVolume = appVolume,
                        canPlay = stations.isNotEmpty(),
                        onPlayPause = {
                            if (playbackState.selectedStation == null) {
                                playStationAt(0)
                            } else {
                                PlaybackService.playOrPause(context)
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
                                    selectedIndex = playbackState.selectedIndex,
                                    onStationEdit = { index, station ->
                                        editorState = StationEditorState(
                                            stationIndex = index,
                                            title = station.title,
                                            streamUrl = station.streamUrl,
                                            tags = station.tags.joinToString(", "),
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
                    if (playbackState.selectedIndex == index) {
                        PlaybackService.stop(context)
                    }
                    scope.launch { repository.saveStations(nextStations) }
                    editorState = null
                },
                onSave = { title, streamUrl, tags ->
                    val trimmedStreamUrl = streamUrl.trim()
                    val updatedStation = Station(
                        id = state.stationIndex?.let { stations[it].id } ?: UUID.randomUUID().toString(),
                        title = title.trim().ifBlank { trimmedStreamUrl.take(STATION_TITLE_MAX_LENGTH) },
                        streamUrl = trimmedStreamUrl,
                        tags = parseTags(tags),
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

private fun parseTags(tags: String): List<String> {
    return tags.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
}

@Composable
private fun MainTopBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onOpenDrawer: () -> Unit,
    onAddStation: () -> Unit,
) {
    val density = LocalDensity.current

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
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "Open drawer",
                tint = RadioText,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            MainTab.entries.forEach { tab ->
                var tabTextWidth by remember(tab) { mutableStateOf(0.dp) }
                val interactionSource = remember(tab) { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = tab.title,
                        color = if (selectedTab == tab) RadioText else RadioTextMuted,
                        fontSize = 18.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.onSizeChanged { size ->
                            tabTextWidth = with(density) { size.width.toDp() }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(tabTextWidth)
                            .height(2.dp)
                            .background(if (selectedTab == tab) RadioPrimary else RadioOutline),
                    )
                }
            }
        }
        IconButton(onClick = onAddStation) {
            Icon(
                painter = painterResource(R.drawable.ic_add_circle_outline),
                contentDescription = "Add station",
                tint = RadioText,
                modifier = Modifier.height(28.dp),
            )
        }
    }
}
