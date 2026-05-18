package omnibeat.app.data

import omnibeat.app.R

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import omnibeat.app.model.Station
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

private val Context.stationDataStore by preferencesDataStore(name = "stations")
private const val DEFAULT_APP_VOLUME = 0.75f
private val appVolumeKey = floatPreferencesKey("app_volume")
private val lastMainPageKey = stringPreferencesKey("last_main_page")
private val lastPlayedStationIdKey = stringPreferencesKey("last_played_station_id")
private val stationSortKey = stringPreferencesKey("station_sort")
private val customStationOrderKey = stringPreferencesKey("custom_station_order")
private val customFavoriteOrderKey = stringPreferencesKey("custom_favorite_order")
private val stationsJsonKey = stringPreferencesKey("stations_json")

class StationRepository(private val context: Context) {
    val appVolume: Flow<Float> = context.stationDataStore.data
        .map { preferences -> preferences[appVolumeKey] ?: DEFAULT_APP_VOLUME }

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
