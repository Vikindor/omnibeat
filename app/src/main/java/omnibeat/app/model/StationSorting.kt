package omnibeat.app.model

fun customSortedStations(
    source: List<Station>,
    customOrder: List<String>,
): List<Station> {
    if (customOrder.isEmpty()) {
        return source
    }
    val sourceById = source.associateBy { it.id }
    val orderedStations = customOrder.mapNotNull(sourceById::get)
    val orderedIds = orderedStations.map { it.id }.toSet()
    return orderedStations + source.filterNot { it.id in orderedIds }
}

fun sortedStations(
    source: List<Station>,
    page: MainPage,
    sortState: StationSortState,
    customStationOrder: List<String>,
    customFavoriteOrder: List<String>,
): List<Station> {
    return when (sortState.mode) {
        StationSortMode.Custom -> customSortedStations(
            source = source,
            customOrder = if (page == MainPage.Favorites) customFavoriteOrder else customStationOrder,
        )
        StationSortMode.DateAdded -> if (sortState.ascending) {
            source.sortedBy { it.dateAdded }
        } else {
            source.sortedByDescending { it.dateAdded }
        }
        StationSortMode.StationTitle -> {
            val sorted = source.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            if (sortState.ascending) sorted else sorted.asReversed()
        }
        StationSortMode.FavoritesFirst -> if (sortState.ascending) {
            source.sortedWith(
                compareBy<Station> { it.isFavorite }
                    .thenBy { it.dateAdded },
            )
        } else {
            source.sortedWith(
                compareByDescending<Station> { it.isFavorite }
                    .thenByDescending { it.dateAdded },
            )
        }
    }
}
