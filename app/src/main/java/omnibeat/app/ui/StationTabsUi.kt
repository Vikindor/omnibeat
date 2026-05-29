package omnibeat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import omnibeat.app.model.MainPage
import omnibeat.app.model.Station
import omnibeat.app.model.StationEditorState
import omnibeat.app.model.StationReorderDraft
import omnibeat.app.model.StationSortState
import omnibeat.app.model.sortedStations

@Composable
fun StationTabsPager(
    visibleTabPages: List<MainPage>,
    pagerState: PagerState,
    stations: List<Station>,
    selectedStationId: String?,
    sortState: StationSortState,
    customStationOrder: List<String>,
    customFavoriteOrder: List<String>,
    reorderDraft: StationReorderDraft?,
    scrollToSelectedRequest: Int,
    scrollToStationId: String?,
    showArtwork: Boolean,
    listEnabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onFavoriteClick: (Station) -> Unit,
    onEditStation: (StationEditorState) -> Unit,
    onStationClick: (Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = reorderDraft == null,
        modifier = modifier,
    ) { pageIndex ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pagerFade(pagerState, pageIndex),
        ) {
            when (visibleTabPages[pageIndex]) {
                MainPage.Stations -> {
                    val visibleStations = reorderDraft?.stations ?: sortedStations(
                        source = stations,
                        page = MainPage.Stations,
                        sortState = sortState,
                        customStationOrder = customStationOrder,
                        customFavoriteOrder = customFavoriteOrder,
                    )
                    StationPageList(
                        stations = visibleStations,
                        selectedStationId = selectedStationId,
                        emptyContent = {
                            EmptyStationsState(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp),
                            )
                        },
                        scrollToSelectedRequest = scrollToSelectedRequest,
                        scrollToStationId = scrollToStationId,
                        showArtwork = showArtwork,
                        enabled = listEnabled,
                        reordering = reorderDraft != null,
                        onMove = onMove,
                        onFavoriteClick = onFavoriteClick,
                        onEditStation = { station ->
                            createEditorState(stations, station)?.let(onEditStation)
                        },
                        onStationClick = onStationClick,
                    )
                }

                MainPage.Favorites -> {
                    val favoriteStations = reorderDraft?.stations ?: sortedStations(
                        source = stations.filter { it.isFavorite },
                        page = MainPage.Favorites,
                        sortState = sortState,
                        customStationOrder = customStationOrder,
                        customFavoriteOrder = customFavoriteOrder,
                    )
                    StationPageList(
                        stations = favoriteStations,
                        selectedStationId = selectedStationId,
                        emptyContent = {
                            EmptyFavoritesState(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp),
                            )
                        },
                        scrollToSelectedRequest = scrollToSelectedRequest,
                        scrollToStationId = scrollToStationId,
                        showArtwork = showArtwork,
                        enabled = listEnabled,
                        reordering = reorderDraft != null,
                        onMove = onMove,
                        onFavoriteClick = onFavoriteClick,
                        onEditStation = { station ->
                            createEditorState(stations, station)?.let(onEditStation)
                        },
                        onStationClick = onStationClick,
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun StationPageList(
    stations: List<Station>,
    selectedStationId: String?,
    emptyContent: @Composable () -> Unit,
    scrollToSelectedRequest: Int,
    scrollToStationId: String?,
    showArtwork: Boolean,
    enabled: Boolean,
    reordering: Boolean,
    onMove: (Int, Int) -> Unit,
    onFavoriteClick: (Station) -> Unit,
    onEditStation: (Station) -> Unit,
    onStationClick: (Station) -> Unit,
) {
    if (stations.isEmpty()) {
        emptyContent()
        return
    }

    StationList(
        stations = stations,
        selectedIndex = stations.indexOfFirst { it.id == selectedStationId },
        scrollToSelectedRequest = scrollToSelectedRequest,
        scrollToStationId = scrollToStationId,
        showArtwork = showArtwork,
        enabled = enabled,
        reordering = reordering,
        onMove = onMove,
        onFavoriteClick = { _, station -> onFavoriteClick(station) },
        onStationEdit = { _, station -> onEditStation(station) },
        onStationClick = { _, station -> onStationClick(station) },
    )
}

private fun createEditorState(
    stations: List<Station>,
    station: Station,
): StationEditorState? {
    val index = stations.indexOfFirst { it.id == station.id }
    if (index == -1) {
        return null
    }
    return StationEditorState(
        stationIndex = index,
        title = station.title,
        streamUrl = station.streamUrl,
        tags = station.tags.joinToString(", "),
        dateAdded = station.dateAdded,
    )
}
