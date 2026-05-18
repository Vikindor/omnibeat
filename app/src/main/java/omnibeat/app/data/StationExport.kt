package omnibeat.app.data

import omnibeat.app.model.STATION_STREAM_URL_MAX_LENGTH
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH
import omnibeat.app.model.Station
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.UUID

private const val EXPORT_SCHEMA_VERSION = 1
private const val EXPORT_APP_NAME = "OmniBeat"

data class StationExportData(
    val stations: List<Station>,
    val sortState: StationSortState = StationSortState(),
    val customStationOrder: List<String> = emptyList(),
    val customFavoriteOrder: List<String> = emptyList(),
    val exportedAt: String = Instant.now().toString(),
)

enum class StationImportMode {
    Merge,
    Replace,
}

data class StationImportResult(
    val stations: List<Station>,
    val sortState: StationSortState,
    val customStationOrder: List<String>,
    val customFavoriteOrder: List<String>,
)

object StationExportCodec {
    fun encode(data: StationExportData): String {
        return JSONObject()
            .put("schemaVersion", EXPORT_SCHEMA_VERSION)
            .put("app", EXPORT_APP_NAME)
            .put("exportedAt", data.exportedAt)
            .put("stations", encodeStations(data.stations))
            .put(
                "sort",
                JSONObject()
                    .put("mode", data.sortState.mode.name)
                    .put("ascending", data.sortState.ascending)
                    .put("customStationOrder", JSONArray(data.customStationOrder))
                    .put("customFavoriteOrder", JSONArray(data.customFavoriteOrder)),
            )
            .toString(2)
    }

    fun decode(json: String): StationExportData {
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion == EXPORT_SCHEMA_VERSION) {
            "Unsupported export schema version: $schemaVersion"
        }

        val stations = decodeStations(root.optJSONArray("stations"))
        val sort = root.optJSONObject("sort")
        return StationExportData(
            stations = stations,
            sortState = decodeSortState(sort),
            customStationOrder = sanitizeOrder(decodeStringList(sort?.optJSONArray("customStationOrder")), stations),
            customFavoriteOrder = sanitizeOrder(decodeStringList(sort?.optJSONArray("customFavoriteOrder")), stations),
            exportedAt = root.optString("exportedAt").takeIf { it.isNotBlank() } ?: Instant.now().toString(),
        )
    }

    fun buildImportResult(
        importedData: StationExportData,
        currentStations: List<Station>,
        currentSortState: StationSortState,
        currentCustomStationOrder: List<String>,
        currentCustomFavoriteOrder: List<String>,
        mode: StationImportMode,
    ): StationImportResult {
        return when (mode) {
            StationImportMode.Replace -> StationImportResult(
                stations = importedData.stations,
                sortState = importedData.sortState,
                customStationOrder = sanitizeOrder(importedData.customStationOrder, importedData.stations),
                customFavoriteOrder = sanitizeOrder(importedData.customFavoriteOrder, importedData.stations),
            )
            StationImportMode.Merge -> mergeImport(
                importedData = importedData,
                currentStations = currentStations,
                currentSortState = currentSortState,
                currentCustomStationOrder = currentCustomStationOrder,
                currentCustomFavoriteOrder = currentCustomFavoriteOrder,
            )
        }
    }

    private fun mergeImport(
        importedData: StationExportData,
        currentStations: List<Station>,
        currentSortState: StationSortState,
        currentCustomStationOrder: List<String>,
        currentCustomFavoriteOrder: List<String>,
    ): StationImportResult {
        val usedIds = currentStations.map { it.id }.toMutableSet()
        val nextStations = currentStations.toMutableList()
        val newStationIds = mutableListOf<String>()

        importedData.stations.forEach { importedStation ->
            val existingIndex = nextStations.indexOfFirst {
                it.streamUrl.equals(importedStation.streamUrl, ignoreCase = true)
            }
            if (existingIndex >= 0) {
                val existingStation = nextStations[existingIndex]
                nextStations[existingIndex] = existingStation.copy(
                    title = importedStation.title,
                    tags = importedStation.tags,
                    isFavorite = importedStation.isFavorite,
                )
            } else {
                val nextId = importedStation.id.takeIf { it !in usedIds } ?: UUID.randomUUID().toString()
                usedIds.add(nextId)
                newStationIds += nextId
                nextStations += importedStation.copy(id = nextId)
            }
        }

        return StationImportResult(
            stations = nextStations,
            sortState = currentSortState,
            customStationOrder = appendMissingIds(currentCustomStationOrder, newStationIds, nextStations),
            customFavoriteOrder = appendMissingIds(
                currentCustomFavoriteOrder,
                newStationIds.filter { newStationId ->
                    nextStations.firstOrNull { it.id == newStationId }?.isFavorite == true
                },
                nextStations,
            ),
        )
    }

    private fun encodeStations(stations: List<Station>): JSONArray {
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
        return items
    }

    private fun decodeStations(stationsJson: JSONArray?): List<Station> {
        if (stationsJson == null) {
            return emptyList()
        }
        return buildList {
            repeat(stationsJson.length()) { index ->
                val item = stationsJson.optJSONObject(index) ?: return@repeat
                val streamUrl = item.optString("streamUrl").trim().take(STATION_STREAM_URL_MAX_LENGTH)
                if (streamUrl.isBlank()) {
                    return@repeat
                }
                val title = item.optString("title")
                    .trim()
                    .ifBlank { streamUrl.take(STATION_TITLE_MAX_LENGTH) }
                    .take(STATION_TITLE_MAX_LENGTH)
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

    private fun decodeTags(tagsJson: JSONArray?): List<String> {
        if (tagsJson == null) {
            return emptyList()
        }
        return buildList {
            repeat(tagsJson.length()) { index ->
                tagsJson.optString(index)
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(::add)
            }
        }.distinctBy { it.lowercase() }
    }

    private fun decodeSortState(sortJson: JSONObject?): StationSortState {
        val mode = sortJson
            ?.optString("mode")
            ?.let { savedMode -> StationSortMode.entries.firstOrNull { it.name == savedMode } }
            ?: StationSortMode.DateAdded
        val ascending = sortJson?.optBoolean("ascending", false) ?: false
        return StationSortState(mode = mode, ascending = ascending)
    }

    private fun decodeStringList(items: JSONArray?): List<String> {
        if (items == null) {
            return emptyList()
        }
        return buildList {
            repeat(items.length()) { index ->
                items.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun sanitizeOrder(order: List<String>, stations: List<Station>): List<String> {
        val stationIds = stations.map { it.id }.toSet()
        return order.filter { it in stationIds }.distinct()
    }

    private fun appendMissingIds(order: List<String>, idsToAppend: List<String>, stations: List<Station>): List<String> {
        val stationIds = stations.map { it.id }.toSet()
        val sanitizedOrder = order.filter { it in stationIds }.distinct()
        return sanitizedOrder + idsToAppend.filter { it in stationIds && it !in sanitizedOrder }.distinct()
    }

    private fun String.isValidInstant(): Boolean {
        return runCatching { Instant.parse(this) }.isSuccess
    }
}

object SimpleStationTextCodec {
    fun encode(stations: List<Station>): String {
        return stations.joinToString("\n\n") { station ->
            buildList {
                add(station.title)
                add(station.streamUrl)
                if (station.tags.isNotEmpty()) {
                    add(station.tags.joinToString(", "))
                }
            }.joinToString("\n")
        } + "\n"
    }

    fun decode(text: String): StationExportData {
        val stations = text
            .trim()
            .split(Regex("""\r?\n\s*\r?\n"""))
            .filter { it.isNotBlank() }
            .mapIndexed { index, block -> decodeStation(block, index) }

        require(stations.isNotEmpty()) {
            "No stations found in TXT file"
        }

        return StationExportData(stations = stations)
    }

    private fun decodeStation(block: String, index: Int): Station {
        val lines = block
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        require(lines.size in 2..3) {
            "Station ${index + 1} must contain 2 or 3 lines"
        }

        val streamUrl = lines[1].take(STATION_STREAM_URL_MAX_LENGTH)
        require(streamUrl.isNotBlank()) {
            "Station ${index + 1} has no stream URL"
        }
        require(streamUrl.isValidStreamUrl()) {
            "Station ${index + 1} has invalid stream URL"
        }

        val title = lines[0]
            .ifBlank { streamUrl.take(STATION_TITLE_MAX_LENGTH) }
            .take(STATION_TITLE_MAX_LENGTH)

        return Station(
            id = UUID.randomUUID().toString(),
            title = title,
            streamUrl = streamUrl,
            tags = lines.getOrNull(2)?.parseTags().orEmpty(),
            imageUrl = null,
            isFavorite = false,
            dateAdded = Instant.now().toString(),
        )
    }

    private fun String.parseTags(): List<String> {
        return split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    private fun String.isValidStreamUrl(): Boolean {
        return runCatching {
            val uri = URI(this)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
