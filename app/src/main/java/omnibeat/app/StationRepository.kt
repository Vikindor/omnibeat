package omnibeat.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

private val Context.stationDataStore by preferencesDataStore(name = "stations")
private const val DEFAULT_APP_VOLUME = 0.75f
private val appVolumeKey = floatPreferencesKey("app_volume")
private val stationsJsonKey = stringPreferencesKey("stations_json")

class StationRepository(private val context: Context) {
    val appVolume: Flow<Float> = context.stationDataStore.data
        .map { preferences -> preferences[appVolumeKey] ?: DEFAULT_APP_VOLUME }

    val stations: Flow<List<Station>> = context.stationDataStore.data
        .map { preferences -> decodeStations(preferences[stationsJsonKey].orEmpty()) }

    suspend fun saveAppVolume(volume: Float) {
        context.stationDataStore.edit { preferences ->
            preferences[appVolumeKey] = volume.coerceIn(0f, 1f)
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
                    .put("name", station.name)
                    .put("formatLabel", station.formatLabel)
                    .put("sourceUrl", station.sourceUrl),
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
                    val name = item.optString("name").trim()
                    val sourceUrl = item.optString("sourceUrl").trim()
                    if (name.isNotEmpty() && sourceUrl.isNotEmpty()) {
                        add(
                            Station(
                                id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                                name = name,
                                formatLabel = item.optString("formatLabel", "Custom stream"),
                                sourceUrl = sourceUrl,
                            ),
                        )
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }
}
