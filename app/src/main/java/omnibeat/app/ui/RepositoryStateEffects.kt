package omnibeat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import omnibeat.app.data.StationRepository
import omnibeat.app.model.MainPage
import omnibeat.app.model.Station
import omnibeat.app.model.StationSortState

@Composable
fun RepositoryStateEffects(
    repository: StationRepository,
    selectedPage: MainPage,
    visibleTabPages: List<MainPage>,
    onStationsChange: (List<Station>) -> Unit,
    onAppVolumeChange: (Float) -> Unit,
    onShowStationArtworkChange: (Boolean) -> Unit,
    onAddRadioBrowserTagsChange: (Boolean) -> Unit,
    onRemoveTrackingParametersChange: (Boolean) -> Unit,
    onRememberLastStationChange: (Boolean) -> Unit,
    onShowBitrateInControlPanelChange: (Boolean) -> Unit,
    onShowUnavailableBitrateChange: (Boolean) -> Unit,
    onMarqueeTrackTitleChange: (Boolean) -> Unit,
    onShowEmptyFavoritesTabChange: (Boolean) -> Unit,
    onConfirmStationDeletionChange: (Boolean) -> Unit,
    onLastMainPageChange: (MainPage) -> Unit,
    onSelectedPageRestore: (MainPage) -> Unit,
    onStationSortStateChange: (StationSortState) -> Unit,
    onCustomStationOrderChange: (List<String>) -> Unit,
    onCustomFavoriteOrderChange: (List<String>) -> Unit,
    onLastPlayedStationIdChange: (String?) -> Unit,
) {
    LaunchedEffect(repository) {
        repository.stations.collect { savedStations ->
            onStationsChange(savedStations)
        }
    }

    LaunchedEffect(repository) {
        repository.appVolume.collect { savedVolume ->
            onAppVolumeChange(savedVolume)
        }
    }

    LaunchedEffect(repository) {
        repository.showStationArtwork.collect { savedShowStationArtwork ->
            onShowStationArtworkChange(savedShowStationArtwork)
        }
    }

    LaunchedEffect(repository) {
        repository.addRadioBrowserTags.collect { savedAddRadioBrowserTags ->
            onAddRadioBrowserTagsChange(savedAddRadioBrowserTags)
        }
    }

    LaunchedEffect(repository) {
        repository.removeTrackingParameters.collect { savedRemoveTrackingParameters ->
            onRemoveTrackingParametersChange(savedRemoveTrackingParameters)
        }
    }

    LaunchedEffect(repository) {
        repository.rememberLastStation.collect { savedRememberLastStation ->
            onRememberLastStationChange(savedRememberLastStation)
        }
    }

    LaunchedEffect(repository) {
        repository.showBitrateInControlPanel.collect { savedShowBitrate ->
            onShowBitrateInControlPanelChange(savedShowBitrate)
        }
    }

    LaunchedEffect(repository) {
        repository.showUnavailableBitrate.collect { savedShowUnavailableBitrate ->
            onShowUnavailableBitrateChange(savedShowUnavailableBitrate)
        }
    }

    LaunchedEffect(repository) {
        repository.marqueeTrackTitle.collect { savedMarqueeTrackTitle ->
            onMarqueeTrackTitleChange(savedMarqueeTrackTitle)
        }
    }

    LaunchedEffect(repository) {
        repository.showEmptyFavoritesTab.collect { savedShowEmptyFavoritesTab ->
            onShowEmptyFavoritesTabChange(savedShowEmptyFavoritesTab)
        }
    }

    LaunchedEffect(repository) {
        repository.confirmStationDeletion.collect { savedConfirmStationDeletion ->
            onConfirmStationDeletionChange(savedConfirmStationDeletion)
        }
    }

    LaunchedEffect(repository, selectedPage, visibleTabPages) {
        repository.lastMainPage.collect { savedPage ->
            val restoredPage = MainPage.tabPages.firstOrNull { it.name == savedPage } ?: MainPage.Stations
            onLastMainPageChange(restoredPage)
            if (selectedPage in visibleTabPages) {
                onSelectedPageRestore(restoredPage)
            }
        }
    }

    LaunchedEffect(repository) {
        repository.stationSortState.collect { savedSortState ->
            onStationSortStateChange(savedSortState)
        }
    }

    LaunchedEffect(repository) {
        repository.customStationOrder.collect { savedOrder ->
            onCustomStationOrderChange(savedOrder)
        }
    }

    LaunchedEffect(repository) {
        repository.customFavoriteOrder.collect { savedOrder ->
            onCustomFavoriteOrderChange(savedOrder)
        }
    }

    LaunchedEffect(repository) {
        repository.lastPlayedStationId.collect { savedStationId ->
            onLastPlayedStationIdChange(savedStationId)
        }
    }
}
