package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.MainActivity
import omnibeat.app.data.parseStationTags
import omnibeat.app.data.StationExportData
import omnibeat.app.model.STATION_STREAM_URL_MAX_LENGTH
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH
import omnibeat.app.radio.RadioBrowserClient
import omnibeat.app.radio.RadioBrowserFilterOption
import omnibeat.app.radio.RadioBrowserSearchParams

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FabPosition
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import omnibeat.app.data.DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES
import omnibeat.app.data.SimpleStationTextCodec
import omnibeat.app.data.StationExportCodec
import omnibeat.app.data.StationImportMode
import omnibeat.app.data.StationRepository
import omnibeat.app.data.StationArtworkCache
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.model.SleepTimerPlacement
import omnibeat.app.model.SleepTimerFreePosition
import omnibeat.app.data.TranslationLanguage
import omnibeat.app.data.appString
import omnibeat.app.data.removeTrackingParameters
import omnibeat.app.data.normalizeStreamUrl
import omnibeat.app.model.MainPage
import omnibeat.app.model.orderedTabs
import omnibeat.app.model.Playlist
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
import omnibeat.app.widget.PlaybackWidget
import omnibeat.app.radio.RadioBrowserStation
import omnibeat.app.radio.stationTags
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

@Composable
fun OmniBeatApp() {
    val context = LocalContext.current
    val repository = remember(context) { StationRepository(context.applicationContext) }
    val translationEnabled by repository.translationEnabled.collectAsState(initial = false)
    var translationLoaded by remember(repository) { mutableStateOf(false) }
    LaunchedEffect(repository) {
        TranslationLanguage.load(repository)
        translationLoaded = true
    }
    val themeMode by repository.themeMode.collectAsState(initial = ThemeMode.System)
    LaunchedEffect(themeMode) {
        PlaybackWidget.requestUpdate(context)
    }
    val appLanguage = context.applicationContext.currentAppLanguage()
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

        if (onboardingCompleted == null || !translationLoaded) {
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

        val versionCode = remember(context) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            ).longVersionCode
        }
        val whatsNewSeenVersionCode by repository.whatsNewSeenVersionCode.collectAsState(initial = null)
        var showWhatsNew by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(versionCode, whatsNewSeenVersionCode) {
            if (whatsNewSeenVersionCode != null && whatsNewSeenVersionCode != versionCode) {
                showWhatsNew = true
            }
        }

        val updates = (context.applicationContext as? AppUpdateProvider)?.rememberUpdates()
        val radioBrowserClient = remember { RadioBrowserClient(context.applicationContext) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val playbackState by PlaybackService.state.collectAsState()
        val sleepTimerRemainingSeconds by PlaybackService.sleepTimerRemainingSeconds.collectAsState()
        val sleepTimerFreePosition by repository.sleepTimerFreePosition.collectAsState(initial = SleepTimerFreePosition())
        var showSleepTimer by rememberSaveable { mutableStateOf(false) }

        val savedPlaylists by repository.playlists.collectAsState(initial = null)
        val playlists = savedPlaylists.orEmpty()
        val savedTabOrder by repository.tabOrder.collectAsState(initial = emptyList())
        var tabOrderDraft by rememberSaveable { mutableStateOf<List<String>?>(null) }
        var showPlaylistEditor by rememberSaveable { mutableStateOf(false) }
        var editingPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
        var deletingPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
        var membershipMode by rememberSaveable { mutableStateOf<PlaylistMembershipMode?>(null) }
        var playbackQueueIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
        var stations by remember { mutableStateOf(emptyList<Station>()) }
        var appVolume by remember { mutableFloatStateOf(0.75f) }
        var stationDividers by remember { mutableStateOf(StationDividerStyle.Inset) }
        var showPlaybackIndicator by remember { mutableStateOf(true) }
        var showFavoriteButtons by remember { mutableStateOf(true) }
        var showEmptyFavoritesTab by remember { mutableStateOf(true) }
        var confirmStationDeletion by remember { mutableStateOf(true) }
        var showStationArtwork by remember { mutableStateOf(true) }
        var syncingStationArtwork by remember { mutableStateOf(false) }
        var rememberLastStation by remember { mutableStateOf(true) }
        var stopServiceAfterPauseMinutes by remember { mutableIntStateOf(DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES) }
        var keepMediaCard by remember { mutableStateOf(repository.keepMediaCard.value) }
        var showBitrateInControlPanel by remember { mutableStateOf(true) }
        var showUnavailableBitrate by remember { mutableStateOf(false) }
        var marqueeTrackTitle by remember { mutableStateOf(true) }
        var autoExpandPlayerPanelOnPlayback by remember { mutableStateOf(true) }
        var collapsePlayerPanelInSearch by remember { mutableStateOf(true) }
        var addRadioBrowserTags by remember { mutableStateOf(true) }
        val sleepTimerCloseApp by repository.sleepTimerCloseApp.collectAsState(initial = false)
        val sleepTimerPlacement by repository.sleepTimerPlacement.collectAsState(initial = SleepTimerPlacement.PanelRight)
        val sleepTimerAlwaysShow by repository.sleepTimerAlwaysShow.collectAsState(initial = false)
        var removeTrackingParametersFromUrls by remember { mutableStateOf(true) }
        var widgetFollowAppTheme by remember { mutableStateOf(false) }
        var widgetMarqueeTrackTitle by remember { mutableStateOf(true) }
        var showAndroidAutoArtwork by remember { mutableStateOf(true) }
        var playerPanelCollapsed by remember { mutableStateOf(false) }
        var editorState by remember { mutableStateOf<StationEditorState?>(null) }
        var selectedPage by remember { mutableStateOf<MainPage>(MainPage.Stations) }
        var lastMainPage by remember { mutableStateOf<MainPage>(MainPage.Stations) }
        var stationSortState by remember { mutableStateOf(StationSortState()) }
        var favoriteSortState by remember { mutableStateOf(StationSortState()) }
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
        var onlineFilterRequest by remember { mutableStateOf<Deferred<List<String>>?>(null) }
        var onlineFilterErrorsHandledBySearch by remember { mutableStateOf(false) }
        var onlineOptionsExpanded by remember { mutableStateOf(false) }
        val allTabPagesState = remember {
            derivedStateOf { orderedTabs(savedPlaylists.orEmpty(), savedTabOrder) }
        }
        val allTabPages by allTabPagesState
        val visibleTabPagesState = remember {
            derivedStateOf {
                allTabPagesState.value.filter {
                    it != MainPage.Favorites || showEmptyFavoritesTab || stations.any { station -> station.isFavorite }
                }
            }
        }
        val visibleTabPages by visibleTabPagesState
        val currentPlaylist = (selectedPage as? MainPage.Playlist)?.let { tab -> playlists.firstOrNull { it.id == tab.id } }
        val currentSortState = currentPlaylist?.sortState
            ?: if (selectedPage == MainPage.Favorites) favoriteSortState else stationSortState
        val showPlayerPanel = !showSleepTimer && tabOrderDraft == null &&
            (selectedPage in visibleTabPages || selectedPage == MainPage.SearchOnline)
        val showSleepTimerPill = showPlayerPanel && (sleepTimerAlwaysShow || sleepTimerRemainingSeconds > 0L)
        val layoutDirection = LocalLayoutDirection.current
        val pagerState = rememberPagerState(pageCount = { visibleTabPagesState.value.size })
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
            onStationsChange = { stations = it },
            onAppVolumeChange = { appVolume = it },
            onStationDividersChange = { stationDividers = it },
            onShowPlaybackIndicatorChange = { showPlaybackIndicator = it },
            onShowFavoriteButtonsChange = { showFavoriteButtons = it },
            onShowEmptyFavoritesTabChange = { showEmptyFavoritesTab = it },
            onConfirmStationDeletionChange = { confirmStationDeletion = it },
            onShowStationArtworkChange = { showStationArtwork = it },
            onRememberLastStationChange = { rememberLastStation = it },
            onStopServiceAfterPauseMinutesChange = { stopServiceAfterPauseMinutes = it },
            onKeepMediaCardChange = { keepMediaCard = it },
            onShowBitrateInControlPanelChange = { showBitrateInControlPanel = it },
            onShowUnavailableBitrateChange = { showUnavailableBitrate = it },
            onMarqueeTrackTitleChange = { marqueeTrackTitle = it },
            onAutoExpandPlayerPanelOnPlaybackChange = { autoExpandPlayerPanelOnPlayback = it },
            onCollapsePlayerPanelInSearchChange = { collapsePlayerPanelInSearch = it },
            onAddRadioBrowserTagsChange = { addRadioBrowserTags = it },
            onRemoveTrackingParametersChange = { removeTrackingParametersFromUrls = it },
            onWidgetFollowAppThemeChange = { widgetFollowAppTheme = it },
            onWidgetMarqueeTrackTitleChange = { widgetMarqueeTrackTitle = it },
            onShowAndroidAutoArtworkChange = { showAndroidAutoArtwork = it },
            onPlayerPanelCollapsedChange = { playerPanelCollapsed = it },
            onLastMainPageChange = { lastMainPage = it },
            onSelectedPageRestore = { selectedPage = it },
            onStationSortStateChange = { stationSortState = it },
            onFavoriteSortStateChange = { favoriteSortState = it },
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
                        resources.appString(R.string.toast_export_failed, error.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        val jsonExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            writePendingExport(uri, resources.appString(R.string.toast_export_json))
        }

        val textExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            writePendingExport(uri, resources.appString(R.string.toast_export_txt))
        }

        fun importStations(mode: StationImportMode) {
            val importData = pendingImportData ?: return
            pendingImportData = null
            scope.launch {
                try {
                    val importResult = withContext(Dispatchers.IO) { repository.importLibrary(importData, mode) }
                    stations = importResult.stations
                    stationSortState = importResult.stationSortState
                    favoriteSortState = importResult.favoriteSortState
                    customStationOrder = importResult.customStationOrder
                    customFavoriteOrder = importResult.customFavoriteOrder
                    reorderDraft = null
                    playbackQueueIds = null
                    if (lastMainPage is MainPage.Playlist &&
                        importResult.collections.playlists.none { MainPage.Playlist(it.id) == lastMainPage }
                    ) {
                        lastMainPage = MainPage.Stations
                    }
                    if (playbackState.selectedStation != null &&
                        importResult.stations.none { it.id == playbackState.selectedStation?.id }
                    ) {
                        PlaybackService.stop(context)
                    }
                    Toast.makeText(context, resources.appString(R.string.toast_stations_imported), Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    errorDialog = resources.appString(R.string.toast_import_failed, error.message.orEmpty())
                }
            }
        }

        fun readImport(uri: android.net.Uri?, decode: (String) -> StationExportData) {
            if (uri == null) return
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.readBytes().toString(Charsets.UTF_8)
                        } ?: error("Could not open import file")
                        decode(content)
                    }
                }.onSuccess { importData ->
                    pendingImportData = importData
                    if (stations.isEmpty() && playlists.isEmpty()) {
                        importStations(StationImportMode.Replace)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    errorDialog = if (error is IllegalArgumentException) {
                        resources.appString(R.string.import_invalid_format)
                    } else {
                        resources.appString(R.string.toast_import_failed, error.message.orEmpty())
                    }
                }
            }
        }

        val jsonImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            readImport(uri) { content ->
                StationExportCodec.decode(content.trimStart().removePrefix("\uFEFF").trimStart())
            }
        }

        val textImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            readImport(uri) { content ->
                SimpleStationTextCodec.decode(
                    text = content,
                    cleanTrackingParameters = removeTrackingParametersFromUrls,
                )
            }
        }

        LaunchedEffect(playbackState.errorText) {
            playbackState.errorText?.let { errorText ->
                errorDialog = errorText
                Toast.makeText(context, resources.appString(R.string.toast_playback_error), Toast.LENGTH_SHORT).show()
            }
        }

        fun loadOnlineFilterOptions(): Deferred<List<String>> {
            onlineFilterRequest?.let { return it }
            onlineFilterErrorsHandledBySearch = false
            val request = scope.async(start = CoroutineStart.LAZY) {
                try {
                    coroutineScope {
                        val requests = buildList {
                            add(async {
                                try {
                                    radioBrowserClient.cachedCountries()?.let { onlineCountries = it }
                                    if (NetworkStatus.isOnline(context)) {
                                        onlineCountries = radioBrowserClient.countries()
                                    }
                                    null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    "${resources.appString(R.string.online_search_countries)}: ${error.message ?: error.toString()}"
                                }
                            })
                            add(async {
                                try {
                                    radioBrowserClient.cachedLanguages()?.let { onlineLanguages = it }
                                    if (NetworkStatus.isOnline(context)) {
                                        onlineLanguages = radioBrowserClient.languages()
                                    }
                                    null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    "${resources.appString(R.string.online_search_languages)}: ${error.message ?: error.toString()}"
                                }
                            })
                        }
                        requests.awaitAll().filterNotNull()
                    }
                } finally {
                    onlineFilterRequest = null
                }
            }
            onlineFilterRequest = request
            return request
        }

        LaunchedEffect(selectedPage) {
            if (selectedPage == MainPage.SearchOnline) {
                onlineOptionsExpanded = true
                val errors = loadOnlineFilterOptions().await()
                if (errors.isNotEmpty() && !onlineFilterErrorsHandledBySearch) {
                    errorDialog = errors.joinToString("\n\n")
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

        LaunchedEffect(visibleTabPages, pendingPlaylistId) {
            val id = pendingPlaylistId
            if (id != null && MainPage.Playlist(id) in visibleTabPages) {
                selectMainPage(MainPage.Playlist(id))
                pendingPlaylistId = null
            }
        }

        fun activeNavigationPage(): MainPage {
            return selectedPage.takeIf { it in visibleTabPages }
                ?: lastMainPage.takeIf { it in visibleTabPages }
                ?: MainPage.Stations
        }

        LaunchedEffect(visibleTabPages, selectedPage, savedPlaylists != null) {
            if ((selectedPage == MainPage.Favorites || (selectedPage is MainPage.Playlist && savedPlaylists != null)) && selectedPage !in visibleTabPages) {
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
            if (selectedPage in visibleTabPages && selectedPage != page && membershipMode == null && !showSleepTimer && tabOrderDraft == null) {
                selectMainPage(page)
            }
        }

        fun exportStations() {
            scope.launch {
                try {
                    pendingExportContent = withContext(Dispatchers.IO) {
                        StationExportCodec.encode(repository.exportLibrary())
                    }
                    val exportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
                    jsonExportLauncher.launch("omnibeat_library_$exportDate.json")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    errorDialog = resources.appString(R.string.toast_export_failed, error.message.orEmpty())
                }
            }
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

        fun clearLibrary() {
            PlaybackService.stop(context)
            scope.launch {
                try {
                    repository.clearLibrary()
                    stations = emptyList()
                    stationSortState = StationSortState()
                    favoriteSortState = StationSortState()
                    customStationOrder = emptyList()
                    customFavoriteOrder = emptyList()
                    reorderDraft = null
                    tabOrderDraft = null
                    membershipMode = null
                    pendingPlaylistId = null
                    playbackQueueIds = null
                    lastPlayedStationId = null
                    lastMainPage = MainPage.Stations
                    Toast.makeText(context, resources.appString(R.string.toast_library_deleted), Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    errorDialog = error.message.orEmpty()
                }
            }
        }

        fun hasInternetOrToast(): Boolean {
            if (NetworkStatus.isOnline(context)) {
                return true
            }
            Toast.makeText(context, resources.appString(R.string.toast_no_internet), Toast.LENGTH_SHORT).show()
            return false
        }

        fun searchOnlineStations() {
            if (onlineSearchLoading || onlineSearchLoadingMore) return
            if (!NetworkStatus.isOnline(context)) {
                errorDialog = resources.appString(R.string.toast_no_internet)
                return
            }
            onlineSearchLoading = true
            val query = onlineSearchState
            scope.launch {
                val filterRequest = loadOnlineFilterOptions()
                onlineFilterErrorsHandledBySearch = true
                val errors = mutableListOf<String>()
                try {
                    runCatching {
                        radioBrowserClient.searchStations(query.toRadioBrowserParams(offset = 0))
                    }.onSuccess { results ->
                        onlineSearchResults = results
                        onlineSearchLastQuery = query
                        onlineSearchHasMore = results.size == RadioBrowserSearchParams.DEFAULT_LIMIT
                        onlineOptionsExpanded = false
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        onlineSearchResults = emptyList()
                        onlineSearchLastQuery = null
                        onlineSearchHasMore = false
                        val message = error.message ?: resources.appString(R.string.toast_search_default_error)
                        errors += message
                        Toast.makeText(context, resources.appString(R.string.toast_search_failed), Toast.LENGTH_SHORT).show()
                    }
                    errors += filterRequest.await()
                    if (errors.isNotEmpty()) errorDialog = errors.joinToString("\n\n")
                } finally {
                    onlineSearchLoading = false
                }
            }
        }

        fun stationFromOnlineResult(radioStation: RadioBrowserStation): Station {
            val streamUrl = radioStation.streamUrl
                .trim()
                .let { if (removeTrackingParametersFromUrls) removeTrackingParameters(it) else it }
                .let(::normalizeStreamUrl)
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
            playbackQueueIds = null
            PlaybackService.playPreview(context, stationFromOnlineResult(radioStation))
        }

        fun addOnlineStation(radioStation: RadioBrowserStation) {
            val station = stationFromOnlineResult(radioStation)
            if (station.streamUrl.isBlank() || stations.any { it.streamUrl == station.streamUrl }) return
            val nextStations = stations + station.copy(id = UUID.randomUUID().toString())
            stations = nextStations
            scope.launch {
                repository.saveStations(nextStations)
                Toast.makeText(context, resources.appString(R.string.toast_station_added), Toast.LENGTH_SHORT).show()
            }
        }

        suspend fun refreshStationArtwork(station: Station): Boolean {
            val imageUrl = radioBrowserClient.findStationByStreamUrl(station.streamUrl)
                ?.imageUrl
                ?.takeIf { it.isNotBlank() }
                ?: return false
            StationArtworkCache.refresh(context, imageUrl)
            val currentStation = stations.firstOrNull { it.id == station.id } ?: return false
            if (currentStation.streamUrl != station.streamUrl) return false
            if (currentStation.imageUrl != imageUrl) {
                val nextStations = stations.map { savedStation ->
                    if (savedStation.id == station.id) savedStation.copy(imageUrl = imageUrl) else savedStation
                }
                stations = nextStations
                repository.saveStations(nextStations)
            }
            return true
        }

        fun syncStationArtwork() {
            if (syncingStationArtwork) return
            if (!hasInternetOrToast()) return
            syncingStationArtwork = true
            scope.launch {
                var updatedCount = 0
                try {
                    stations.toList().forEach { station ->
                        runCatching {
                            if (refreshStationArtwork(station)) updatedCount++
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                        }
                    }
                    Toast.makeText(
                        context,
                        resources.appString(R.string.toast_artwork_synced_count, updatedCount),
                        Toast.LENGTH_SHORT,
                    ).show()
                } finally {
                    syncingStationArtwork = false
                }
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
            Toast.makeText(context, resources.appString(R.string.toast_artwork_searching), Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching {
                    refreshStationArtwork(station)
                }.onSuccess { refreshed ->
                    if (!refreshed) {
                        Toast.makeText(context, resources.appString(R.string.toast_artwork_not_found), Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    Toast.makeText(context, resources.appString(R.string.toast_artwork_synced), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Toast.makeText(
                        context,
                        resources.appString(R.string.toast_artwork_sync_failed, error.message.orEmpty()),
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

        fun playlistFor(page: MainPage): Playlist? =
            (page as? MainPage.Playlist)?.let { tab -> playlists.firstOrNull { it.id == tab.id } }

        fun stationsForPage(page: MainPage): List<Station> {
            if (page is MainPage.Playlist) return playlistFor(page)?.stations(stations).orEmpty()
            return sortedStations(
                source = if (page == MainPage.Favorites) stations.filter { it.isFavorite } else stations,
                page = page,
                sortState = if (page == MainPage.Favorites) favoriteSortState else stationSortState,
                customStationOrder = customStationOrder,
                customFavoriteOrder = customFavoriteOrder,
            )
        }

        fun navigationStations(): List<Station> {
            if (selectedPage == MainPage.SearchOnline) {
                return onlineSearchResults.map(::stationFromOnlineResult)
            }
            return stationsForPage(activeNavigationPage())
        }

        fun playStationAt(index: Int, queue: List<Station> = navigationStations()) {
            if (!hasInternetOrToast()) return
            expandPlayerPanelForUserPlaybackRequest()
            scope.launch { drawerState.close() }
            scrollToStationId = stations.getOrNull(index)?.id
            scrollToSelectedRequest += 1
            playbackQueueIds = queue.map { it.id }
            PlaybackService.playStation(
                context = context,
                index = index,
                queueIds = queue.map { it.id },
            )
        }

        fun playStation(station: Station) {
            val index = stations.indexOfFirst { it.id == station.id }
            if (index != -1) {
                playStationAt(index)
            } else {
                if (!hasInternetOrToast()) return
                expandPlayerPanelForUserPlaybackRequest()
                playbackQueueIds = null
                PlaybackService.playPreview(context, station)
            }
        }

        fun loadMoreOnlineStations() {
            if (onlineSearchLoading || onlineSearchLoadingMore || !onlineSearchHasMore) return
            if (!NetworkStatus.isOnline(context)) {
                errorDialog = resources.appString(R.string.toast_no_internet)
                return
            }
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
                    if (error is CancellationException) throw error
                    val message = error.message ?: resources.appString(R.string.toast_load_more_default_error)
                    errorDialog = message
                    Toast.makeText(context, resources.appString(R.string.toast_search_failed), Toast.LENGTH_SHORT).show()
                }
                onlineSearchLoadingMore = false
            }
        }

        fun saveBuiltinSortState(page: MainPage, state: StationSortState) {
            if (page == MainPage.Favorites) {
                favoriteSortState = state
                scope.launch { repository.saveFavoriteSortState(state) }
            } else {
                stationSortState = state
                scope.launch { repository.saveStationSortState(state) }
            }
        }

        fun selectSortMode(nextSortMode: StationSortMode) {
            val page = activeNavigationPage()
            val playlist = playlistFor(page)
            if (playlist != null) {
                if (nextSortMode == StationSortMode.Custom) {
                    reorderDraft = StationReorderDraft(page, playlist.stations(stations))
                }
                scope.launch {
                    repository.updatePlaylist(playlist.id) { current ->
                        val nextState = if (nextSortMode == current.sortState.mode && nextSortMode != StationSortMode.Custom) {
                            current.sortState.copy(ascending = !current.sortState.ascending)
                        } else {
                            StationSortState(nextSortMode, ascending = false)
                        }
                        current.copy(sortState = nextState)
                    }
                }
                return
            }
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
                saveBuiltinSortState(reorderPage, nextSortState)
                reorderDraft = StationReorderDraft(page = reorderPage, stations = draftStations)
                return
            }
            val current = if (page == MainPage.Favorites) favoriteSortState else stationSortState
            val nextSortState = if (current.mode == nextSortMode) {
                current.copy(ascending = !current.ascending)
            } else {
                StationSortState(mode = nextSortMode, ascending = false)
            }
            saveBuiltinSortState(page, nextSortState)
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
            val playlist = playlistFor(draft.page)
            if (playlist != null) {
                reorderDraft = null
                scope.launch {
                    repository.updatePlaylist(playlist.id) {
                        it.copy(stationIds = nextOrder, sortState = StationSortState(StationSortMode.Custom, false))
                    }
                }
                return
            }
            val nextSortState = StationSortState(mode = StationSortMode.Custom, ascending = false)
            if (draft.page == MainPage.Favorites) {
                customFavoriteOrder = nextOrder
            } else {
                customStationOrder = nextOrder
            }
            saveBuiltinSortState(draft.page, nextSortState)
            reorderDraft = null
            scope.launch {
                if (draft.page == MainPage.Favorites) {
                    repository.saveCustomFavoriteOrder(nextOrder)
                } else {
                    repository.saveCustomStationOrder(nextOrder)
                }
            }
        }

        fun playbackStations(): List<Station> {
            if (selectedPage == MainPage.SearchOnline) return navigationStations()
            val ids = playbackQueueIds ?: return navigationStations()
            val byId = stations.associateBy { it.id }
            return ids.mapNotNull(byId::get)
        }

        fun playAdjacentStation(direction: Int) {
            val pageStations = playbackStations()
            if (pageStations.isEmpty()) return
            val currentIndex = pageStations.indexOfFirst { it.id == playbackState.selectedStation?.id }
            val nextIndex = if (currentIndex in pageStations.indices) {
                (currentIndex + direction + pageStations.size) % pageStations.size
            } else if (direction > 0) {
                0
            } else {
                pageStations.lastIndex
            }
            val station = pageStations[nextIndex]
            val index = stations.indexOfFirst { it.id == station.id }
            if (index >= 0) playStationAt(index, queue = pageStations)
            else playStation(station)
        }

        fun playRandomStation() {
            val pageStations = playbackStations()
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
            val station = pageStations[nextIndex]
            val index = stations.indexOfFirst { it.id == station.id }
            if (index >= 0) playStationAt(index, queue = pageStations)
            else playStation(station)
        }

        fun playOrPause() {
            val pageStations = navigationStations()
            val hasActivePlaybackRequest = playbackState.isPlaying || playbackState.resolving || playbackState.buffering
            if (!hasActivePlaybackRequest && playbackState.selectedStation == null && pageStations.isNotEmpty()) {
                val rememberedStation = if (rememberLastStation && selectedPage != MainPage.SearchOnline) {
                    lastPlayedStationId
                        ?.let { stationId -> pageStations.firstOrNull { it.id == stationId } }
                } else {
                    null
                }
                playStation(rememberedStation ?: pageStations.first())
            } else {
                if (!hasActivePlaybackRequest && !hasInternetOrToast()) return
                PlaybackService.playOrPause(context)
            }
        }

        BackHandler(enabled = tabOrderDraft != null || membershipMode != null || showSleepTimer || drawerState.isOpen || reorderDraft != null || selectedPage !in visibleTabPages) {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else if (tabOrderDraft != null) {
                tabOrderDraft = null
            } else if (membershipMode != null) {
                membershipMode = null
            } else if (showSleepTimer) {
                showSleepTimer = false
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
                    isOpen = drawerState.isOpen,
                    translationEnabled = translationEnabled,
                    onToggleTranslation = {
                        scope.launch {
                            val enabled = repository.toggleTranslation()
                            val message = if (enabled) R.string.translation_enabled else R.string.translation_disabled
                            Toast.makeText(context, resources.appString(message), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTranslationClick = {
                        selectedPage = MainPage.Translation
                        scope.launch { drawerState.close() }
                    },
                    onStationsClick = {
                        selectedPage = lastMainPage
                        scope.launch { drawerState.close() }
                    },
                    onSleepTimerClick = {
                        showSleepTimer = true
                        scope.launch { drawerState.close() }
                    },
                    sleepTimerOpen = showSleepTimer,
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
                        MainActivity.closeApp(context)
                    },
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = RadioBackground,
                    floatingActionButtonPosition = when (sleepTimerPlacement) {
                        SleepTimerPlacement.PanelLeft -> if (layoutDirection == LayoutDirection.Ltr) FabPosition.Start else FabPosition.End
                        else -> if (layoutDirection == LayoutDirection.Ltr) FabPosition.End else FabPosition.Start
                    },
                    topBar = {
                        MainTopBar(
                            selectedPage = selectedPage,
                            visualSelectedPage = visualSelectedPage,
                            tabPages = visibleTabPages,
                            sortState = currentSortState,
                            playlists = playlists,
                            onCreatePlaylist = {
                                editingPlaylistId = null
                                showPlaylistEditor = true
                            },
                            onAddToPlaylist = { membershipMode = PlaylistMembershipMode.Add },
                            onRemoveFromPlaylist = { membershipMode = PlaylistMembershipMode.Remove },
                            onRenamePlaylist = {
                                editingPlaylistId = currentPlaylist?.id
                                showPlaylistEditor = true
                            },
                            onDeletePlaylist = { deletingPlaylistId = currentPlaylist?.id },
                            reordering = reorderDraft != null || tabOrderDraft != null,
                            reorderingTabs = tabOrderDraft != null,
                            onPageSelected = { page ->
                                if (reorderDraft == null) {
                                    selectMainPage(page)
                                }
                            },
                            onSortModeSelected = ::selectSortMode,
                            onReorderTabs = { tabOrderDraft = allTabPages.map { it.name } },
                            onCancelReorder = {
                                if (tabOrderDraft != null) tabOrderDraft = null else cancelReorder()
                            },
                            onConfirmReorder = {
                                val order = tabOrderDraft
                                if (order == null) {
                                    confirmReorder()
                                } else {
                                    tabOrderDraft = null
                                    scope.launch { repository.saveTabOrder(order) }
                                }
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigateBack = {
                                if (tabOrderDraft != null) tabOrderDraft = null
                                else if (membershipMode != null) membershipMode = null
                                else if (showSleepTimer) showSleepTimer = false else selectedPage = lastMainPage
                            },
                            overlayTitle = when {
                                tabOrderDraft != null -> resources.appString(R.string.action_reorder_tabs)
                                showSleepTimer -> resources.appString(R.string.page_sleep_timer)
                                membershipMode == PlaylistMembershipMode.Add -> resources.appString(R.string.playlist_add)
                                membershipMode == PlaylistMembershipMode.Remove -> resources.appString(R.string.playlist_remove)
                                else -> null
                            },
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
                    floatingActionButton = {
                        if (showSleepTimerPill && sleepTimerPlacement != SleepTimerPlacement.FreePlacement) {
                            SleepTimerPill(
                                remainingSeconds = sleepTimerRemainingSeconds,
                                onClick = { showSleepTimer = true },
                            )
                        }
                    },
                    bottomBar = {
                        if (showPlayerPanel) {
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
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (selectedPage) {
                            MainPage.Stations,
                            MainPage.Favorites,
                            is MainPage.Playlist -> {
                                StationTabsPager(
                                    visibleTabPagesState = visibleTabPagesState,
                                    playlists = playlists,
                                    pagerState = pagerState,
                                    stations = stations,
                                    selectedStationId = playbackState.selectedStation?.id,
                                    playingStationId = playbackState.selectedStation?.id.takeIf { playbackState.isPlaying },
                                    showPlaybackIndicator = showPlaybackIndicator,
                                    showFavoriteButtons = showFavoriteButtons,
                                    stationSortState = stationSortState,
                                    favoriteSortState = favoriteSortState,
                                    customStationOrder = customStationOrder,
                                    customFavoriteOrder = customFavoriteOrder,
                                    reorderDraft = reorderDraft,
                                    scrollToSelectedRequest = scrollToSelectedRequest,
                                    scrollToStationId = scrollToStationId,
                                    showArtwork = showStationArtwork,
                                    dividerStyle = stationDividers,
                                    listEnabled = drawerState.isClosed && reorderDraft == null && tabOrderDraft == null && membershipMode == null && !showSleepTimer,
                                    onMove = ::moveReorderDraft,
                                    onFavoriteClick = ::toggleFavorite,
                                    onEditStation = { editorState = it },
                                    onStationClick = { page, station ->
                                        val index = stations.indexOfFirst { it.id == station.id }
                                        if (index >= 0) playStationAt(index, queue = stationsForPage(page))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            MainPage.ExportImport -> {
                                ExportImportPage(
                                    stationCount = stations.size,
                                    favoriteCount = stations.count { it.isFavorite },
                                    playlistCount = playlists.size,
                                    onExportStations = { exportStations() },
                                    onExportSimpleText = { exportSimpleText() },
                                    onImportJson = {
                                        jsonImportLauncher.launch(arrayOf("application/json"))
                                    },
                                    onImportSimpleText = {
                                        textImportLauncher.launch(arrayOf("text/plain"))
                                    },

                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            MainPage.Translation -> TranslationPage(repository = repository)

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
                                    dividerStyle = stationDividers,
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
                                    stationDividers = stationDividers,
                                    showPlaybackIndicator = showPlaybackIndicator,
                                    showFavoriteButtons = showFavoriteButtons,
                                    showEmptyFavoritesTab = showEmptyFavoritesTab,
                                    confirmStationDeletion = confirmStationDeletion,
                                    showStationArtwork = showStationArtwork,
                                    syncingStationArtwork = syncingStationArtwork,
                                    rememberLastStation = rememberLastStation,
                                    stopServiceAfterPauseMinutes = stopServiceAfterPauseMinutes,
                                    keepMediaCard = keepMediaCard,
                                    showBitrateInControlPanel = showBitrateInControlPanel,
                                    showUnavailableBitrate = showUnavailableBitrate,
                                    marqueeTrackTitle = marqueeTrackTitle,
                                    autoExpandPlayerPanelOnPlayback = autoExpandPlayerPanelOnPlayback,
                                    collapsePlayerPanelInSearch = collapsePlayerPanelInSearch,
                                    addRadioBrowserTags = addRadioBrowserTags,
                                    sleepTimerCloseApp = sleepTimerCloseApp,
                                    sleepTimerPlacement = sleepTimerPlacement,
                                    sleepTimerAlwaysShow = sleepTimerAlwaysShow,
                                    removeTrackingParameters = removeTrackingParametersFromUrls,
                                    widgetFollowAppTheme = widgetFollowAppTheme,
                                    widgetMarqueeTrackTitle = widgetMarqueeTrackTitle,
                                    showAndroidAutoArtwork = showAndroidAutoArtwork,
                                    onThemeModeChange = { nextThemeMode ->
                                        scope.launch { repository.saveThemeMode(nextThemeMode) }
                                    },
                                    onAppLanguageChange = { nextAppLanguage ->
                                        context.applicationContext.applyAppLanguage(nextAppLanguage)
                                    },
                                    onStationDividersChange = { style ->
                                        stationDividers = style
                                        scope.launch { repository.saveStationDividers(style) }
                                    },
                                    onShowPlaybackIndicatorChange = { show ->
                                        showPlaybackIndicator = show
                                        scope.launch { repository.saveShowPlaybackIndicator(show) }
                                    },
                                    onShowFavoriteButtonsChange = { show ->
                                        showFavoriteButtons = show
                                        scope.launch { repository.saveShowFavoriteButtons(show) }
                                    },
                                    onShowEmptyFavoritesTabChange = { show ->
                                        showEmptyFavoritesTab = show
                                        scope.launch { repository.saveShowEmptyFavoritesTab(show) }
                                    },
                                    onConfirmStationDeletionChange = { confirm ->
                                        confirmStationDeletion = confirm
                                        scope.launch { repository.saveConfirmStationDeletion(confirm) }
                                    },
                                    onShowStationArtworkChange = { show ->
                                        showStationArtwork = show
                                        scope.launch { repository.saveShowStationArtwork(show) }
                                    },
                                    onSyncStationArtwork = { syncStationArtwork() },
                                    onRememberLastStationChange = { remember ->
                                        rememberLastStation = remember
                                        scope.launch { repository.saveRememberLastStation(remember) }
                                    },
                                    onStopServiceAfterPauseMinutesChange = { minutes ->
                                        stopServiceAfterPauseMinutes = minutes
                                        scope.launch { repository.saveStopServiceAfterPauseMinutes(minutes) }
                                    },
                                    onKeepMediaCardChange = { keep ->
                                        repository.saveKeepMediaCard(keep)
                                        keepMediaCard = keep
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
                                    onAutoExpandPlayerPanelOnPlaybackChange = { autoExpand ->
                                        autoExpandPlayerPanelOnPlayback = autoExpand
                                        scope.launch { repository.saveAutoExpandPlayerPanelOnPlayback(autoExpand) }
                                    },
                                    onCollapsePlayerPanelInSearchChange = { collapse ->
                                        collapsePlayerPanelInSearch = collapse
                                        scope.launch { repository.saveCollapsePlayerPanelInSearch(collapse) }
                                    },
                                    onAddRadioBrowserTagsChange = { add ->
                                        addRadioBrowserTags = add
                                        scope.launch { repository.saveAddRadioBrowserTags(add) }
                                    },
                                    onSleepTimerCloseAppChange = { closeApp ->
                                        scope.launch { repository.saveSleepTimerCloseApp(closeApp) }
                                    },
                                    onSleepTimerPlacementChange = { placement ->
                                        scope.launch { repository.saveSleepTimerPlacement(placement) }
                                    },
                                    onSleepTimerAlwaysShowChange = { alwaysShow ->
                                        scope.launch { repository.saveSleepTimerAlwaysShow(alwaysShow) }
                                    },
                                    onRemoveTrackingParametersChange = { remove ->
                                        removeTrackingParametersFromUrls = remove
                                        scope.launch { repository.saveRemoveTrackingParameters(remove) }
                                    },
                                    onWidgetFollowAppThemeChange = { follow ->
                                        widgetFollowAppTheme = follow
                                        scope.launch {
                                            repository.saveWidgetFollowAppTheme(follow)
                                            PlaybackWidget.requestUpdate(context)
                                        }
                                    },
                                    onWidgetMarqueeTrackTitleChange = { marquee ->
                                        widgetMarqueeTrackTitle = marquee
                                        scope.launch {
                                            repository.saveWidgetMarqueeTrackTitle(marquee)
                                            PlaybackWidget.requestUpdate(context)
                                        }
                                    },
                                    onShowAndroidAutoArtworkChange = { show ->
                                        showAndroidAutoArtwork = show
                                        scope.launch { repository.saveShowAndroidAutoArtwork(show) }
                                    },
                                    onClearLibrary = { clearLibrary() },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            MainPage.About -> {
                                AboutPage(
                                    onWhatsNewClick = { showWhatsNew = true },
                                    modifier = Modifier.fillMaxSize(),
                                    updates = updates,
                                )
                            }
                        }
                        val membership = membershipMode
                        if (membership != null && currentPlaylist != null) {
                            androidx.compose.runtime.key(currentPlaylist.id, membership) {
                                Box(Modifier.fillMaxSize().background(RadioBackground)) {
                                    PlaylistMembershipPage(
                                        stations = if (membership == PlaylistMembershipMode.Add) stationsForPage(MainPage.Stations)
                                            else currentPlaylist.stations(stations),
                                        playlist = currentPlaylist,
                                        mode = membership,
                                        showArtwork = showStationArtwork,
                                        dividerStyle = stationDividers,
                                        selectedStationId = playbackState.selectedStation?.id,
                                        onStationClick = { station ->
                                            val index = stations.indexOfFirst { it.id == station.id }
                                            if (index >= 0) {
                                                val queue = if (membership == PlaylistMembershipMode.Add)
                                                    stationsForPage(MainPage.Stations) else currentPlaylist.stations(stations)
                                                playStationAt(index, queue = queue)
                                            }
                                        },
                                        onMembershipChange = { stationId ->
                                            scope.launch {
                                                repository.updatePlaylist(currentPlaylist.id) { playlist ->
                                                    playlist.copy(stationIds = if (membership == PlaylistMembershipMode.Add)
                                                        (playlist.stationIds + stationId).distinct()
                                                        else playlist.stationIds - stationId)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        tabOrderDraft?.let { order ->
                            ReorderTabsPage(
                                tabs = orderedTabs(playlists, order),
                                playlists = playlists,
                                dividerStyle = stationDividers,
                                onMove = { from, to ->
                                    val draft = tabOrderDraft ?: return@ReorderTabsPage
                                    if (from in draft.indices && to in draft.indices && from != to) {
                                        tabOrderDraft = draft.toMutableList().apply { add(to, removeAt(from)) }
                                    }
                                },
                            )
                        }
                        if (showSleepTimer) {
                            SleepTimerPage(
                                remainingSeconds = sleepTimerRemainingSeconds,
                                onStart = { seconds ->
                                    PlaybackService.startSleepTimer(context, seconds)
                                    showSleepTimer = false
                                },
                                onStop = {
                                    PlaybackService.cancelSleepTimer(context)
                                    showSleepTimer = false
                                },
                            )
                        }
                    }
                }
                if (showSleepTimerPill && sleepTimerPlacement == SleepTimerPlacement.FreePlacement) {
                    FreeSleepTimerPill(
                        remainingSeconds = sleepTimerRemainingSeconds,
                        position = sleepTimerFreePosition,
                        onPositionChange = { position ->
                            scope.launch { repository.saveSleepTimerFreePosition(position) }
                        },
                        onClick = { showSleepTimer = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(RadioSpacing.small),
                    )
                }
            }
        }

        if (showPlaylistEditor && savedPlaylists != null) {
            PlaylistTitleDialog(
                playlist = playlists.firstOrNull { it.id == editingPlaylistId },
                onDismiss = { showPlaylistEditor = false },
                onSave = { title ->
                    val id = editingPlaylistId
                    showPlaylistEditor = false
                    scope.launch {
                        if (id == null) {
                            pendingPlaylistId = repository.createPlaylist(title)
                        } else {
                            repository.updatePlaylist(id) { it.copy(title = title) }
                        }
                    }
                },
            )
        }
        deletingPlaylistId?.let { id ->
            OmniConfirmDialog(
                title = resources.appString(R.string.playlist_delete),
                text = resources.appString(R.string.playlist_delete_message),
                confirmText = resources.appString(R.string.action_delete),
                destructive = true,
                onDismiss = { deletingPlaylistId = null },
                onConfirm = {
                    deletingPlaylistId = null
                    membershipMode = null
                    selectMainPage(MainPage.Stations)
                    scope.launch { repository.deletePlaylist(id) }
                },
            )
        }

        if (showWhatsNew) {
            WhatsNewDialog(
                onDismiss = {
                    scope.launch {
                        repository.saveWhatsNewSeenVersionCode(versionCode)
                        showWhatsNew = false
                    }
                },
            )
        }

        ReleaseWarning.ShowIfNeeded(
            repository = repository,
            canShow = whatsNewSeenVersionCode == versionCode && !showWhatsNew,
        )

        pendingImportData?.let { importData ->
            ImportStationsDialog(
                stationCount = importData.stations.size,
                playlistCount = importData.collections?.playlists?.size,
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
                    val cleanedStreamUrl = if (state.stationIndex == null && removeTrackingParametersFromUrls) {
                        removeTrackingParameters(trimmedStreamUrl)
                    } else {
                        trimmedStreamUrl
                    }
                    val savedStreamUrl = normalizeStreamUrl(cleanedStreamUrl)
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
