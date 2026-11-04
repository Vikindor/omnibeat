package omnibeat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import omnibeat.app.model.Playlist
import omnibeat.app.model.MainPage
import omnibeat.app.model.Station
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.model.StationEditorState
import omnibeat.app.model.StationReorderDraft
import omnibeat.app.model.StationSortState
import omnibeat.app.model.sortedStations

@Composable
fun StationTabsPager(
    visibleTabPagesState: State<List<MainPage>>,
    playlists: List<Playlist>,
    pagerState: PagerState,
    stations: List<Station>,
    selectedStationId: String?,
    playingStationId: String?,
    showPlaybackIndicator: Boolean,
    showFavoriteButtons: Boolean,
    stationSortState: StationSortState,
    favoriteSortState: StationSortState,
    customStationOrder: List<String>,
    customFavoriteOrder: List<String>,
    reorderDraft: StationReorderDraft?,
    scrollToSelectedRequest: Int,
    scrollToStationId: String?,
    showArtwork: Boolean,
    dividerStyle: StationDividerStyle,
    listEnabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onFavoriteClick: (Station) -> Unit,
    onEditStation: (StationEditorState) -> Unit,
    onStationClick: (MainPage, Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        key = { visibleTabPagesState.value[it].name },
        userScrollEnabled = reorderDraft == null && listEnabled,
        modifier = modifier,
    ) { pageIndex ->
        val page = visibleTabPagesState.value.getOrNull(pageIndex) ?: return@HorizontalPager
        val playlist = (page as? MainPage.Playlist)?.let { tab -> playlists.firstOrNull { it.id == tab.id } }
        val draft = reorderDraft?.takeIf { it.page == page }
        val visibleStations = draft?.stations ?: if (page is MainPage.Playlist) {
            playlist?.stations(stations).orEmpty()
        } else sortedStations(
            source = if (page == MainPage.Favorites) stations.filter { it.isFavorite } else stations,
            page = page,
            sortState = if (page == MainPage.Favorites) favoriteSortState else stationSortState,
            customStationOrder = customStationOrder,
            customFavoriteOrder = customFavoriteOrder,
        )
        Box(Modifier.fillMaxSize().pagerFade(pagerState, pageIndex)) {
            StationPageList(
                stations = visibleStations,
                selectedStationId = selectedStationId,
                playingStationId = playingStationId,
                showPlaybackIndicator = showPlaybackIndicator,
                showFavoriteButtons = showFavoriteButtons,
                emptyContent = {
                    when (page) {
                        MainPage.Stations -> EmptyStationsState(Modifier.fillMaxSize().padding(horizontal = RadioSpacing.inset))
                        MainPage.Favorites -> EmptyFavoritesState(Modifier.fillMaxSize().padding(horizontal = RadioSpacing.inset))
                        else -> EmptyStationsState(Modifier.fillMaxSize().padding(horizontal = RadioSpacing.inset))
                    }
                },
                scrollToSelectedRequest = scrollToSelectedRequest,
                scrollToStationId = scrollToStationId,
                showArtwork = showArtwork,
                dividerStyle = dividerStyle,
                enabled = listEnabled,
                reordering = draft != null,
                onMove = onMove,
                onFavoriteClick = onFavoriteClick,
                onEditStation = { station -> createEditorState(stations, station)?.let(onEditStation) },
                onStationClick = { station -> onStationClick(page, station) },
            )
        }
    }
}

@Composable
private fun StationPageList(
    stations: List<Station>,
    selectedStationId: String?,
    playingStationId: String?,
    showPlaybackIndicator: Boolean,
    showFavoriteButtons: Boolean,
    emptyContent: @Composable () -> Unit,
    scrollToSelectedRequest: Int,
    scrollToStationId: String?,
    showArtwork: Boolean,
    dividerStyle: StationDividerStyle,
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
        playingStationId = playingStationId,
        showPlaybackIndicator = showPlaybackIndicator,
        showFavoriteButtons = showFavoriteButtons,
        scrollToSelectedRequest = scrollToSelectedRequest,
        scrollToStationId = scrollToStationId,
        showArtwork = showArtwork,
        dividerStyle = dividerStyle,
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
