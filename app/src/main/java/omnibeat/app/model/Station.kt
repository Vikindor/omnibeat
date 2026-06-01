package omnibeat.app.model

const val STATION_TITLE_MAX_LENGTH = 100
const val STATION_STREAM_URL_MAX_LENGTH = 2048
const val STATION_TAGS_INPUT_MAX_LENGTH = 200

data class Station(
    val id: String,
    val title: String,
    val streamUrl: String,
    val tags: List<String>,
    val imageUrl: String? = null,
    val isFavorite: Boolean = false,
    val dateAdded: String,
)

enum class StationSortMode {
    DateAdded,
    StationTitle,
    FavoritesFirst,
    Custom,
}

data class StationSortState(
    val mode: StationSortMode = StationSortMode.DateAdded,
    val ascending: Boolean = false,
)

enum class MainPage {
    Stations,
    Favorites,
    ExportImport,
    SearchOnline,
    Settings,
    About;

    companion object {
        val tabPages = listOf(Stations, Favorites)
    }
}

data class StationReorderDraft(
    val page: MainPage,
    val stations: List<Station>,
)

data class StationEditorState(
    val stationIndex: Int?,
    val title: String,
    val streamUrl: String,
    val tags: String,
    val dateAdded: String?,
)
