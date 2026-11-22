package omnibeat.app.data

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import omnibeat.app.model.ImportedTranslation
import omnibeat.app.model.Station
import omnibeat.app.model.Playlist
import omnibeat.app.model.orderedTabs
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.model.ThemeMode
import omnibeat.app.model.SleepTimerPlacement
import omnibeat.app.model.SleepTimerFreePosition
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

private val Context.stationDataStore by preferencesDataStore(name = "stations")
private const val DEFAULT_APP_VOLUME = 0.75f
const val STOP_SERVICE_AFTER_PAUSE_NEVER = 0
const val DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES = 5
private val appVolumeKey = floatPreferencesKey("app_volume")
private val themeModeKey = stringPreferencesKey("theme_mode")
private val stationDividersKey = stringPreferencesKey("station_dividers")
private val showPlaybackIndicatorKey = booleanPreferencesKey("show_playback_indicator")
private val showFavoriteButtonsKey = booleanPreferencesKey("show_favorite_buttons")
private val showEmptyFavoritesTabKey = booleanPreferencesKey("show_empty_favorites_tab")
private val confirmStationDeletionKey = booleanPreferencesKey("confirm_station_deletion")
private val showStationArtworkKey = booleanPreferencesKey("show_station_artwork")
private val rememberLastStationKey = booleanPreferencesKey("remember_last_station")
private val stopServiceAfterPauseMinutesKey = intPreferencesKey("stop_service_after_pause_minutes")
private val showBitrateInControlPanelKey = booleanPreferencesKey("show_bitrate_in_control_panel")
private val showUnavailableBitrateKey = booleanPreferencesKey("show_unavailable_bitrate")
private val marqueeTrackTitleKey = booleanPreferencesKey("marquee_track_title")
private val autoExpandPlayerPanelOnPlaybackKey = booleanPreferencesKey("auto_expand_player_panel_on_playback")
private val collapsePlayerPanelInSearchKey = booleanPreferencesKey("collapse_player_panel_in_search")
private val addRadioBrowserTagsKey = booleanPreferencesKey("add_radio_browser_tags")
private val sleepTimerCloseAppKey = booleanPreferencesKey("sleep_timer_close_app")
private val sleepTimerPlacementKey = stringPreferencesKey("sleep_timer_placement")
private val sleepTimerAlwaysShowKey = booleanPreferencesKey("sleep_timer_always_show")
private val sleepTimerFreeXKey = floatPreferencesKey("sleep_timer_free_x")
private val sleepTimerFreeYKey = floatPreferencesKey("sleep_timer_free_y")
private val removeTrackingParametersKey = booleanPreferencesKey("remove_tracking_parameters")
private val widgetFollowAppThemeKey = booleanPreferencesKey("widget_follow_app_theme")
private val widgetMarqueeTrackTitleKey = booleanPreferencesKey("widget_marquee_track_title")
private val showAndroidAutoArtworkKey = booleanPreferencesKey("show_android_auto_artwork")
private val playerPanelCollapsedKey = booleanPreferencesKey("player_panel_collapsed")
private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
private val whatsNewSeenVersionCodeKey = longPreferencesKey("whats_new_seen_version_code")
private val releaseWarningSeenIdKey = stringPreferencesKey("release_warning_seen_id")
private val lastMainPageKey = stringPreferencesKey("last_main_page")
private val lastPlayedStationIdKey = stringPreferencesKey("last_played_station_id")
private val recentlyPlayedStationIdsKey = stringPreferencesKey("recently_played_station_ids")
private val stationSortKey = stringPreferencesKey("station_sort")
private val favoriteSortKey = stringPreferencesKey("favorite_sort")
private val customStationOrderKey = stringPreferencesKey("custom_station_order")
private val customFavoriteOrderKey = stringPreferencesKey("custom_favorite_order")
private val tabOrderKey = stringPreferencesKey("tab_order")
private val playlistsJsonKey = stringPreferencesKey("playlists_json")
private val stationsJsonKey = stringPreferencesKey("stations_json")
private val translationEnabledKey = booleanPreferencesKey("translation_enabled")
private val importedTranslationKey = stringPreferencesKey("imported_translation")

class StationRepository(private val context: Context) {
    @OptIn(UnstableApi::class)
    private val mediaButtonReceiver = ComponentName(context, MediaButtonReceiver::class.java)
    private val keepMediaCardState = MutableStateFlow(
        context.packageManager.getComponentEnabledSetting(mediaButtonReceiver) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    )

    val translationEnabled: Flow<Boolean> = context.stationDataStore.data
        .map { it[translationEnabledKey] ?: false }

    suspend fun loadImportedTranslation(): ImportedTranslation? = withContext(Dispatchers.IO) {
        context.stationDataStore.data.first()[importedTranslationKey]?.let { saved ->
            val json = JSONObject(saved)
            TranslationCodec.decode(json.getString("source"), context.resources)
                .copy(previousLocaleTags = json.getString("previousLocaleTags"))
        }
    }

    suspend fun toggleTranslation(): Boolean {
        val preferences = context.stationDataStore.edit {
            it[translationEnabledKey] = !(it[translationEnabledKey] ?: false)
        }
        return preferences[translationEnabledKey] == true
    }

    suspend fun importTranslation(translation: ImportedTranslation, previousLocaleTags: String): ImportedTranslation {
        var installed = translation
        context.stationDataStore.edit {
            val previous = it[importedTranslationKey]?.let { saved -> JSONObject(saved).getString("previousLocaleTags") }
                ?: previousLocaleTags
            installed = translation.copy(previousLocaleTags = previous)
            it[importedTranslationKey] = JSONObject()
                .put("source", translation.source)
                .put("previousLocaleTags", previous)
                .toString()
        }
        return installed
    }

    suspend fun clearImportedTranslation() {
        context.stationDataStore.edit { it.remove(importedTranslationKey) }
    }

    val appVolume: Flow<Float> = context.stationDataStore.data
        .map { preferences -> preferences[appVolumeKey] ?: DEFAULT_APP_VOLUME }

    val themeMode: Flow<ThemeMode> = context.stationDataStore.data
        .map { preferences ->
            preferences[themeModeKey]
                ?.let { savedMode -> ThemeMode.entries.firstOrNull { it.name == savedMode } }
                ?: ThemeMode.System
        }

    val stationDividers: Flow<StationDividerStyle> = context.stationDataStore.data
        .map { preferences ->
            StationDividerStyle.entries.firstOrNull { it.name == preferences[stationDividersKey] }
                ?: StationDividerStyle.Inset
        }

    val showPlaybackIndicator: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showPlaybackIndicatorKey] ?: true }

    val showFavoriteButtons: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showFavoriteButtonsKey] ?: true }

    val showEmptyFavoritesTab: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showEmptyFavoritesTabKey] ?: true }

    val confirmStationDeletion: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[confirmStationDeletionKey] ?: true }

    val showStationArtwork: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showStationArtworkKey] ?: true }

    val rememberLastStation: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[rememberLastStationKey] ?: true }

    val stopServiceAfterPauseMinutes: Flow<Int> = context.stationDataStore.data
        .map { preferences -> preferences[stopServiceAfterPauseMinutesKey] ?: DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES }

    val keepMediaCard = keepMediaCardState.asStateFlow()

    val showBitrateInControlPanel: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showBitrateInControlPanelKey] ?: true }

    val showUnavailableBitrate: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showUnavailableBitrateKey] ?: false }

    val marqueeTrackTitle: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[marqueeTrackTitleKey] ?: true }

    val autoExpandPlayerPanelOnPlayback: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[autoExpandPlayerPanelOnPlaybackKey] ?: true }

    val collapsePlayerPanelInSearch: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[collapsePlayerPanelInSearchKey] ?: true }

    val addRadioBrowserTags: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[addRadioBrowserTagsKey] ?: true }

    val sleepTimerCloseApp: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[sleepTimerCloseAppKey] ?: false }

    val sleepTimerPlacement: Flow<SleepTimerPlacement> = context.stationDataStore.data
        .map { preferences ->
            SleepTimerPlacement.entries.firstOrNull { it.name == preferences[sleepTimerPlacementKey] }
                ?: SleepTimerPlacement.PanelRight
        }

    val sleepTimerAlwaysShow: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[sleepTimerAlwaysShowKey] ?: false }

    val sleepTimerFreePosition: Flow<SleepTimerFreePosition> = context.stationDataStore.data
        .map { preferences ->
            val defaults = SleepTimerFreePosition()
            SleepTimerFreePosition(
                xFraction = preferences[sleepTimerFreeXKey]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
                    ?: defaults.xFraction,
                yFraction = preferences[sleepTimerFreeYKey]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
                    ?: defaults.yFraction,
            )
        }

    val removeTrackingParameters: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[removeTrackingParametersKey] ?: true }

    val widgetFollowAppTheme: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[widgetFollowAppThemeKey] ?: false }

    val widgetMarqueeTrackTitle: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[widgetMarqueeTrackTitleKey] ?: true }

    val showAndroidAutoArtwork: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showAndroidAutoArtworkKey] ?: true }

    val playerPanelCollapsed: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[playerPanelCollapsedKey] ?: false }

    val onboardingCompleted: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[onboardingCompletedKey] ?: false }

    val whatsNewSeenVersionCode: Flow<Long> = context.stationDataStore.data
        .map { preferences -> preferences[whatsNewSeenVersionCodeKey] ?: 0L }

    val releaseWarningSeenId: Flow<String> = context.stationDataStore.data
        .map { preferences -> preferences[releaseWarningSeenIdKey].orEmpty() }

    val stations: Flow<List<Station>> = context.stationDataStore.data
        .map { preferences -> decodeStations(preferences[stationsJsonKey].orEmpty()) }

    val tabOrder: Flow<List<String>> = context.stationDataStore.data
        .map { decodeStringList(it[tabOrderKey]) }

    val playlists: Flow<List<Playlist>> = context.stationDataStore.data
        .map { decodePlaylists(it[playlistsJsonKey]) }

    val lastPlayedStationId: Flow<String?> = context.stationDataStore.data
        .map { preferences -> preferences[lastPlayedStationIdKey]?.takeIf { it.isNotBlank() } }

    val recentlyPlayedStationIds: Flow<List<String>> = context.stationDataStore.data
        .map { preferences -> decodeStringList(preferences[recentlyPlayedStationIdsKey]) }

    val lastMainPage: Flow<String?> = context.stationDataStore.data
        .map { preferences -> preferences[lastMainPageKey]?.takeIf { it.isNotBlank() } }

    val stationSortState: Flow<StationSortState> = context.stationDataStore.data
        .map { preferences ->
            decodeStationSortState(preferences[stationSortKey])
        }

    val favoriteSortState: Flow<StationSortState> = context.stationDataStore.data
        .map { preferences -> decodeStationSortState(preferences[favoriteSortKey]) }

    val customStationOrder: Flow<List<String>> = context.stationDataStore.data
        .map { preferences -> decodeStringList(preferences[customStationOrderKey]) }

    val customFavoriteOrder: Flow<List<String>> = context.stationDataStore.data
        .map { preferences -> decodeStringList(preferences[customFavoriteOrderKey]) }

    suspend fun saveAppVolume(volume: Float) {
        context.stationDataStore.edit { preferences ->
            preferences[appVolumeKey] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.stationDataStore.edit { preferences ->
            preferences[themeModeKey] = themeMode.name
        }
    }

    suspend fun saveStationDividers(style: StationDividerStyle) {
        context.stationDataStore.edit { preferences ->
            preferences[stationDividersKey] = style.name
        }
    }

    suspend fun saveShowPlaybackIndicator(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showPlaybackIndicatorKey] = show
        }
    }

    suspend fun saveShowFavoriteButtons(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showFavoriteButtonsKey] = show
        }
    }

    suspend fun saveShowEmptyFavoritesTab(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showEmptyFavoritesTabKey] = show
        }
    }

    suspend fun saveConfirmStationDeletion(confirm: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[confirmStationDeletionKey] = confirm
        }
    }

    suspend fun saveShowStationArtwork(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showStationArtworkKey] = show
        }
    }

    suspend fun saveRememberLastStation(remember: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[rememberLastStationKey] = remember
        }
    }

    suspend fun saveStopServiceAfterPauseMinutes(minutes: Int) {
        context.stationDataStore.edit { preferences ->
            preferences[stopServiceAfterPauseMinutesKey] = minutes
        }
    }

    fun saveKeepMediaCard(keep: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            mediaButtonReceiver,
            if (keep) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        keepMediaCardState.value = keep
    }

    suspend fun saveShowBitrateInControlPanel(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showBitrateInControlPanelKey] = show
        }
    }

    suspend fun saveShowUnavailableBitrate(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showUnavailableBitrateKey] = show
        }
    }

    suspend fun saveMarqueeTrackTitle(marquee: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[marqueeTrackTitleKey] = marquee
        }
    }

    suspend fun saveAutoExpandPlayerPanelOnPlayback(autoExpand: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[autoExpandPlayerPanelOnPlaybackKey] = autoExpand
        }
    }

    suspend fun saveCollapsePlayerPanelInSearch(collapse: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[collapsePlayerPanelInSearchKey] = collapse
        }
    }

    suspend fun saveAddRadioBrowserTags(add: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[addRadioBrowserTagsKey] = add
        }
    }

    suspend fun saveSleepTimerCloseApp(closeApp: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[sleepTimerCloseAppKey] = closeApp
        }
    }

    suspend fun saveSleepTimerPlacement(placement: SleepTimerPlacement) {
        context.stationDataStore.edit { preferences ->
            preferences[sleepTimerPlacementKey] = placement.name
        }
    }

    suspend fun saveSleepTimerAlwaysShow(alwaysShow: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[sleepTimerAlwaysShowKey] = alwaysShow
        }
    }

    suspend fun saveSleepTimerFreePosition(position: SleepTimerFreePosition) {
        if (!position.xFraction.isFinite() || !position.yFraction.isFinite()) return
        context.stationDataStore.edit { preferences ->
            preferences[sleepTimerFreeXKey] = position.xFraction.coerceIn(0f, 1f)
            preferences[sleepTimerFreeYKey] = position.yFraction.coerceIn(0f, 1f)
        }
    }

    suspend fun saveRemoveTrackingParameters(remove: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[removeTrackingParametersKey] = remove
        }
    }

    suspend fun saveWidgetFollowAppTheme(follow: Boolean) {
        context.stationDataStore.edit { preferences -> preferences[widgetFollowAppThemeKey] = follow }
    }

    suspend fun saveWidgetMarqueeTrackTitle(marquee: Boolean) {
        context.stationDataStore.edit { preferences -> preferences[widgetMarqueeTrackTitleKey] = marquee }
    }

    suspend fun saveShowAndroidAutoArtwork(show: Boolean) {
        context.stationDataStore.edit { preferences -> preferences[showAndroidAutoArtworkKey] = show }
    }

    suspend fun savePlayerPanelCollapsed(collapsed: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[playerPanelCollapsedKey] = collapsed
        }
    }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }

    suspend fun saveWhatsNewSeenVersionCode(versionCode: Long) {
        context.stationDataStore.edit { preferences ->
            preferences[whatsNewSeenVersionCodeKey] = versionCode
        }
    }

    suspend fun saveReleaseWarningSeenId(id: String) {
        context.stationDataStore.edit { preferences ->
            preferences[releaseWarningSeenIdKey] = id
        }
    }

    suspend fun saveLastPlayedStationId(stationId: String) {
        context.stationDataStore.edit { preferences ->
            preferences[lastPlayedStationIdKey] = stationId
        }
    }

    suspend fun recordStationPlayed(stationId: String) {
        context.stationDataStore.edit { preferences ->
            val recent = decodeStringList(preferences[recentlyPlayedStationIdsKey])
            preferences[recentlyPlayedStationIdsKey] = encodeStringList(
                (listOf(stationId) + recent.filter { it != stationId }).take(50),
            )
        }
    }

    suspend fun saveLastMainPage(pageName: String) {
        context.stationDataStore.edit { preferences ->
            preferences[lastMainPageKey] = pageName
        }
    }

    suspend fun saveStationSortState(sortState: StationSortState) {
        context.stationDataStore.edit { preferences ->
            preferences[stationSortKey] = "${sortState.mode.name}:${sortState.ascending}"
        }
    }

    suspend fun saveFavoriteSortState(sortState: StationSortState) {
        context.stationDataStore.edit { preferences ->
            preferences[favoriteSortKey] = "${sortState.mode.name}:${sortState.ascending}"
        }
    }

    suspend fun saveCustomStationOrder(stationIds: List<String>) {
        context.stationDataStore.edit { preferences ->
            preferences[customStationOrderKey] = encodeStringList(stationIds)
        }
    }

    suspend fun saveCustomFavoriteOrder(stationIds: List<String>) {
        context.stationDataStore.edit { preferences ->
            preferences[customFavoriteOrderKey] = encodeStringList(stationIds)
        }
    }

    suspend fun exportLibrary(): StationExportData = withContext(Dispatchers.IO) {
        val preferences = context.stationDataStore.data.first()
        StationExportData(
            stations = decodeStations(preferences[stationsJsonKey].orEmpty()),
            stationSortState = decodeStationSortState(preferences[stationSortKey]),
            favoriteSortState = decodeStationSortState(preferences[favoriteSortKey]),
            customStationOrder = decodeStringList(preferences[customStationOrderKey]),
            customFavoriteOrder = decodeStringList(preferences[customFavoriteOrderKey]),
            collections = LibraryCollections(
                playlists = decodePlaylists(preferences[playlistsJsonKey]),
                tabOrder = decodeStringList(preferences[tabOrderKey]),
            ),
        )
    }

    suspend fun importLibrary(data: StationExportData, mode: StationImportMode): StationImportResult {
        var result: StationImportResult? = null
        context.stationDataStore.edit { preferences ->
            val imported = StationExportCodec.buildImportResult(
                importedData = data,
                currentStations = decodeStations(preferences[stationsJsonKey].orEmpty()),
                currentStationSortState = decodeStationSortState(preferences[stationSortKey]),
                currentFavoriteSortState = decodeStationSortState(preferences[favoriteSortKey]),
                currentCustomStationOrder = decodeStringList(preferences[customStationOrderKey]),
                currentCustomFavoriteOrder = decodeStringList(preferences[customFavoriteOrderKey]),
                currentCollections = LibraryCollections(
                    playlists = decodePlaylists(preferences[playlistsJsonKey]),
                    tabOrder = decodeStringList(preferences[tabOrderKey]),
                ),
                mode = mode,
            )
            preferences[stationsJsonKey] = encodeStations(imported.stations)
            preferences[stationSortKey] = "${imported.stationSortState.mode.name}:${imported.stationSortState.ascending}"
            preferences[favoriteSortKey] = "${imported.favoriteSortState.mode.name}:${imported.favoriteSortState.ascending}"
            preferences[customStationOrderKey] = encodeStringList(imported.customStationOrder)
            preferences[customFavoriteOrderKey] = encodeStringList(imported.customFavoriteOrder)
            preferences[playlistsJsonKey] = encodePlaylists(imported.collections.playlists)
            val tabs = orderedTabs(imported.collections.playlists, imported.collections.tabOrder).map { it.name }
            preferences[tabOrderKey] = encodeStringList(tabs)
            if (preferences[lastMainPageKey] !in tabs) preferences[lastMainPageKey] = "Stations"
            val ids = imported.stations.mapTo(mutableSetOf()) { it.id }
            if (preferences[lastPlayedStationIdKey] !in ids) preferences.remove(lastPlayedStationIdKey)
            preferences[recentlyPlayedStationIdsKey] = encodeStringList(
                decodeStringList(preferences[recentlyPlayedStationIdsKey]).filter(ids::contains),
            )
            result = imported
        }
        return requireNotNull(result)
    }

    suspend fun saveTabOrder(tabIds: List<String>) {
        context.stationDataStore.edit { preferences ->
            val playlists = decodePlaylists(preferences[playlistsJsonKey])
            preferences[tabOrderKey] = encodeStringList(orderedTabs(playlists, tabIds).map { it.name })
        }
    }

    suspend fun createPlaylist(title: String): String {
        val id = UUID.randomUUID().toString()
        updatePlaylists { it + Playlist(id = id, title = title.trim()) }
        return id
    }

    suspend fun updatePlaylist(id: String, transform: (Playlist) -> Playlist) {
        updatePlaylists { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    suspend fun deletePlaylist(id: String) {
        updatePlaylists { list -> list.filterNot { it.id == id } }
    }

    suspend fun saveStations(stations: List<Station>) {
        context.stationDataStore.edit { preferences ->
            preferences[stationsJsonKey] = encodeStations(stations)
            val ids = stations.mapTo(mutableSetOf()) { it.id }
            preferences[playlistsJsonKey] = encodePlaylists(decodePlaylists(preferences[playlistsJsonKey]).map {
                it.copy(stationIds = it.stationIds.filter(ids::contains))
            })
        }
    }

    suspend fun clearLibrary() {
        context.stationDataStore.edit { preferences ->
            preferences[stationsJsonKey] = encodeStations(emptyList())
            preferences.remove(playlistsJsonKey)
            preferences.remove(tabOrderKey)
            preferences.remove(stationSortKey)
            preferences.remove(favoriteSortKey)
            preferences[lastMainPageKey] = "Stations"
            preferences[customStationOrderKey] = encodeStringList(emptyList())
            preferences[customFavoriteOrderKey] = encodeStringList(emptyList())
            preferences.remove(lastPlayedStationIdKey)
            preferences.remove(recentlyPlayedStationIdsKey)
        }
    }

    suspend fun saveStationImageUrl(stationId: String, imageUrl: String) {
        context.stationDataStore.edit { preferences ->
            val stations = decodeStations(preferences[stationsJsonKey].orEmpty())
            val index = stations.indexOfFirst { it.id == stationId }
            if (index == -1) return@edit
            val nextStations = stations.toMutableList().also { list ->
                list[index] = list[index].copy(imageUrl = imageUrl)
            }
            preferences[stationsJsonKey] = encodeStations(nextStations)
        }
    }

    private suspend fun updatePlaylists(transform: (List<Playlist>) -> List<Playlist>) {
        context.stationDataStore.edit { preferences ->
            val playlists = transform(decodePlaylists(preferences[playlistsJsonKey]))
            preferences[playlistsJsonKey] = encodePlaylists(playlists)
            preferences[tabOrderKey] = encodeStringList(
                orderedTabs(playlists, decodeStringList(preferences[tabOrderKey])).map { it.name },
            )
        }
    }

    private fun encodePlaylists(playlists: List<Playlist>): String = JSONArray().apply {
        playlists.forEach { playlist ->
            put(JSONObject()
                .put("id", playlist.id)
                .put("title", playlist.title)
                .put("stationIds", JSONArray(playlist.stationIds))
                .put("sort", "${playlist.sortState.mode.name}:${playlist.sortState.ascending}"))
        }
    }.toString()

    private fun decodePlaylists(saved: String?): List<Playlist> {
        if (saved.isNullOrBlank()) return emptyList()
        return runCatching {
            val items = JSONArray(saved)
            List(items.length()) { index ->
                val item = items.getJSONObject(index)
                Playlist(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    stationIds = decodeStringList(item.optJSONArray("stationIds")?.toString()).distinct(),
                    sortState = decodeStationSortState(item.optString("sort")),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeStations(stations: List<Station>): String {
        val items = JSONArray()
        stations.forEach { station ->
            items.put(
                JSONObject()
                    .put("id", station.id)
                    .put("title", station.title)
                    .put("streamUrl", station.streamUrl)
                    .put("tags", JSONArray(station.tags))
                    .put("imageUrl", station.imageUrl.orEmpty())
                    .put("isFavorite", station.isFavorite)
                    .put("dateAdded", station.dateAdded),
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
                    val title = item.optString("title").trim()
                    val streamUrl = item.optString("streamUrl").trim()
                    if (title.isNotEmpty() && streamUrl.isNotEmpty()) {
                        add(
                            Station(
                                id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                                title = title,
                                streamUrl = streamUrl,
                                tags = decodeTags(item.optJSONArray("tags")),
                                imageUrl = item.optString("imageUrl").trim().takeIf { it.isNotBlank() },
                                isFavorite = item.optBoolean("isFavorite", false),
                                dateAdded = item.optString("dateAdded")
                                    .takeIf { it.isValidInstant() }
                                    ?: Instant.now().toString(),
                            ),
                        )
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun decodeTags(tagsJson: JSONArray?): List<String> {
        if (tagsJson == null) {
            return emptyList()
        }
        return buildList {
            repeat(tagsJson.length()) { index ->
                tagsJson.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private fun String.isValidInstant(): Boolean {
        return runCatching { Instant.parse(this) }.isSuccess
    }

    private fun encodeStringList(items: List<String>): String {
        val json = JSONArray()
        items.forEach { item -> json.put(item) }
        return json.toString()
    }

    private fun decodeStringList(savedItems: String?): List<String> {
        if (savedItems.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            val items = JSONArray(savedItems)
            buildList {
                repeat(items.length()) { index ->
                    items.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun decodeStationSortState(savedState: String?): StationSortState {
        val parts = savedState.orEmpty().split(":")
        val mode = parts.getOrNull(0)
            ?.let { savedMode -> StationSortMode.entries.firstOrNull { it.name == savedMode } }
            ?: StationSortMode.DateAdded
        val ascending = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
        return StationSortState(mode = mode, ascending = ascending)
    }
}
