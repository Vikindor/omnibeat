package omnibeat.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.absoluteValue
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
        var selectedPage by remember { mutableStateOf(MainPage.Stations) }
        var lastMainPage by remember { mutableStateOf(MainPage.Stations) }
        var sortState by remember { mutableStateOf(StationSortState()) }
        var customStationOrder by remember { mutableStateOf(emptyList<String>()) }
        var customFavoriteOrder by remember { mutableStateOf(emptyList<String>()) }
        var reorderDraft by remember { mutableStateOf<StationReorderDraft?>(null) }
        var scrollToSelectedRequest by remember { mutableIntStateOf(0) }
        var scrollToStationId by remember { mutableStateOf<String?>(null) }
        var lastPlayedStationId by remember { mutableStateOf<String?>(null) }
        var pendingExportJson by remember { mutableStateOf<String?>(null) }
        var pendingImportData by remember { mutableStateOf<StationExportData?>(null) }
        val pagerState = rememberPagerState(pageCount = { MainPage.tabPages.size })

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

        LaunchedEffect(repository) {
            repository.lastMainPage.collect { savedPage ->
                val restoredPage = MainPage.tabPages.firstOrNull { it.name == savedPage } ?: MainPage.Stations
                lastMainPage = restoredPage
                if (selectedPage in MainPage.tabPages) {
                    selectedPage = restoredPage
                }
            }
        }

        LaunchedEffect(repository) {
            repository.stationSortState.collect { savedSortState ->
                sortState = savedSortState
            }
        }

        LaunchedEffect(repository) {
            repository.customStationOrder.collect { savedOrder ->
                customStationOrder = savedOrder
            }
        }

        LaunchedEffect(repository) {
            repository.customFavoriteOrder.collect { savedOrder ->
                customFavoriteOrder = savedOrder
            }
        }

        LaunchedEffect(repository) {
            repository.lastPlayedStationId.collect { savedStationId ->
                lastPlayedStationId = savedStationId
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            val exportJson = pendingExportJson ?: return@rememberLauncherForActivityResult
            pendingExportJson = null
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(exportJson.toByteArray(Charsets.UTF_8))
                        } ?: error("Could not open export file")
                    }
                }.onSuccess {
                    Toast.makeText(context, "Stations exported", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                runCatching {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.readBytes().toString(Charsets.UTF_8)
                        } ?: error("Could not open import file")
                    }
                    StationExportCodec.decode(json)
                }.onSuccess { importData ->
                    pendingImportData = importData
                }.onFailure { error ->
                    Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        LaunchedEffect(playbackState.errorText) {
            playbackState.errorText?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }

        fun selectMainPage(page: MainPage) {
            selectedPage = page
            lastMainPage = page
            scope.launch { repository.saveLastMainPage(page.name) }
        }

        LaunchedEffect(selectedPage) {
            val tabIndex = MainPage.tabPages.indexOf(selectedPage)
            if (tabIndex >= 0 && pagerState.currentPage != tabIndex) {
                pagerState.animateScrollToPage(tabIndex)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
                val page = MainPage.tabPages.getOrNull(pageIndex) ?: return@collect
                if (selectedPage in MainPage.tabPages && selectedPage != page) {
                    selectMainPage(page)
                }
            }
        }

        fun exportStations() {
            pendingExportJson = StationExportCodec.encode(
                StationExportData(
                    stations = stations,
                    sortState = sortState,
                    customStationOrder = customStationOrder,
                    customFavoriteOrder = customFavoriteOrder,
                ),
            )
            val exportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
            exportLauncher.launch("omnibeat_stations_$exportDate.json")
        }

        fun importStations(mode: StationImportMode) {
            val importData = pendingImportData ?: return
            val importResult = StationExportCodec.buildImportResult(
                importedData = importData,
                currentStations = stations,
                currentSortState = sortState,
                currentCustomStationOrder = customStationOrder,
                currentCustomFavoriteOrder = customFavoriteOrder,
                mode = mode,
            )
            pendingImportData = null
            stations = importResult.stations
            sortState = importResult.sortState
            customStationOrder = importResult.customStationOrder
            customFavoriteOrder = importResult.customFavoriteOrder
            reorderDraft = null
            if (
                playbackState.selectedStation != null &&
                importResult.stations.none { it.id == playbackState.selectedStation?.id }
            ) {
                PlaybackService.stop(context)
            }
            scope.launch {
                repository.saveImportedLibrary(importResult)
                Toast.makeText(context, "Stations imported", Toast.LENGTH_SHORT).show()
            }
        }

        fun customSortedStations(source: List<Station>, customOrder: List<String>): List<Station> {
            if (customOrder.isEmpty()) {
                return source
            }
            val sourceById = source.associateBy { it.id }
            val orderedStations = customOrder.mapNotNull(sourceById::get)
            val orderedIds = orderedStations.map { it.id }.toSet()
            return orderedStations + source.filterNot { it.id in orderedIds }
        }

        fun sortedStations(source: List<Station>, page: MainPage): List<Station> {
            return when (sortState.mode) {
                StationSortMode.Custom -> customSortedStations(
                    source = source,
                    customOrder = if (page == MainPage.Favorites) customFavoriteOrder else customStationOrder,
                )
                StationSortMode.DateAdded -> if (sortState.ascending) {
                    source.sortedBy { it.dateAdded }
                } else {
                    source.sortedByDescending { it.dateAdded }
                }
                StationSortMode.StationTitle -> {
                    val sorted = source.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                    if (sortState.ascending) sorted else sorted.asReversed()
                }
                StationSortMode.FavoritesFirst -> if (sortState.ascending) {
                    source.sortedWith(
                        compareBy<Station> { it.isFavorite }
                            .thenBy { it.dateAdded },
                    )
                } else {
                    source.sortedWith(
                        compareByDescending<Station> { it.isFavorite }
                            .thenByDescending { it.dateAdded },
                    )
                }
            }
        }

        fun toggleFavorite(station: Station) {
            val index = stations.indexOfFirst { it.id == station.id }
            if (index == -1) return
            val nextStations = stations.toMutableList().also { list ->
                list[index] = list[index].copy(isFavorite = !list[index].isFavorite)
            }
            stations = nextStations
            scope.launch { repository.saveStations(nextStations) }
        }

        fun playStationAt(index: Int) {
            scope.launch { drawerState.close() }
            scrollToStationId = stations.getOrNull(index)?.id
            scrollToSelectedRequest += 1
            PlaybackService.playStation(context, index)
        }

        fun playStation(station: Station) {
            val index = stations.indexOfFirst { it.id == station.id }
            if (index != -1) {
                playStationAt(index)
            }
        }

        fun navigationStations(): List<Station> {
            val navigationPage = selectedPage.takeIf { it in MainPage.tabPages } ?: lastMainPage
            val pageStations = if (navigationPage == MainPage.Favorites) {
                stations.filter { it.isFavorite }
            } else {
                stations
            }
            return sortedStations(pageStations, navigationPage)
        }

        fun selectSortMode(nextSortMode: StationSortMode) {
            if (nextSortMode == StationSortMode.Custom) {
                val reorderPage = selectedPage.takeIf { it in MainPage.tabPages } ?: lastMainPage
                val pageStations = if (reorderPage == MainPage.Favorites) {
                    stations.filter { it.isFavorite }
                } else {
                    stations
                }
                val customOrder = if (reorderPage == MainPage.Favorites) {
                    customFavoriteOrder
                } else {
                    customStationOrder
                }
                val draftStations = if (customOrder.isEmpty()) {
                    navigationStations()
                } else {
                    customSortedStations(pageStations, customOrder)
                }
                val nextSortState = StationSortState(mode = StationSortMode.Custom, ascending = false)
                sortState = nextSortState
                reorderDraft = StationReorderDraft(page = reorderPage, stations = draftStations)
                scope.launch { repository.saveStationSortState(nextSortState) }
                return
            }
            val nextSortState = if (sortState.mode == nextSortMode) {
                sortState.copy(ascending = !sortState.ascending)
            } else {
                StationSortState(mode = nextSortMode, ascending = false)
            }
            sortState = nextSortState
            scope.launch { repository.saveStationSortState(nextSortState) }
        }

        fun moveReorderDraft(fromIndex: Int, toIndex: Int) {
            val currentDraft = reorderDraft ?: return
            if (
                fromIndex !in currentDraft.stations.indices ||
                toIndex !in currentDraft.stations.indices ||
                fromIndex == toIndex
            ) {
                return
            }
            val nextStations = currentDraft.stations.toMutableList().also { list ->
                val movedStation = list.removeAt(fromIndex)
                list.add(toIndex, movedStation)
            }
            reorderDraft = currentDraft.copy(stations = nextStations)
        }

        fun cancelReorder() {
            reorderDraft = null
        }

        fun confirmReorder() {
            val draft = reorderDraft ?: return
            val nextOrder = draft.stations.map { it.id }
            val nextSortState = StationSortState(mode = StationSortMode.Custom, ascending = false)
            if (draft.page == MainPage.Favorites) {
                customFavoriteOrder = nextOrder
            } else {
                customStationOrder = nextOrder
            }
            sortState = nextSortState
            reorderDraft = null
            scope.launch {
                if (draft.page == MainPage.Favorites) {
                    repository.saveCustomFavoriteOrder(nextOrder)
                } else {
                    repository.saveCustomStationOrder(nextOrder)
                }
                repository.saveStationSortState(nextSortState)
            }
        }

        fun playAdjacentStation(direction: Int) {
            val pageStations = navigationStations()
            if (pageStations.isEmpty()) return
            val currentIndex = pageStations.indexOfFirst { it.id == playbackState.selectedStation?.id }
            val nextIndex = if (currentIndex in pageStations.indices) {
                (currentIndex + direction + pageStations.size) % pageStations.size
            } else if (direction > 0) {
                0
            } else {
                pageStations.lastIndex
            }
            playStation(pageStations[nextIndex])
        }

        fun playRandomStation() {
            val pageStations = navigationStations()
            if (pageStations.isEmpty()) return
            val currentIndex = pageStations.indexOfFirst { it.id == playbackState.selectedStation?.id }
            val nextIndex = if (pageStations.size == 1) {
                0
            } else {
                var randomIndex: Int
                do {
                    randomIndex = Random.nextInt(pageStations.size)
                } while (randomIndex == currentIndex)
                randomIndex
            }
            playStation(pageStations[nextIndex])
        }

        fun playOrStop() {
            val pageStations = navigationStations()
            if (!playbackState.isPlaying && playbackState.selectedStation == null && pageStations.isNotEmpty()) {
                val lastPlayedStation = lastPlayedStationId
                    ?.let { stationId -> stations.firstOrNull { it.id == stationId } }
                playStation(lastPlayedStation ?: pageStations.first())
            } else {
                PlaybackService.playOrStop(context)
            }
        }

        BackHandler(enabled = drawerState.isOpen || reorderDraft != null || selectedPage !in MainPage.tabPages) {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else if (reorderDraft != null) {
                reorderDraft = null
            } else {
                selectedPage = lastMainPage
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                DrawerContent(
                    selectedPage = selectedPage,
                    onStationsClick = {
                        selectedPage = lastMainPage
                        scope.launch { drawerState.close() }
                    },
                    onExportImportClick = {
                        selectedPage = MainPage.ExportImport
                        scope.launch { drawerState.close() }
                    },
                    onSettingsClick = {
                        selectedPage = MainPage.Settings
                        scope.launch { drawerState.close() }
                    },
                    onAboutClick = {
                        selectedPage = MainPage.About
                        scope.launch { drawerState.close() }
                    },
                    onExitClick = {
                        PlaybackService.stop(context)
                        context.findActivity()?.finishAffinity()
                    },
                )
            },
        ) {
            Scaffold(
                containerColor = RadioBackground,
                topBar = {
                    MainTopBar(
                        selectedPage = selectedPage,
                        sortState = sortState,
                        reordering = reorderDraft != null,
                        onPageSelected = { page ->
                            if (reorderDraft == null) {
                                selectMainPage(page)
                            }
                        },
                        onSortModeSelected = ::selectSortMode,
                        onCancelReorder = ::cancelReorder,
                        onConfirmReorder = ::confirmReorder,
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
                    if (selectedPage in MainPage.tabPages) {
                        val pageStations = navigationStations()
                        val canStartPlayback = pageStations.isNotEmpty() ||
                            lastPlayedStationId?.let { stationId -> stations.any { it.id == stationId } } == true
                        PlayerPanel(
                            station = playbackState.selectedStation,
                            trackText = playbackState.trackText,
                            errorText = playbackState.errorText,
                            streamInfo = playbackState.streamInfo,
                            loading = playbackState.resolving || playbackState.buffering,
                            resolving = playbackState.resolving,
                            isPlaying = playbackState.isPlaying,
                            canNavigateStations = pageStations.isNotEmpty(),
                            appVolume = appVolume,
                            canPlay = canStartPlayback,
                            onPlayStop = { playOrStop() },
                            onPreviousStation = { playAdjacentStation(-1) },
                            onNextStation = { playAdjacentStation(1) },
                            onRandomStation = { playRandomStation() },
                            onVolumeChange = { volume ->
                                appVolume = volume
                                scope.launch { repository.saveAppVolume(volume) }
                            },
                        )
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (selectedPage) {
                        MainPage.Stations,
                        MainPage.Favorites -> {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = reorderDraft == null,
                                modifier = Modifier.fillMaxSize(),
                            ) { pageIndex ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pagerFade(pagerState, pageIndex),
                                ) {
                                    when (MainPage.tabPages[pageIndex]) {
                                        MainPage.Stations -> {
                                            val visibleStations = reorderDraft?.stations ?: sortedStations(stations, MainPage.Stations)
                                            if (visibleStations.isEmpty()) {
                                                EmptyStationsState(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 32.dp),
                                                )
                                            } else {
                                                StationList(
                                                    stations = visibleStations,
                                                    selectedIndex = visibleStations.indexOfFirst { it.id == playbackState.selectedStation?.id },
                                                    scrollToSelectedRequest = scrollToSelectedRequest,
                                                    scrollToStationId = scrollToStationId,
                                                    enabled = drawerState.isClosed && reorderDraft == null,
                                                    reordering = reorderDraft != null,
                                                    onMove = ::moveReorderDraft,
                                                    onFavoriteClick = { _, station -> toggleFavorite(station) },
                                                    onStationEdit = { _, station ->
                                                        val index = stations.indexOfFirst { it.id == station.id }
                                                        if (index != -1) {
                                                            editorState = StationEditorState(
                                                                stationIndex = index,
                                                                title = station.title,
                                                                streamUrl = station.streamUrl,
                                                                tags = station.tags.joinToString(", "),
                                                            )
                                                        }
                                                    },
                                                    onStationClick = { _, station -> playStation(station) },
                                                )
                                            }
                                        }

                                        MainPage.Favorites -> {
                                            val favoriteStations = reorderDraft?.stations
                                                ?: sortedStations(stations.filter { it.isFavorite }, MainPage.Favorites)
                                            if (favoriteStations.isEmpty()) {
                                                EmptyFavoritesState(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 32.dp),
                                                )
                                            } else {
                                                StationList(
                                                    stations = favoriteStations,
                                                    selectedIndex = favoriteStations.indexOfFirst { it.id == playbackState.selectedStation?.id },
                                                    scrollToSelectedRequest = scrollToSelectedRequest,
                                                    scrollToStationId = scrollToStationId,
                                                    enabled = drawerState.isClosed && reorderDraft == null,
                                                    reordering = reorderDraft != null,
                                                    onMove = ::moveReorderDraft,
                                                    onFavoriteClick = { _, station -> toggleFavorite(station) },
                                                    onStationEdit = { _, station ->
                                                        val index = stations.indexOfFirst { it.id == station.id }
                                                        if (index != -1) {
                                                            editorState = StationEditorState(
                                                                stationIndex = index,
                                                                title = station.title,
                                                                streamUrl = station.streamUrl,
                                                                tags = station.tags.joinToString(", "),
                                                            )
                                                        }
                                                    },
                                                    onStationClick = { _, station ->
                                                        val index = stations.indexOfFirst { it.id == station.id }
                                                        if (index != -1) {
                                                            playStationAt(index)
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                        else -> Unit
                                    }
                                }
                            }
                        }

                        MainPage.ExportImport -> {
                            ExportImportPage(
                                stationCount = stations.size,
                                favoriteCount = stations.count { it.isFavorite },
                                onExportStations = { exportStations() },
                                onImportStations = {
                                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        MainPage.Settings -> {
                            EmptyFuturePage(
                                title = "Settings",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp),
                            )
                        }

                        MainPage.About -> {
                            AboutPage(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        pendingImportData?.let { importData ->
            AlertDialog(
                onDismissRequest = { pendingImportData = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 560.dp),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                containerColor = RadioSurface,
                title = {
                    Text(
                        text = "Import stations",
                        color = RadioText,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                text = {
                    Text(
                        text = "Import ${importData.stations.size} stations?\nMerge keeps your current library and updates matching stream URLs.\nReplace clears the current library first.",
                        color = RadioTextMuted,
                        lineHeight = 20.sp,
                    )
                },
                confirmButton = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = { pendingImportData = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { importStations(StationImportMode.Replace) }) {
                            Text("Replace")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { importStations(StationImportMode.Merge) }) {
                            Text("Merge")
                        }
                    }
                },
                dismissButton = {},
            )
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
                        isFavorite = state.stationIndex?.let { stations[it].isFavorite } ?: false,
                        dateAdded = state.stationIndex?.let { stations[it].dateAdded } ?: Instant.now().toString(),
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

@Suppress("FrequentlyChangedStateReadInComposition")
private fun Modifier.pagerFade(
    pagerState: PagerState,
    pageIndex: Int,
): Modifier = graphicsLayer {
    val pageOffset = (
        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
    ).absoluteValue.coerceIn(0f, 1f)
    val minPageAlpha = 0.35f
    val fadeProgress = (pageOffset * 2.2f).coerceIn(0f, 1f)
    alpha = 1f - fadeProgress * (1f - minPageAlpha)
}

enum class MainPage(val title: String) {
    Stations("Stations"),
    Favorites("Favorites"),
    ExportImport("Export / Import"),
    Settings("Settings"),
    About("About");

    companion object {
        val tabPages = listOf(Stations, Favorites)
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun parseTags(tags: String): List<String> {
    return tags.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
}

@Composable
private fun MainTopBar(
    selectedPage: MainPage,
    sortState: StationSortState,
    reordering: Boolean,
    onPageSelected: (MainPage) -> Unit,
    onSortModeSelected: (StationSortMode) -> Unit,
    onCancelReorder: () -> Unit,
    onConfirmReorder: () -> Unit,
    onOpenDrawer: () -> Unit,
    onAddStation: () -> Unit,
) {
    val density = LocalDensity.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

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
            if (selectedPage in MainPage.tabPages) {
                MainPage.tabPages.forEach { tab ->
                var tabTextWidth by remember(tab) { mutableStateOf(0.dp) }
                Column(
                    modifier = Modifier
                        .selectable(
                            selected = selectedPage == tab,
                            enabled = !reordering,
                            role = Role.Tab,
                            onClick = { onPageSelected(tab) },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = tab.title,
                        color = if (selectedPage == tab) RadioText else RadioTextMuted,
                        fontSize = 18.sp,
                        fontWeight = if (selectedPage == tab) FontWeight.SemiBold else FontWeight.Medium,
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
                            .background(if (selectedPage == tab) RadioPrimary else RadioOutline),
                    )
                }
                }
            } else {
                Text(
                    text = selectedPage.title,
                    color = RadioText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        if (selectedPage in MainPage.tabPages) {
            if (reordering) {
                OmniTopBarIconButton(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Cancel reorder",
                    onClick = onCancelReorder,
                    tint = Color(0xFFFF5C6C),
                )
                OmniTopBarIconButton(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Save reorder",
                    onClick = onConfirmReorder,
                    tint = Color(0xFF66D17A),
                )
            } else {
                Box {
                OmniTopBarIconButton(
                    painter = painterResource(R.drawable.ic_filter_list),
                    contentDescription = "Sort stations",
                    onClick = { sortMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = RadioSurface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                ) {
                    StationSortMode.entries.forEach { option ->
                        val selected = sortState.mode == option
                        OmniDropdownMenuItem(
                            text = option.label,
                            onClick = {
                                onSortModeSelected(option)
                                sortMenuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (selected) {
                                            R.drawable.ic_radio_button_checked
                                        } else {
                                            R.drawable.ic_radio_button_unchecked
                                        },
                                    ),
                                    contentDescription = null,
                                    tint = if (selected) RadioPrimary else RadioTextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = if (selected && option != StationSortMode.Custom) {
                                {
                                    Icon(
                                        painter = painterResource(
                                            if (sortState.ascending) {
                                                R.drawable.ic_keyboard_arrow_up
                                            } else {
                                                R.drawable.ic_keyboard_arrow_down
                                            },
                                        ),
                                        contentDescription = if (sortState.ascending) {
                                            "Ascending"
                                        } else {
                                            "Descending"
                                        },
                                        tint = RadioText,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
                OmniTopBarIconButton(
                    painter = painterResource(R.drawable.ic_add_circle_outline),
                    contentDescription = "Add station",
                    onClick = onAddStation,
                )
            }
        }
    }
}
