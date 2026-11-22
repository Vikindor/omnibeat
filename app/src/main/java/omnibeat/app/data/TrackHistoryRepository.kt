package omnibeat.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import omnibeat.app.model.TrackHistoryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.time.LocalDateTime

const val TRACK_HISTORY_DISABLED = 0
const val DEFAULT_TRACK_HISTORY_LIMIT = 50
val TRACK_HISTORY_LIMITS = listOf(TRACK_HISTORY_DISABLED, 30, 50, 100)

private val Context.trackHistoryDataStore by preferencesDataStore(name = "track_history")
private val historyLimitKey = intPreferencesKey("limit")
private val historyCopyStationKey = booleanPreferencesKey("copy_station")
private val historyMutex = Mutex()
private val historyChanges = MutableStateFlow(0L)

class TrackHistoryRepository(private val context: Context) {
    private val historyFile = AtomicFile(File(context.cacheDir, "track_history.json"))

    val limit: Flow<Int> = context.trackHistoryDataStore.data
        .map { preferences -> preferences[historyLimitKey] ?: DEFAULT_TRACK_HISTORY_LIMIT }

    val copyStation: Flow<Boolean> = context.trackHistoryDataStore.data
        .map { preferences -> preferences[historyCopyStationKey] ?: false }

    val entries: Flow<List<TrackHistoryEntry>> = historyChanges.map {
        historyMutex.withLock { readEntries() }
    }.flowOn(Dispatchers.IO)

    suspend fun saveLimit(limit: Int) = withContext(Dispatchers.IO) {
        require(limit in TRACK_HISTORY_LIMITS)
        historyMutex.withLock {
            context.trackHistoryDataStore.edit { preferences ->
                preferences[historyLimitKey] = limit
            }
            if (limit != TRACK_HISTORY_DISABLED) {
                writeEntries(readEntries().take(limit))
                historyChanges.update { it + 1L }
            }
        }
    }

    suspend fun saveCopyStation(copyStation: Boolean) {
        context.trackHistoryDataStore.edit { preferences ->
            preferences[historyCopyStationKey] = copyStation
        }
    }

    suspend fun record(trackText: String, stationTitle: String) = withContext(Dispatchers.IO) {
        historyMutex.withLock {
            val limit = context.trackHistoryDataStore.data.first()[historyLimitKey] ?: DEFAULT_TRACK_HISTORY_LIMIT
            if (limit == TRACK_HISTORY_DISABLED) return@withLock false
            val current = readEntries()
            val id = (current.maxOfOrNull { it.id } ?: 0L) + 1L
            val entry = TrackHistoryEntry(id, trackText, stationTitle, LocalDateTime.now())
            writeEntries((listOf(entry) + current).take(limit))
            historyChanges.update { it + 1L }
            true
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        historyMutex.withLock {
            writeEntries(emptyList())
            historyChanges.update { it + 1L }
        }
    }

    private fun readEntries(): List<TrackHistoryEntry> {
        return try {
            historyFile.openRead().bufferedReader(Charsets.UTF_8).use { decodeEntries(it.readText()) }
        } catch (_: FileNotFoundException) {
            emptyList()
        }
    }

    private fun writeEntries(entries: List<TrackHistoryEntry>) {
        var stream: FileOutputStream? = null
        try {
            stream = historyFile.startWrite()
            stream.write(encodeEntries(entries).toByteArray(Charsets.UTF_8))
            historyFile.finishWrite(stream)
        } catch (error: Exception) {
            historyFile.failWrite(stream)
            throw error
        }
    }

    private fun decodeEntries(json: String?): List<TrackHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                TrackHistoryEntry(
                    id = item.getLong("id"),
                    trackText = item.getString("trackText"),
                    stationTitle = item.getString("stationTitle"),
                    createdAt = LocalDateTime.parse(item.getString("createdAt")),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeEntries(entries: List<TrackHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("trackText", entry.trackText)
                    .put("stationTitle", entry.stationTitle)
                    .put("createdAt", entry.createdAt.toString()),
            )
        }
        return array.toString()
    }
}
