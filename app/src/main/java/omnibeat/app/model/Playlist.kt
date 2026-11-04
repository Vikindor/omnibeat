package omnibeat.app.model

data class Playlist(
    val id: String,
    val title: String,
    val stationIds: List<String> = emptyList(),
    val sortState: StationSortState = StationSortState(),
) {
    fun stations(library: List<Station>): List<Station> {
        val byId = library.associateBy { it.id }
        return sortedStations(
            source = stationIds.mapNotNull(byId::get),
            page = MainPage.Stations,
            sortState = sortState,
            customStationOrder = stationIds,
            customFavoriteOrder = emptyList(),
        )
    }
}
