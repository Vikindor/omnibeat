package omnibeat.app.data

import omnibeat.app.R

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
import java.nio.charset.StandardCharsets
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
private val showEmptyFavoritesTabKey = booleanPreferencesKey("show_empty_favorites_tab")
private val confirmStationDeletionKey = booleanPreferencesKey("confirm_station_deletion")
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
        .map { preferences -> preferences[removeTrackingParametersKey] ?: false }

    val rememberLastStation: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[rememberLastStationKey] ?: true }

    val showBitrateInControlPanel: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showBitrateInControlPanelKey] ?: true }

    val showUnavailableBitrate: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showUnavailableBitrateKey] ?: false }

    val marqueeTrackTitle: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[marqueeTrackTitleKey] ?: true }

    val showEmptyFavoritesTab: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[showEmptyFavoritesTabKey] ?: true }

    val confirmStationDeletion: Flow<Boolean> = context.stationDataStore.data
        .map { preferences -> preferences[confirmStationDeletionKey] ?: true }

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

    suspend fun seedTestStationsForPrototype() {
        context.stationDataStore.edit { preferences ->
            if (preferences[stationsJsonKey].isNullOrBlank()) {
                val stationsJson = context.resources
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
                                dateAdded = item.getString("dateAdded"),
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
