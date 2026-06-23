package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.data.parseStationTags
import omnibeat.app.data.StationExportData
import omnibeat.app.model.STATION_STREAM_URL_MAX_LENGTH
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH
import omnibeat.app.radio.RadioBrowserClient
import omnibeat.app.radio.RadioBrowserFilterOption
import omnibeat.app.radio.RadioBrowserSearchParams

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import omnibeat.app.data.DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES
import omnibeat.app.data.SimpleStationTextCodec
import omnibeat.app.data.StationExportCodec
import omnibeat.app.data.StationImportMode
import omnibeat.app.data.StationRepository
import omnibeat.app.data.removeTrackingParameters
import omnibeat.app.model.MainPage
import omnibeat.app.model.Station
import omnibeat.app.model.StationEditorState
import omnibeat.app.model.StationReorderDraft
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import omnibeat.app.model.ThemeMode
import omnibeat.app.model.customSortedStations
import omnibeat.app.model.sortedStations
import omnibeat.app.network.NetworkStatus
import omnibeat.app.playback.PlaybackService
import omnibeat.app.radio.RadioBrowserStation
import omnibeat.app.radio.stationTags
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniBeatApp() {
    val context = LocalContext.current
    val repository = remember(context) { StationRepository(context.applicationContext) }
    val themeMode by repository.themeMode.collectAsState(initial = ThemeMode.System)
    var appLanguage by remember(context) { mutableStateOf(context.applicationContext.currentAppLanguage()) }
    val useDarkTheme = shouldUseDarkTheme(themeMode)

    OmniBeatTheme(themeMode = themeMode) {
        val resources = LocalResources.current
        val statusBarColor = RadioBackground
        val navigationBarColor = RadioBackground
        SideEffect {
            val activity = context.findActivity() as? ComponentActivity ?: return@SideEffect
            val statusBarArgb = statusBarColor.toArgb()
            val navigationBarArgb = navigationBarColor.toArgb()
            activity.enableEdgeToEdge(
                statusBarStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(statusBarArgb)
                } else {
                    SystemBarStyle.light(statusBarArgb, statusBarArgb)
                },
                navigationBarStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(navigationBarArgb)
                } else {
                    SystemBarStyle.light(navigationBarArgb, navigationBarArgb)
                },
            )
        }

        val scope = rememberCoroutineScope()
        val onboardingCompleted by repository.onboardingCompleted.collectAsState(initial = null)

        if (onboardingCompleted == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RadioBackground),
            )
            return@OmniBeatTheme
        }

        if (onboardingCompleted == false) {
            OnboardingFlow(
                onFinished = {
                    scope.launch { repository.saveOnboardingCompleted(true) }
                },
                themeMode = themeMode,
                onThemeModeChange = { nextThemeMode ->
                    scope.launch { repository.saveThemeMode(nextThemeMode) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            return@OmniBeatTheme
        }

        val radioBrowserClient = remember { RadioBrowserClient() }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val playbackState by PlaybackService.state.collectAsState()

        var stations by remember { mutableStateOf(emptyList<Station>()) }
        var appVolume by remember { mutableFloatStateOf(0.75f) }
        var showStationArtwork by remember { mutableStateOf(true) }
        var addRadioBrowserTags by remember { mutableStateOf(true) }
        var removeTrackingParametersFromUrls by remember { mutableStateOf(true) }
        var rememberLastStation by remember { mutableStateOf(true) }
        var showBitrateInControlPanel by remember { mutableStateOf(true) }
        var showUnavailableBitrate by remember { mutableStateOf(false) }
        var marqueeTrackTitle by remember { mutableStateOf(true) }
        var stopServiceAfterPauseMinutes by remember { mutableIntStateOf(DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES) }
        var playerPanelCollapsed by remember { mutableStateOf(false) }
        var autoExpandPlayerPanelOnPlayback by remember { mutableStateOf(true) }
        var collapsePlayerPanelInSearch by remember { mutableStateOf(true) }
        var showEmptyFavoritesTab by remember { mutableStateOf(true) }
        var confirmStationDeletion by remember { mutableStateOf(true) }
        var syncingStationArtwork by remember { mutableStateOf(false) }
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
        var pendingExportContent by remember { mutableStateOf<String?>(null) }
        var pendingImportData by remember { mutableStateOf<StationExportData?>(null) }
        var onlineSearchState by remember { mutableStateOf(OnlineStationSearchState()) }
        var onlineSearchResults by remember { mutableStateOf(emptyList<RadioBrowserStation>()) }
        var onlineSearchLoading by remember { mutableStateOf(false) }
        var onlineSearchLoadingMore by remember { mutableStateOf(false) }
        var onlineSearchHasMore by remember { mutableStateOf(false) }
        var onlineSearchLastQuery by remember { mutableStateOf<OnlineStationSearchState?>(null) }
        var errorDialog by remember { mutableStateOf<String?>(null) }
        var onlineCountries by remember { mutableStateOf(emptyList<RadioBrowserFilterOption>()) }
        var onlineLanguages by remember { mutableStateOf(emptyList<RadioBrowserFilterOption>()) }
        var onlineOptionsExpanded by remember { mutableStateOf(false) }
        val visibleTabPages = if (showEmptyFavoritesTab || stations.any { it.isFavorite }) {
            MainPage.tabPages
        } else {
            listOf(MainPage.Stations)
        }
        val pagerState = rememberPagerState(pageCount = { visibleTabPages.size })
        val visualSelectedPage by remember(pagerState, visibleTabPages, selectedPage) {
            derivedStateOf {
                if (selectedPage in visibleTabPages) {
                    visibleTabPages.getOrNull(pagerState.currentPage) ?: selectedPage
                } else {
                    selectedPage
                }
            }
        }

        RepositoryStateEffects(
            repository = repository,
            selectedPage = selectedPage,
            visibleTabPages = visibleTabPages,
            onStationsChange = { stations = it },
            onAppVolumeChange = { appVolume = it },
            onShowStationArtworkChange = { showStationArtwork = it },
            onAddRadioBrowserTagsChange = { addRadioBrowserTags = it },
            onRemoveTrackingParametersChange = { removeTrackingParametersFromUrls = it },
            onRememberLastStationChange = { rememberLastStation = it },
            onShowBitrateInControlPanelChange = { showBitrateInControlPanel = it },
            onShowUnavailableBitrateChange = { showUnavailableBitrate = it },
            onMarqueeTrackTitleChange = { marqueeTrackTitle = it },
            onStopServiceAfterPauseMinutesChange = { stopServiceAfterPauseMinutes = it },
            onPlayerPanelCollapsedChange = { playerPanelCollapsed = it },
            onAutoExpandPlayerPanelOnPlaybackChange = { autoExpandPlayerPanelOnPlayback = it },
            onCollapsePlayerPanelInSearchChange = { collapsePlayerPanelInSearch = it },
            onShowEmptyFavoritesTabChange = { showEmptyFavoritesTab = it },
            onConfirmStationDeletionChange = { confirmStationDeletion = it },
            onLastMainPageChange = { lastMainPage = it },
            onSelectedPageRestore = { selectedPage = it },
            onStationSortStateChange = { sortState = it },
            onCustomStationOrderChange = { customStationOrder = it },
            onCustomFavoriteOrderChange = { customFavoriteOrder = it },
            onLastPlayedStationIdChange = { lastPlayedStationId = it },
        )

        fun writePendingExport(uri: android.net.Uri?, successMessage: String) {
            val exportContent = pendingExportContent ?: return
            pendingExportContent = null
            if (uri == null) {
                return
            }
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(exportContent.toByteArray(Charsets.UTF_8))
                        } ?: error("Could not open export file")
                    }
                }.onSuccess {
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_export_failed, error.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        val jsonExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            writePendingExport(uri, resources.getString(R.string.toast_export_json))
        }

        val textExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            writePendingExport(uri, resources.getString(R.string.toast_export_txt))
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                runCatching {
                    val importText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.readBytes().toString(Charsets.UTF_8)
                        } ?: error("Could not open import file")
                    }
                    if (importText.trimStart().startsWith("{")) {
                        StationExportCodec.decode(importText)
                    } else {
                        SimpleStationTextCodec.decode(
                            text = importText,
                            cleanTrackingParameters = removeTrackingParametersFromUrls,
                        )
                    }
                }.onSuccess { importData ->
                    pendingImportData = importData
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_import_failed, error.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        LaunchedEffect(playbackState.errorText) {
            playbackState.errorText?.let { errorText ->
                errorDialog = errorText
                Toast.makeText(context, resources.getString(R.string.toast_playback_error), Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(selectedPage) {
            if (selectedPage == MainPage.SearchOnline) {
                onlineOptionsExpanded = true
                if (onlineCountries.isEmpty()) {
                    scope.launch {
                        runCatching { radioBrowserClient.countries() }
                            .onSuccess { onlineCountries = it }
                    }
                }
                if (onlineLanguages.isEmpty()) {
                    scope.launch {
                        runCatching { radioBrowserClient.languages() }
                            .onSuccess { onlineLanguages = it }
                    }
                }
            } else {
                onlineOptionsExpanded = false
            }
        }

        fun selectMainPage(page: MainPage) {
            selectedPage = page
            lastMainPage = page
            scope.launch { repository.saveLastMainPage(page.name) }
        }

        fun activeNavigationPage(): MainPage {
            return selectedPage.takeIf { it in visibleTabPages }
                ?: lastMainPage.takeIf { it in visibleTabPages }
                ?: MainPage.Stations
        }

        LaunchedEffect(visibleTabPages, selectedPage) {
            if (selectedPage == MainPage.Favorites && MainPage.Favorites !in visibleTabPages) {
                selectMainPage(MainPage.Stations)
            }
        }

        LaunchedEffect(selectedPage, collapsePlayerPanelInSearch) {
            if (selectedPage == MainPage.SearchOnline && collapsePlayerPanelInSearch && !playerPanelCollapsed) {
                playerPanelCollapsed = true
                repository.savePlayerPanelCollapsed(true)
            }
        }

        SyncPagerWithSelectedPage(
            pagerState = pagerState,
            pages = visibleTabPages,
            selectedPage = selectedPage,
        ) { page ->
            if (selectedPage in visibleTabPages && selectedPage != page) {
                selectMainPage(page)
            }
        }

        fun exportStations() {
            pendingExportContent = StationExportCodec.encode(
                StationExportData(
                    stations = stations,
                    sortState = sortState,
                    customStationOrder = customStationOrder,
                    customFavoriteOrder = customFavoriteOrder,
                ),
            )
            val exportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
            jsonExportLauncher.launch("omnibeat_stations_$exportDate.json")
        }

        fun exportSimpleText() {
            if (stations.isEmpty()) {
                pendingExportContent = resources
                    .openRawResource(R.raw.omnibeat_export_example)
                    .bufferedReader()
                    .use { it.readText() }
                textExportLauncher.launch("omnibeat_export_example.txt")
            } else {
                pendingExportContent = SimpleStationTextCodec.encode(stations)
                val exportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
                textExportLauncher.launch("omnibeat_stations_$exportDate.txt")
            }
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
                Toast.makeText(context, resources.getString(R.string.toast_stations_imported), Toast.LENGTH_SHORT).show()
            }
        }

        fun clearLibrary() {
            PlaybackService.stop(context)
            stations = emptyList()
            customStationOrder = emptyList()
            customFavoriteOrder = emptyList()
            reorderDraft = null
            lastPlayedStationId = null
            if (selectedPage == MainPage.Favorites) {
                selectMainPage(MainPage.Stations)
            }
            scope.launch {
                repository.clearLibrary()
                Toast.makeText(context, resources.getString(R.string.toast_library_deleted), Toast.LENGTH_SHORT).show()
            }
        }

        fun hasInternetOrToast(): Boolean {
            if (NetworkStatus.isOnline(context)) {
                return true
            }
            Toast.makeText(context, resources.getString(R.string.toast_no_internet), Toast.LENGTH_SHORT).show()
            return false
        }

        fun searchOnlineStations() {
            if (onlineSearchLoading || onlineSearchLoadingMore) return
            if (!hasInternetOrToast()) return
            onlineSearchLoading = true
            scope.launch {
                runCatching {
                    radioBrowserClient.searchStations(onlineSearchState.toRadioBrowserParams(offset = 0))
                }.onSuccess { results ->
                    onlineSearchResults = results
                    onlineSearchLastQuery = onlineSearchState
                    onlineSearchHasMore = results.size == RadioBrowserSearchParams.DEFAULT_LIMIT
                    onlineOptionsExpanded = false
                }.onFailure { error ->
                    onlineSearchResults = emptyList()
                    onlineSearchLastQuery = null
                    onlineSearchHasMore = false
                    val message = error.message ?: resources.getString(R.string.toast_search_default_error)
                    errorDialog = message
                    Toast.makeText(context, resources.getString(R.string.toast_search_failed), Toast.LENGTH_SHORT).show()
                }
                onlineSearchLoading = false
            }
        }

        fun stationFromOnlineResult(radioStation: RadioBrowserStation): Station {
            val streamUrl = radioStation.streamUrl
                .trim()
                .let { if (removeTrackingParametersFromUrls) removeTrackingParameters(it) else it }
            return Station(
                id = radioStation.stationUuid.takeIf { it.isNotBlank() } ?: streamUrl,
                title = radioStation.title.trim().take(STATION_TITLE_MAX_LENGTH)
                    .ifBlank { streamUrl.take(STATION_TITLE_MAX_LENGTH) },
                streamUrl = streamUrl.take(STATION_STREAM_URL_MAX_LENGTH),
                tags = if (addRadioBrowserTags) radioStation.stationTags() else emptyList(),
                imageUrl = radioStation.imageUrl,
                isFavorite = false,
                dateAdded = Instant.now().toString(),
            )
        }

        fun expandPlayerPanelForUserPlaybackRequest() {
            if (!autoExpandPlayerPanelOnPlayback || !playerPanelCollapsed) return
            playerPanelCollapsed = false
            scope.launch { repository.savePlayerPanelCollapsed(false) }
        }

        fun previewOnlineStation(radioStation: RadioBrowserStation) {
            if (radioStation.streamUrl.isBlank()) return
            if (!hasInternetOrToast()) return
            expandPlayerPanelForUserPlaybackRequest()
            PlaybackService.playPreview(context, stationFromOnlineResult(radioStation))
        }

        fun addOnlineStation(radioStation: RadioBrowserStation) {
            val station = stationFromOnlineResult(radioStation)
            if (station.streamUrl.isBlank() || stations.any { it.streamUrl == station.streamUrl }) return
            val nextStations = stations + station.copy(id = UUID.randomUUID().toString())
            stations = nextStations
            scope.launch {
                repository.saveStations(nextStations)
                Toast.makeText(context, resources.getString(R.string.toast_station_added), Toast.LENGTH_SHORT).show()
            }
        }

        fun syncStationArtwork() {
            if (syncingStationArtwork) return
            if (!hasInternetOrToast()) return
            syncingStationArtwork = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        stations.mapNotNull { station ->
                            if (!station.imageUrl.isNullOrBlank()) return@mapNotNull null
                            val imageUrl = radioBrowserClient.findStationByStreamUrl(station.streamUrl)
                                ?.imageUrl
                                ?.takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null
                            station.id to imageUrl
                        }
                    }
                }.onSuccess { updates ->
                    if (updates.isNotEmpty()) {
                        val imageByStationId = updates.toMap()
                        val nextStations = stations.map { station ->
                            imageByStationId[station.id]?.let { imageUrl ->
                                station.copy(imageUrl = imageUrl)
                            } ?: station
                        }
                        stations = nextStations
                        repository.saveStations(nextStations)
                    }
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_artwork_synced_count, updates.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_artwork_sync_failed, error.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                syncingStationArtwork = false
            }
        }

        fun syncNewStationArtwork(station: Station) {
            if (!showStationArtwork || !station.imageUrl.isNullOrBlank()) return
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        radioBrowserClient.findStationByStreamUrl(station.streamUrl)
                            ?.imageUrl
                            ?.takeIf { it.isNotBlank() }
                    }
                }.onSuccess { imageUrl ->
                    if (imageUrl == null) return@onSuccess
                    val nextStations = stations.map { savedStation ->
                        if (savedStation.id == station.id && savedStation.imageUrl.isNullOrBlank()) {
                            savedStation.copy(imageUrl = imageUrl)
                        } else {
                            savedStation
                        }
                    }
                    stations = nextStations
                    repository.saveStations(nextStations)
                }
            }
        }

        fun syncStationArtwork(index: Int) {
            val station = stations.getOrNull(index) ?: return
            if (!hasInternetOrToast()) return
            Toast.makeText(context, resources.getString(R.string.toast_artwork_searching), Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        radioBrowserClient.findStationByStreamUrl(station.streamUrl)
                            ?.imageUrl
                            ?.takeIf { it.isNotBlank() }
                    }
                }.onSuccess { imageUrl ->
                    if (imageUrl == null) {
                        Toast.makeText(context, resources.getString(R.string.toast_artwork_not_found), Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    val nextStations = stations.toMutableList().also { list ->
                        val currentIndex = list.indexOfFirst { it.id == station.id }
                        if (currentIndex != -1) {
                            list[currentIndex] = list[currentIndex].copy(imageUrl = imageUrl)
                        }
                    }
                    stations = nextStations
                    repository.saveStations(nextStations)
                    Toast.makeText(context, resources.getString(R.string.toast_artwork_synced), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_artwork_sync_failed, error.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
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

        fun navigationStations(): List<Station> {
            if (selectedPage == MainPage.SearchOnline) {
                return onlineSearchResults.map(::stationFromOnlineResult)
            }
            val navigationPage = activeNavigationPage()
            val pageStations = if (navigationPage == MainPage.Favorites) {
                stations.filter { it.isFavorite }
            } else {
                stations
            }
            return sortedStations(
                source = pageStations,
                page = navigationPage,
                sortState = sortState,
                customStationOrder = customStationOrder,
                customFavoriteOrder = customFavoriteOrder,
            )
        }

        fun playStationAt(index: Int) {
            if (!hasInternetOrToast()) return
            expandPlayerPanelForUserPlaybackRequest()
            scope.launch { drawerState.close() }
            scrollToStationId = stations.getOrNull(index)?.id
            scrollToSelectedRequest += 1
            PlaybackService.playStation(
                context = context,
                index = index,
                queueIds = navigationStations().map { it.id },
            )
        }

        fun playStation(station: Station) {
            val index = stations.indexOfFirst { it.id == station.id }
            if (index != -1) {
                playStationAt(index)
            } else {
                if (!hasInternetOrToast()) return
                expandPlayerPanelForUserPlaybackRequest()
                PlaybackService.playPreview(context, station)
            }
        }

        fun loadMoreOnlineStations() {
            if (onlineSearchLoading || onlineSearchLoadingMore || !onlineSearchHasMore) return
            if (!hasInternetOrToast()) return
            val query = onlineSearchLastQuery ?: onlineSearchState
            onlineSearchLoadingMore = true
            scope.launch {
                runCatching {
                    radioBrowserClient.searchStations(
                        query.toRadioBrowserParams(offset = onlineSearchResults.size),
                    )
                }.onSuccess { results ->
                    if (results.isEmpty()) {
                        onlineSearchHasMore = false
                    } else {
                        val existingKeys = onlineSearchResults
                            .map { it.stationUuid.ifBlank { it.streamUrl } }
                            .toSet()
                        val newResults = results.filter { station ->
                            station.stationUuid.ifBlank { station.streamUrl } !in existingKeys
                        }
                        onlineSearchResults = onlineSearchResults + newResults
                        onlineSearchHasMore = results.size == RadioBrowserSearchParams.DEFAULT_LIMIT
                    }
                }.onFailure { error ->
                    val message = error.message ?: resources.getString(R.string.toast_load_more_default_error)
                    errorDialog = message
                    Toast.makeText(context, resources.getString(R.string.toast_search_failed), Toast.LENGTH_SHORT).show()
                }
                onlineSearchLoadingMore = false
            }
        }

        fun selectSortMode(nextSortMode: StationSortMode) {
            if (nextSortMode == StationSortMode.Custom) {
                val reorderPage = activeNavigationPage()
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

        fun playOrPause() {
            val pageStations = navigationStations()
            val hasActivePlaybackRequest = playbackState.isPlaying || playbackState.resolving || playbackState.buffering
            if (!hasActivePlaybackRequest && playbackState.selectedStation == null && pageStations.isNotEmpty()) {
                val rememberedStation = if (rememberLastStation && selectedPage != MainPage.SearchOnline) {
                    lastPlayedStationId
                        ?.let { stationId -> stations.firstOrNull { it.id == stationId } }
                } else {
                    null
                }
                playStation(rememberedStation ?: pageStations.first())
            } else {
                if (!hasActivePlaybackRequest && !hasInternetOrToast()) return
                PlaybackService.playOrPause(context)
            }
        }

        BackHandler(enabled = drawerState.isOpen || reorderDraft != null || selectedPage !in visibleTabPages) {
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
                        visualSelectedPage = visualSelectedPage,
                        tabPages = visibleTabPages,
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
                        onNavigateBack = { selectedPage = lastMainPage },
                        onAddStation = {
                            editorState = StationEditorState(
                                stationIndex = null,
                                title = "",
                                streamUrl = "",
                                tags = "",
                                dateAdded = null,
                            )
                        },
                        onSearchOnline = {
                            selectedPage = MainPage.SearchOnline
                        },
                        onlineSearchControl = if (selectedPage == MainPage.SearchOnline) {
                            { controlModifier ->
                                SearchOptionsTopBarControl(
                                    expanded = onlineOptionsExpanded,
                                    loading = onlineSearchLoading,
                                    onExpandedChange = { onlineOptionsExpanded = it },
                                    modifier = controlModifier,
                                )
                            }
                        } else {
                            null
                        },
                    )
                },
                bottomBar = {
                    if (selectedPage in visibleTabPages || selectedPage == MainPage.SearchOnline) {
                        PlayerPanel(
                            station = playbackState.selectedStation,
                            trackText = playbackState.trackText,
                            trackStatus = playbackState.trackStatus,
                            errorText = playbackState.errorText,
                            streamInfo = playbackState.streamInfo,
                            loading = playbackState.resolving || playbackState.buffering,
                            isPlaying = playbackState.isPlaying,
                            appVolume = appVolume,
                            showBitrate = showBitrateInControlPanel,
                            showUnavailableBitrate = showUnavailableBitrate,
                            marqueeTrackTitle = marqueeTrackTitle,
                            collapsed = playerPanelCollapsed,
                            onCollapsedChange = { collapsed ->
                                playerPanelCollapsed = collapsed
                                scope.launch { repository.savePlayerPanelCollapsed(collapsed) }
                            },
                            onPlayPause = { playOrPause() },
                            onStop = { PlaybackService.stop(context) },
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
                            StationTabsPager(
                                visibleTabPages = visibleTabPages,
                                pagerState = pagerState,
                                stations = stations,
                                selectedStationId = playbackState.selectedStation?.id,
                                playingStationId = playbackState.selectedStation?.id.takeIf { playbackState.isPlaying },
                                sortState = sortState,
                                customStationOrder = customStationOrder,
                                customFavoriteOrder = customFavoriteOrder,
                                reorderDraft = reorderDraft,
                                scrollToSelectedRequest = scrollToSelectedRequest,
                                scrollToStationId = scrollToStationId,
                                showArtwork = showStationArtwork,
                                listEnabled = drawerState.isClosed && reorderDraft == null,
                                onMove = ::moveReorderDraft,
                                onFavoriteClick = ::toggleFavorite,
                                onEditStation = { editorState = it },
                                onStationClick = ::playStation,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        MainPage.ExportImport -> {
                            ExportImportPage(
                                stationCount = stations.size,
                                favoriteCount = stations.count { it.isFavorite },
                                onExportStations = { exportStations() },
                                onExportSimpleText = { exportSimpleText() },
                                onImportStations = {
                                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                },
                                onClearLibrary = { clearLibrary() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        MainPage.SearchOnline -> {
                            OnlineStationSearchPage(
                                searchState = onlineSearchState,
                                countries = onlineCountries,
                                languages = onlineLanguages,
                                results = onlineSearchResults,
                                loading = onlineSearchLoading,
                                loadingMore = onlineSearchLoadingMore,
                                hasMoreResults = onlineSearchHasMore,
                                optionsExpanded = onlineOptionsExpanded,
                                showArtwork = showStationArtwork,
                                addedStreamUrls = stations.mapTo(mutableSetOf()) { it.streamUrl },
                                selectedStreamUrl = playbackState.selectedStation?.streamUrl,
                                onSearchStateChange = {
                                    onlineSearchState = it
                                },
                                onSearch = { searchOnlineStations() },
                                onLoadMore = { loadMoreOnlineStations() },
                                onPreviewStation = { previewOnlineStation(it) },
                                onAddStation = { addOnlineStation(it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        MainPage.Settings -> {
                            SettingsPage(
                                themeMode = themeMode,
                                appLanguage = appLanguage,
                                showStationArtwork = showStationArtwork,
                                addRadioBrowserTags = addRadioBrowserTags,
                                removeTrackingParameters = removeTrackingParametersFromUrls,
                                rememberLastStation = rememberLastStation,
                                showBitrateInControlPanel = showBitrateInControlPanel,
                                showUnavailableBitrate = showUnavailableBitrate,
                                marqueeTrackTitle = marqueeTrackTitle,
                                stopServiceAfterPauseMinutes = stopServiceAfterPauseMinutes,
                                autoExpandPlayerPanelOnPlayback = autoExpandPlayerPanelOnPlayback,
                                collapsePlayerPanelInSearch = collapsePlayerPanelInSearch,
                                showEmptyFavoritesTab = showEmptyFavoritesTab,
                                confirmStationDeletion = confirmStationDeletion,
                                syncingStationArtwork = syncingStationArtwork,
                                onShowStationArtworkChange = { show ->
                                    showStationArtwork = show
                                    scope.launch { repository.saveShowStationArtwork(show) }
                                },
                                onAddRadioBrowserTagsChange = { add ->
                                    addRadioBrowserTags = add
                                    scope.launch { repository.saveAddRadioBrowserTags(add) }
                                },
                                onRemoveTrackingParametersChange = { remove ->
                                    removeTrackingParametersFromUrls = remove
                                    scope.launch { repository.saveRemoveTrackingParameters(remove) }
                                },
                                onRememberLastStationChange = { remember ->
                                    rememberLastStation = remember
                                    scope.launch { repository.saveRememberLastStation(remember) }
                                },
                                onShowBitrateInControlPanelChange = { show ->
                                    showBitrateInControlPanel = show
                                    scope.launch { repository.saveShowBitrateInControlPanel(show) }
                                },
                                onShowUnavailableBitrateChange = { show ->
                                    showUnavailableBitrate = show
                                    scope.launch { repository.saveShowUnavailableBitrate(show) }
                                },
                                onMarqueeTrackTitleChange = { marquee ->
                                    marqueeTrackTitle = marquee
                                    scope.launch { repository.saveMarqueeTrackTitle(marquee) }
                                },
                                onStopServiceAfterPauseMinutesChange = { minutes ->
                                    stopServiceAfterPauseMinutes = minutes
                                    scope.launch { repository.saveStopServiceAfterPauseMinutes(minutes) }
                                },
                                onAutoExpandPlayerPanelOnPlaybackChange = { autoExpand ->
                                    autoExpandPlayerPanelOnPlayback = autoExpand
                                    scope.launch { repository.saveAutoExpandPlayerPanelOnPlayback(autoExpand) }
                                },
                                onCollapsePlayerPanelInSearchChange = { collapse ->
                                    collapsePlayerPanelInSearch = collapse
                                    scope.launch { repository.saveCollapsePlayerPanelInSearch(collapse) }
                                },
                                onShowEmptyFavoritesTabChange = { show ->
                                    showEmptyFavoritesTab = show
                                    scope.launch { repository.saveShowEmptyFavoritesTab(show) }
                                },
                                onConfirmStationDeletionChange = { confirm ->
                                    confirmStationDeletion = confirm
                                    scope.launch { repository.saveConfirmStationDeletion(confirm) }
                                },
                                onSyncStationArtwork = { syncStationArtwork() },
                                onClearLibrary = { clearLibrary() },
                                onThemeModeChange = { nextThemeMode ->
                                    scope.launch { repository.saveThemeMode(nextThemeMode) }
                                },
                                onAppLanguageChange = { nextAppLanguage ->
                                    appLanguage = nextAppLanguage
                                    context.applicationContext.applyAppLanguage(nextAppLanguage)
                                },
                                modifier = Modifier.fillMaxSize(),
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
            ImportStationsDialog(
                stationCount = importData.stations.size,
                onImport = ::importStations,
                onDismiss = { pendingImportData = null },
            )
        }

        errorDialog?.let { message ->
            ErrorDialog(
                message = message,
                onDismiss = { errorDialog = null },
            )
        }

        editorState?.let { state ->
            StationEditorDialog(
                state = state,
                showDelete = state.stationIndex != null,
                confirmStationDeletion = confirmStationDeletion,
                onDismiss = { editorState = null },
                onSyncArtwork = state.stationIndex?.let { stationIndex ->
                    { syncStationArtwork(stationIndex) }
                },
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
                    val savedStreamUrl = if (state.stationIndex == null && removeTrackingParametersFromUrls) {
                        removeTrackingParameters(trimmedStreamUrl)
                    } else {
                        trimmedStreamUrl
                    }
                    val updatedStation = Station(
                        id = state.stationIndex?.let { stations[it].id } ?: UUID.randomUUID().toString(),
                        title = title.trim().ifBlank { savedStreamUrl.take(STATION_TITLE_MAX_LENGTH) },
                        streamUrl = savedStreamUrl,
                        tags = parseStationTags(tags),
                        imageUrl = state.stationIndex?.let { stations[it].imageUrl },
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
                    if (state.stationIndex == null) {
                        syncNewStationArtwork(updatedStation)
                    }
                    editorState = null
                },
            )
        }
    }
}

