package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.data.StationExportData
import omnibeat.app.model.STATION_STREAM_URL_MAX_LENGTH
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH
import omnibeat.app.radio.RadioBrowserClient
import omnibeat.app.radio.RadioBrowserFilterOption

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import omnibeat.app.network.NO_INTERNET_MESSAGE
import omnibeat.app.network.NetworkStatus
import omnibeat.app.playback.PlaybackService
import omnibeat.app.radio.RadioBrowserStation
import omnibeat.app.radio.stationTags
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniBeatApp() {
    val context = LocalContext.current
    val repository = remember(context) { StationRepository(context.applicationContext) }
    val themeMode by repository.themeMode.collectAsState(initial = ThemeMode.System)
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
        val notificationPermissionPromptCompleted by repository.notificationPermissionPromptCompleted.collectAsState(
            initial = null,
        )
        var notificationPermissionGranted by remember {
            mutableStateOf(hasNotificationPermission(context))
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            notificationPermissionGranted = granted
            scope.launch { repository.saveNotificationPermissionPromptCompleted(true) }
        }

        if (onboardingCompleted == null || notificationPermissionPromptCompleted == null) {
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

        if (!notificationPermissionGranted && notificationPermissionPromptCompleted == false) {
            NotificationPermissionIntro(
                onGrant = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onSkip = {
                    scope.launch { repository.saveNotificationPermissionPromptCompleted(true) }
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
        var removeTrackingParametersFromUrls by remember { mutableStateOf(false) }
        var rememberLastStation by remember { mutableStateOf(true) }
        var showBitrateInControlPanel by remember { mutableStateOf(true) }
        var showUnavailableBitrate by remember { mutableStateOf(false) }
        var marqueeTrackTitle by remember { mutableStateOf(true) }
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
            repository.showStationArtwork.collect { savedShowStationArtwork ->
                showStationArtwork = savedShowStationArtwork
            }
        }

        LaunchedEffect(repository) {
            repository.addRadioBrowserTags.collect { savedAddRadioBrowserTags ->
                addRadioBrowserTags = savedAddRadioBrowserTags
            }
        }

        LaunchedEffect(repository) {
            repository.removeTrackingParameters.collect { savedRemoveTrackingParameters ->
                removeTrackingParametersFromUrls = savedRemoveTrackingParameters
            }
        }

        LaunchedEffect(repository) {
            repository.rememberLastStation.collect { savedRememberLastStation ->
                rememberLastStation = savedRememberLastStation
            }
        }

        LaunchedEffect(repository) {
            repository.showBitrateInControlPanel.collect { savedShowBitrate ->
                showBitrateInControlPanel = savedShowBitrate
            }
        }

        LaunchedEffect(repository) {
            repository.showUnavailableBitrate.collect { savedShowUnavailableBitrate ->
                showUnavailableBitrate = savedShowUnavailableBitrate
            }
        }

        LaunchedEffect(repository) {
            repository.marqueeTrackTitle.collect { savedMarqueeTrackTitle ->
                marqueeTrackTitle = savedMarqueeTrackTitle
            }
        }

        LaunchedEffect(repository) {
            repository.showEmptyFavoritesTab.collect { savedShowEmptyFavoritesTab ->
                showEmptyFavoritesTab = savedShowEmptyFavoritesTab
            }
        }

        LaunchedEffect(repository) {
            repository.confirmStationDeletion.collect { savedConfirmStationDeletion ->
                confirmStationDeletion = savedConfirmStationDeletion
            }
        }

        LaunchedEffect(repository) {
            repository.lastMainPage.collect { savedPage ->
                val restoredPage = MainPage.tabPages.firstOrNull { it.name == savedPage } ?: MainPage.Stations
                lastMainPage = restoredPage
                if (selectedPage in visibleTabPages) {
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
                    Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val jsonExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            writePendingExport(uri, "JSON exported")
        }

        val textExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            writePendingExport(uri, "TXT exported")
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
                    Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        LaunchedEffect(playbackState.errorText) {
            playbackState.errorText?.let { errorText ->
                errorDialog = errorText
                Toast.makeText(context, "Playback error", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(selectedPage) {
            if (selectedPage == MainPage.FindOnline) {
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

        LaunchedEffect(selectedPage) {
            val tabIndex = visibleTabPages.indexOf(selectedPage)
            if (tabIndex >= 0 && pagerState.currentPage != tabIndex) {
                pagerState.animateScrollToPage(tabIndex)
            }
        }

        LaunchedEffect(pagerState, visibleTabPages) {
            snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
                val page = visibleTabPages.getOrNull(pageIndex) ?: return@collect
                if (selectedPage in visibleTabPages && selectedPage != page) {
                    selectMainPage(page)
                }
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
                Toast.makeText(context, "Stations imported", Toast.LENGTH_SHORT).show()
            }
        }

        fun deleteEntireLibrary() {
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
                Toast.makeText(context, "Library deleted", Toast.LENGTH_SHORT).show()
            }
        }

        fun hasInternetOrToast(): Boolean {
            if (NetworkStatus.isOnline(context)) {
                return true
            }
            Toast.makeText(context, NO_INTERNET_MESSAGE, Toast.LENGTH_SHORT).show()
            return false
        }

        fun searchOnlineStations() {
            if (onlineSearchLoading) return
            if (!hasInternetOrToast()) return
            onlineSearchLoading = true
            scope.launch {
                runCatching {
                    radioBrowserClient.searchStations(onlineSearchState.toRadioBrowserParams())
                }.onSuccess { results ->
                    onlineSearchResults = results
                    onlineOptionsExpanded = false
                }.onFailure { error ->
                    onlineSearchResults = emptyList()
                    val message = error.message ?: "Could not search stations"
                    errorDialog = message
                    Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                }
                onlineSearchLoading = false
            }
        }

        fun stationFromOnlineResult(radioStation: RadioBrowserStation): Station {
            val streamUrl = radioStation.streamUrl
                .trim()
                .let { if (removeTrackingParametersFromUrls) removeTrackingParameters(it) else it }
            return Station(
                id = radioStation.stationUuid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                title = radioStation.title.trim().take(STATION_TITLE_MAX_LENGTH)
                    .ifBlank { streamUrl.take(STATION_TITLE_MAX_LENGTH) },
                streamUrl = streamUrl.take(STATION_STREAM_URL_MAX_LENGTH),
                tags = if (addRadioBrowserTags) radioStation.stationTags() else emptyList(),
                imageUrl = radioStation.imageUrl,
                isFavorite = false,
                dateAdded = Instant.now().toString(),
            )
        }

        fun previewOnlineStation(radioStation: RadioBrowserStation) {
            if (radioStation.streamUrl.isBlank()) return
            if (!hasInternetOrToast()) return
            PlaybackService.playPreview(context, stationFromOnlineResult(radioStation))
        }

        fun addOnlineStation(radioStation: RadioBrowserStation) {
            val station = stationFromOnlineResult(radioStation)
            if (station.streamUrl.isBlank() || stations.any { it.streamUrl == station.streamUrl }) return
            val nextStations = stations + station.copy(id = UUID.randomUUID().toString())
            stations = nextStations
            scope.launch {
                repository.saveStations(nextStations)
                Toast.makeText(context, "Station added", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Artwork synced: ${updates.size}", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Artwork sync failed: ${error.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "Searching for artwork...", Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        radioBrowserClient.findStationByStreamUrl(station.streamUrl)
                            ?.imageUrl
                            ?.takeIf { it.isNotBlank() }
                    }
                }.onSuccess { imageUrl ->
                    if (imageUrl == null) {
                        Toast.makeText(context, "No artwork found", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Artwork synced", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Artwork sync failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
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
            if (!hasInternetOrToast()) return
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
            val navigationPage = activeNavigationPage()
            val pageStations = if (navigationPage == MainPage.Favorites) {
                stations.filter { it.isFavorite }
            } else {
                stations
            }
            return sortedStations(pageStations, navigationPage)
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

        fun playOrStop() {
            val pageStations = navigationStations()
            val hasActivePlaybackRequest = playbackState.isPlaying || playbackState.resolving || playbackState.buffering
            if (!hasActivePlaybackRequest && playbackState.selectedStation == null && pageStations.isNotEmpty()) {
                val rememberedStation = if (rememberLastStation) {
                    lastPlayedStationId
                        ?.let { stationId -> stations.firstOrNull { it.id == stationId } }
                } else {
                    null
                }
                playStation(rememberedStation ?: pageStations.first())
            } else {
                if (!hasActivePlaybackRequest && !hasInternetOrToast()) return
                PlaybackService.playOrStop(context)
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
                        onAddStation = {
                            editorState = StationEditorState(
                                stationIndex = null,
                                title = "",
                                streamUrl = "",
                                tags = "",
                                dateAdded = null,
                            )
                        },
                        onFindOnline = {
                            selectedPage = MainPage.FindOnline
                        },
                        onlineSearchControl = if (selectedPage == MainPage.FindOnline) {
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
                    if (selectedPage in visibleTabPages || selectedPage == MainPage.FindOnline) {
                        val pageStations = navigationStations()
                        val canStartPlayback = pageStations.isNotEmpty() ||
                            (
                                rememberLastStation &&
                                    lastPlayedStationId?.let { stationId -> stations.any { it.id == stationId } } == true
                            )
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
                            showBitrate = showBitrateInControlPanel,
                            showUnavailableBitrate = showUnavailableBitrate,
                            marqueeTrackTitle = marqueeTrackTitle,
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
                                    when (visibleTabPages[pageIndex]) {
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
                                                    showArtwork = showStationArtwork,
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
                                                                dateAdded = station.dateAdded,
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
                                                    showArtwork = showStationArtwork,
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
                                                                dateAdded = station.dateAdded,
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
                                onExportSimpleText = { exportSimpleText() },
                                onImportStations = {
                                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                },
                                onDeleteLibrary = { deleteEntireLibrary() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        MainPage.FindOnline -> {
                            OnlineStationSearchPage(
                                searchState = onlineSearchState,
                                countries = onlineCountries,
                                languages = onlineLanguages,
                                results = onlineSearchResults,
                                loading = onlineSearchLoading,
                                optionsExpanded = onlineOptionsExpanded,
                                showArtwork = showStationArtwork,
                                addedStreamUrls = stations.mapTo(mutableSetOf()) { it.streamUrl },
                                selectedStreamUrl = playbackState.selectedStation?.streamUrl,
                                onSearchStateChange = {
                                    onlineSearchState = it
                                },
                                onSearch = { searchOnlineStations() },
                                onPreviewStation = { previewOnlineStation(it) },
                                onAddStation = { addOnlineStation(it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        MainPage.Settings -> {
                            SettingsPage(
                                themeMode = themeMode,
                                showStationArtwork = showStationArtwork,
                                addRadioBrowserTags = addRadioBrowserTags,
                                removeTrackingParameters = removeTrackingParametersFromUrls,
                                rememberLastStation = rememberLastStation,
                                showBitrateInControlPanel = showBitrateInControlPanel,
                                showUnavailableBitrate = showUnavailableBitrate,
                                marqueeTrackTitle = marqueeTrackTitle,
                                showEmptyFavoritesTab = showEmptyFavoritesTab,
                                confirmStationDeletion = confirmStationDeletion,
                                notificationPermissionGranted = notificationPermissionGranted,
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
                                onShowEmptyFavoritesTabChange = { show ->
                                    showEmptyFavoritesTab = show
                                    scope.launch { repository.saveShowEmptyFavoritesTab(show) }
                                },
                                onConfirmStationDeletionChange = { confirm ->
                                    confirmStationDeletion = confirm
                                    scope.launch { repository.saveConfirmStationDeletion(confirm) }
                                },
                                onGrantNotificationPermission = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onSyncStationArtwork = { syncStationArtwork() },
                                onDeleteLibrary = { deleteEntireLibrary() },
                                onThemeModeChange = { nextThemeMode ->
                                    scope.launch { repository.saveThemeMode(nextThemeMode) }
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
                        tags = parseTags(tags),
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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
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
    tabPages: List<MainPage>,
    sortState: StationSortState,
    reordering: Boolean,
    onPageSelected: (MainPage) -> Unit,
    onSortModeSelected: (StationSortMode) -> Unit,
    onCancelReorder: () -> Unit,
    onConfirmReorder: () -> Unit,
    onOpenDrawer: () -> Unit,
    onAddStation: () -> Unit,
    onFindOnline: () -> Unit,
    onlineSearchControl: (@Composable (Modifier) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }

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
        if (selectedPage == MainPage.FindOnline && onlineSearchControl != null) {
            onlineSearchControl(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (selectedPage in tabPages) {
                tabPages.forEach { tab ->
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
        }
        if (selectedPage in tabPages) {
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    color = RadioText,
                                )
                            },
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
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
                Box {
                    OmniTopBarIconButton(
                        painter = painterResource(R.drawable.ic_add_circle_outline),
                        contentDescription = "Add station",
                        onClick = { addMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                        containerColor = RadioSurface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        offset = DpOffset(x = 0.dp, y = 4.dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add manually",
                                    color = RadioText,
                                )
                            },
                            onClick = {
                                addMenuExpanded = false
                                onAddStation()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_circle_outline),
                                    contentDescription = null,
                                    tint = RadioText,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Find online",
                                    color = RadioText,
                                )
                            },
                            onClick = {
                                addMenuExpanded = false
                                onFindOnline()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    tint = RadioText,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
