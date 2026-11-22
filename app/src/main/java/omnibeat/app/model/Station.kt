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

sealed class MainPage(val name: String) {
    data object Stations : MainPage("Stations")
    data object Favorites : MainPage("Favorites")
    data class Playlist(val id: String) : MainPage("Playlist:$id")
    data object ExportImport : MainPage("ExportImport")
    data object Translation : MainPage("Translation")
    data object SearchOnline : MainPage("SearchOnline")
    data object Settings : MainPage("Settings")
    data object About : MainPage("About")
    data object TrackHistory : MainPage("TrackHistory")

    companion object {
        val tabPages: List<MainPage>
            get() = listOf(Stations, Favorites)
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
