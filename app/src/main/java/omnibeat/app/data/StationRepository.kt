package omnibeat.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import omnibeat.app.model.Station
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import omnibeat.app.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

private val Context.stationDataStore by preferencesDataStore(name = "stations")
private const val DEFAULT_APP_VOLUME = 0.75f
private val appVolumeKey = floatPreferencesKey("app_volume")
private val showStationArtworkKey = booleanPreferencesKey("show_station_artwork")
private val addRadioBrowserTagsKey = booleanPreferencesKey("add_radio_browser_tags")
private val removeTrackingParametersKey = booleanPreferencesKey("remove_tracking_parameters")
private val rememberLastStationKey = booleanPreferencesKey("remember_last_station")
private val showBitrateInControlPanelKey = booleanPreferencesKey("show_bitrate_in_control_panel")
private val showUnavailableBitrateKey = booleanPreferencesKey("show_unavailable_bitrate")
private val marqueeTrackTitleKey = booleanPreferencesKey("marquee_track_title")
private val playerPanelCollapsedKey = booleanPreferencesKey("player_panel_collapsed")
private val autoExpandPlayerPanelOnPlaybackKey = booleanPreferencesKey("auto_expand_player_panel_on_playback")
private val collapsePlayerPanelInSearchKey = booleanPreferencesKey("collapse_player_panel_in_search")
private val showEmptyFavoritesTabKey = booleanPreferencesKey("show_empty_favorites_tab")
private val confirmStationDeletionKey = booleanPreferencesKey("confirm_station_deletion")
private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
private val themeModeKey = stringPreferencesKey("theme_mode")
private val lastMainPageKey = stringPreferencesKey("last_main_page")
private val lastPlayedStationIdKey = stringPreferencesKey("last_played_station_id")
private val stationSortKey = stringPreferencesKey("station_sort")
private val customStationOrderKey = stringPreferencesKey("custom_station_order")
private val customFavoriteOrderKey = stringPreferencesKey("custom_favorite_order")
private val stationsJsonKey = stringPreferencesKey("stations_json")

class StationRepository(private val context: Context) {
    val appVolume: Flow<Float> = context.stationDataStore.data
        .map { preferences -> preferences[appVolumeKey] ?: DEFAULT_APP_VOLUME }

    val showStationArtwork: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showStationArtworkKey] ?: true }

    val addRadioBrowserTags: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[addRadioBrowserTagsKey] ?: true }

    val removeTrackingParameters: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[removeTrackingParametersKey] ?: true }

    val rememberLastStation: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[rememberLastStationKey] ?: true }

    val showBitrateInControlPanel: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showBitrateInControlPanelKey] ?: true }

    val showUnavailableBitrate: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showUnavailableBitrateKey] ?: false }

    val marqueeTrackTitle: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[marqueeTrackTitleKey] ?: true }

    val playerPanelCollapsed: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[playerPanelCollapsedKey] ?: false }

    val autoExpandPlayerPanelOnPlayback: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[autoExpandPlayerPanelOnPlaybackKey] ?: true }

    val collapsePlayerPanelInSearch: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[collapsePlayerPanelInSearchKey] ?: true }

    val showEmptyFavoritesTab: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showEmptyFavoritesTabKey] ?: true }

    val confirmStationDeletion: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[confirmStationDeletionKey] ?: true }

    val onboardingCompleted: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[onboardingCompletedKey] ?: false }

    val themeMode: Flow<ThemeMode> = context.stationDataStore.data
        .map { preferences ->
            preferences[themeModeKey]
                ?.let { savedMode -> ThemeMode.entries.firstOrNull { it.name == savedMode } }
                ?: ThemeMode.System
        }

    val stations: Flow<List<Station>> = context.stationDataStore.data
        .map { preferences -> decodeStations(preferences[stationsJsonKey].orEmpty()) }

    val lastPlayedStationId: Flow<String?> = context.stationDataStore.data
        .map { preferences -> preferences[lastPlayedStationIdKey]?.takeIf { it.isNotBlank() } }

    val lastMainPage: Flow<String?> = context.stationDataStore.data
        .map { preferences -> preferences[lastMainPageKey]?.takeIf { it.isNotBlank() } }

    val stationSortState: Flow<StationSortState> = context.stationDataStore.data
        .map { preferences ->
            decodeStationSortState(preferences[stationSortKey])
        }

    val customStationOrder: Flow<List<String>> = context.stationDataStore.data
        .map { preferences -> decodeStringList(preferences[customStationOrderKey]) }

    val customFavoriteOrder: Flow<List<String>> = context.stationDataStore.data
        .map { preferences -> decodeStringList(preferences[customFavoriteOrderKey]) }

    suspend fun saveAppVolume(volume: Float) {
        context.stationDataStore.edit { preferences ->
            preferences[appVolumeKey] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun saveShowStationArtwork(show: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[showStationArtworkKey] = show
        }
    }

    suspend fun saveAddRadioBrowserTags(add: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[addRadioBrowserTagsKey] = add
        }
    }

    suspend fun saveRemoveTrackingParameters(remove: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[removeTrackingParametersKey] = remove
        }
    }

    suspend fun saveRememberLastStation(remember: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[rememberLastStationKey] = remember
        }
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

    suspend fun savePlayerPanelCollapsed(collapsed: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[playerPanelCollapsedKey] = collapsed
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

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        context.stationDataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.stationDataStore.edit { preferences ->
            preferences[themeModeKey] = themeMode.name
        }
    }

    suspend fun saveLastPlayedStationId(stationId: String) {
        context.stationDataStore.edit { preferences ->
            preferences[lastPlayedStationIdKey] = stationId
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

    suspend fun saveImportedLibrary(importResult: StationImportResult) {
        context.stationDataStore.edit { preferences ->
            preferences[stationsJsonKey] = encodeStations(importResult.stations)
            preferences[stationSortKey] = "${importResult.sortState.mode.name}:${importResult.sortState.ascending}"
            preferences[customStationOrderKey] = encodeStringList(importResult.customStationOrder)
            preferences[customFavoriteOrderKey] = encodeStringList(importResult.customFavoriteOrder)
        }
    }

    suspend fun saveStations(stations: List<Station>) {
        context.stationDataStore.edit { preferences ->
            preferences[stationsJsonKey] = encodeStations(stations)
        }
    }

    suspend fun clearLibrary() {
        context.stationDataStore.edit { preferences ->
            preferences[stationsJsonKey] = encodeStations(emptyList())
            preferences[customStationOrderKey] = encodeStringList(emptyList())
            preferences[customFavoriteOrderKey] = encodeStringList(emptyList())
            preferences.remove(lastPlayedStationIdKey)
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
