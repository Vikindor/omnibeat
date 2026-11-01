package omnibeat.app.data

import omnibeat.app.model.STATION_STREAM_URL_MAX_LENGTH
import omnibeat.app.model.STATION_TITLE_MAX_LENGTH
import omnibeat.app.model.Playlist
import omnibeat.app.model.Station
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState
import omnibeat.app.model.orderedTabs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.UUID

private const val EXPORT_SCHEMA_VERSION = 2
private const val EXPORT_APP_NAME = "OmniBeat"

data class LibraryCollections(
    val playlists: List<Playlist>,
    val tabOrder: List<String>,
)

data class StationExportData(
    val stations: List<Station>,
    val stationSortState: StationSortState = StationSortState(),
    val favoriteSortState: StationSortState = StationSortState(),
    val customStationOrder: List<String> = emptyList(),
    val customFavoriteOrder: List<String> = emptyList(),
    val exportedAt: String = Instant.now().toString(),
    val collections: LibraryCollections? = null,
)

enum class StationImportMode {
    Merge,
    Replace,
}

data class StationImportResult(
    val stations: List<Station>,
    val stationSortState: StationSortState,
    val favoriteSortState: StationSortState,
    val customStationOrder: List<String>,
    val customFavoriteOrder: List<String>,
    val collections: LibraryCollections,
)

class InvalidLibraryFormatException(cause: Exception) : IllegalArgumentException("Invalid data format", cause)

object StationExportCodec {
    fun encode(data: StationExportData): String {
        val collections = requireNotNull(data.collections)
        val stationIds = data.stations.mapTo(mutableSetOf()) { it.id }
        val playlists = collections.playlists.map {
            it.copy(stationIds = it.stationIds.filter(stationIds::contains).distinct())
        }
        return JSONObject()
            .put("schemaVersion", EXPORT_SCHEMA_VERSION)
            .put("app", EXPORT_APP_NAME)
            .put("exportedAt", data.exportedAt)
            .put("stations", JSONArray(data.stations.map { station ->
                JSONObject()
                    .put("id", station.id)
                    .put("title", station.title)
                    .put("streamUrl", station.streamUrl)
                    .put("tags", JSONArray(station.tags))
                    .put("imageUrl", station.imageUrl.orEmpty())
                    .put("isFavorite", station.isFavorite)
                    .put("dateAdded", station.dateAdded)
            }))
            .put("lists", JSONObject()
                .put("stations", JSONObject()
                    .put("sort", encodeSort(data.stationSortState))
                    .put("order", JSONArray(data.customStationOrder.filter(stationIds::contains).distinct())))
                .put("favorites", JSONObject()
                    .put("sort", encodeSort(data.favoriteSortState))
                    .put("order", JSONArray(data.customFavoriteOrder.filter(stationIds::contains).distinct()))))
            .put("playlists", JSONArray(playlists.map { playlist ->
                JSONObject()
                    .put("id", playlist.id)
                    .put("title", playlist.title)
                    .put("stationIds", JSONArray(playlist.stationIds))
                    .put("sort", encodeSort(playlist.sortState))
            }))
            .put("tabOrder", JSONArray(orderedTabs(playlists, collections.tabOrder).map { it.name }))
            .toString(2)
    }

    fun decode(json: String): StationExportData {
        try {
            val root = JSONObject(json.removePrefix("\uFEFF"))
            root.requireFields("schemaVersion", "app", "exportedAt", "stations", "lists", "playlists", "tabOrder")
            require(root.get("schemaVersion") == EXPORT_SCHEMA_VERSION)
            require(root.string("app") == EXPORT_APP_NAME)
            val exportedAt = root.string("exportedAt")
            Instant.parse(exportedAt)
            val stations = root.getJSONArray("stations").objects().map { item ->
                item.requireFields("id", "title", "streamUrl", "tags", "imageUrl", "isFavorite", "dateAdded")
                val id = item.id()
                val title = item.string("title").also { require(it.isNotBlank()) }
                val streamUrl = item.string("streamUrl").also { require(it.isNotBlank()) }
                val dateAdded = item.string("dateAdded")
                Instant.parse(dateAdded)
                Station(
                    id = id,
                    title = title,
                    streamUrl = streamUrl,
                    tags = item.getJSONArray("tags").strings(),
                    imageUrl = item.string("imageUrl").takeIf { it.isNotEmpty() },
                    isFavorite = item.boolean("isFavorite"),
                    dateAdded = dateAdded,
                )
            }
            requireUnique(stations.map { it.id })
            val stationIds = stations.mapTo(mutableSetOf()) { it.id }
            val lists = root.getJSONObject("lists")
            lists.requireFields("stations", "favorites")
            val stationList = lists.getJSONObject("stations")
            val favoriteList = lists.getJSONObject("favorites")
            stationList.requireFields("sort", "order")
            favoriteList.requireFields("sort", "order")
            val stationSort = stationList.getJSONObject("sort")
            val favoriteSort = favoriteList.getJSONObject("sort")
            stationSort.requireFields("mode", "ascending")
            favoriteSort.requireFields("mode", "ascending")
            val stationOrder = stationList.getJSONArray("order").references(stationIds)
            val favoriteOrder = favoriteList.getJSONArray("order").references(stationIds)
            val playlists = root.getJSONArray("playlists").objects().map { item ->
                item.requireFields("id", "title", "stationIds", "sort")
                val playlistSort = item.getJSONObject("sort")
                playlistSort.requireFields("mode", "ascending")
                Playlist(
                    id = item.id(),
                    title = item.string("title").also { require(it.isNotBlank()) },
                    stationIds = item.getJSONArray("stationIds").references(stationIds),
                    sortState = decodeSort(playlistSort),
                )
            }
            requireUnique(playlists.map { it.id })
            val tabIds = orderedTabs(playlists, emptyList()).mapTo(mutableSetOf()) { it.name }
            val tabOrder = root.getJSONArray("tabOrder").references(tabIds)
            require(tabOrder.toSet() == tabIds)
            return StationExportData(
                stations = stations,
                stationSortState = decodeSort(stationSort),
                favoriteSortState = decodeSort(favoriteSort),
                customStationOrder = stationOrder,
                customFavoriteOrder = favoriteOrder,
                exportedAt = exportedAt,
                collections = LibraryCollections(playlists, tabOrder),
            )
        } catch (error: Exception) {
            throw InvalidLibraryFormatException(error)
        }
    }

    fun buildImportResult(
        importedData: StationExportData,
        currentStations: List<Station>,
        currentStationSortState: StationSortState,
        currentFavoriteSortState: StationSortState,
        currentCustomStationOrder: List<String>,
        currentCustomFavoriteOrder: List<String>,
        currentCollections: LibraryCollections,
        mode: StationImportMode,
    ): StationImportResult {
        if (mode == StationImportMode.Replace) {
            val ids = importedData.stations.mapTo(mutableSetOf()) { it.id }
            val collections = importedData.collections ?: currentCollections.copy(
                playlists = currentCollections.playlists.map { playlist ->
                    playlist.copy(stationIds = playlist.stationIds.filter(ids::contains))
                },
            )
            return StationImportResult(
                stations = importedData.stations,
                stationSortState = importedData.stationSortState,
                favoriteSortState = importedData.favoriteSortState,
                customStationOrder = importedData.customStationOrder,
                customFavoriteOrder = importedData.customFavoriteOrder,
                collections = collections,
            )
        }

        val nextStations = currentStations.toMutableList()
        val usedIds = currentStations.mapTo(mutableSetOf()) { it.id }
        val stationIdMap = mutableMapOf<String, String>()
        importedData.stations.forEach { imported ->
            val index = nextStations.indexOfFirst { it.streamUrl == imported.streamUrl }
            if (index >= 0) {
                val existing = nextStations[index]
                stationIdMap[imported.id] = existing.id
                nextStations[index] = existing.copy(
                    title = imported.title,
                    tags = imported.tags,
                    isFavorite = imported.isFavorite,
                    imageUrl = imported.imageUrl ?: existing.imageUrl,
                )
            } else {
                val id = imported.id.takeIf { it !in usedIds } ?: UUID.randomUUID().toString()
                usedIds += id
                stationIdMap[imported.id] = id
                nextStations += imported.copy(id = id)
            }
        }

        val playlists = currentCollections.playlists.toMutableList()
        importedData.collections?.playlists?.forEach { imported ->
            val mappedIds = imported.stationIds.map { stationIdMap.getValue(it) }.distinct()
            val index = playlists.indexOfFirst { it.id == imported.id }
            if (index >= 0) {
                val existing = playlists[index]
                playlists[index] = existing.copy(
                    title = imported.title,
                    stationIds = (existing.stationIds + mappedIds).distinct(),
                )
            } else {
                playlists += imported.copy(stationIds = mappedIds)
            }
        }
        val allIds = nextStations.mapTo(mutableSetOf()) { it.id }
        val mappedStationOrder = importedData.customStationOrder.mapNotNull(stationIdMap::get)
        val mappedFavoriteOrder = importedData.customFavoriteOrder.mapNotNull(stationIdMap::get)
        val localTabOrder = orderedTabs(currentCollections.playlists, currentCollections.tabOrder).map { it.name }
        return StationImportResult(
            stations = nextStations,
            stationSortState = currentStationSortState,
            favoriteSortState = currentFavoriteSortState,
            customStationOrder = (currentCustomStationOrder + currentStations.map { it.id } + mappedStationOrder + nextStations.map { it.id })
                .filter(allIds::contains).distinct(),
            customFavoriteOrder = (currentCustomFavoriteOrder + currentStations.filter { it.isFavorite }.map { it.id } + mappedFavoriteOrder + nextStations.filter { it.isFavorite }.map { it.id })
                .filter(allIds::contains).distinct(),
            collections = LibraryCollections(
                playlists = playlists,
                tabOrder = orderedTabs(playlists, localTabOrder + importedData.collections?.tabOrder.orEmpty()).map { it.name },
            ),
        )
    }

    private fun encodeSort(state: StationSortState): JSONObject = JSONObject()
        .put("mode", state.mode.name)
        .put("ascending", state.ascending)

    private fun decodeSort(json: JSONObject): StationSortState = StationSortState(
        mode = StationSortMode.entries.firstOrNull { it.name == json.string("mode") }
            ?: error("Unknown sort mode"),
        ascending = json.boolean("ascending"),
    )

    private fun JSONObject.requireFields(vararg fields: String) {
        require(keys().asSequence().toSet() == fields.toSet())
    }

    private fun JSONObject.string(key: String): String = get(key).let {
        require(it is String)
        it
    }

    private fun JSONObject.boolean(key: String): Boolean = get(key).let {
        require(it is Boolean)
        it
    }

    private fun JSONObject.id(): String = string("id").also {
        require(it.isNotBlank() && '/' !in it)
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = List(length()) { index ->
        get(index).let { require(it is String); it }
    }

    private fun JSONArray.references(available: Set<String>): List<String> = strings().also {
        requireUnique(it)
        require(it.all(available::contains))
    }

    private fun requireUnique(ids: List<String>) {
        require(ids.size == ids.toSet().size)
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

    fun decode(text: String, cleanTrackingParameters: Boolean = false): StationExportData {
        val stations = text
            .trim()
            .split(Regex("""\r?\n\s*\r?\n"""))
            .filter { it.isNotBlank() }
            .mapIndexed { index, block -> decodeStation(block, index, cleanTrackingParameters) }

        require(stations.isNotEmpty()) {
            "No stations found in TXT file"
        }

        return StationExportData(stations = stations)
    }

    private fun decodeStation(
        block: String,
        index: Int,
        cleanTrackingParameters: Boolean,
    ): Station {
        val lines = block
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        require(lines.size in 2..3) {
            "Station ${index + 1} must contain 2 or 3 lines"
        }

        val streamUrl = lines[1]
            .let { if (cleanTrackingParameters) removeTrackingParameters(it) else it }
            .take(STATION_STREAM_URL_MAX_LENGTH)
            .let(::normalizeStreamUrl)
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
            tags = lines.getOrNull(2)?.let(::parseStationTags).orEmpty(),
            imageUrl = null,
            isFavorite = false,
            dateAdded = Instant.now().toString(),
        )
    }

    private fun String.isValidStreamUrl(): Boolean {
        return runCatching {
            val uri = URI(this)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}
