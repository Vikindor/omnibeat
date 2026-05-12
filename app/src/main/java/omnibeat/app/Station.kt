package omnibeat.app

const val STATION_TITLE_MAX_LENGTH = 100
const val STATION_STREAM_URL_MAX_LENGTH = 2048
const val STATION_TAGS_INPUT_MAX_LENGTH = 200

data class Station(
    val id: String,
    val title: String,
    val streamUrl: String,
    val tags: List<String>,
    val isFavorite: Boolean = false,
    val dateAdded: String,
)

enum class StationSortMode(val label: String) {
    DateAdded("Date added"),
    StationTitle("Station title"),
    FavoritesFirst("Favorites"),
}

data class StationSortState(
    val mode: StationSortMode = StationSortMode.DateAdded,
    val ascending: Boolean = false,
)

data class StationEditorState(
    val stationIndex: Int?,
    val title: String,
    val streamUrl: String,
    val tags: String,
)
