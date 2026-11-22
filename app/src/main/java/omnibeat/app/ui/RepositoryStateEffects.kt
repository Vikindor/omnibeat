package omnibeat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import omnibeat.app.data.StationRepository
import omnibeat.app.model.MainPage
import omnibeat.app.model.Station
import omnibeat.app.model.StationDividerStyle
import omnibeat.app.model.StationSortState

@Composable
fun RepositoryStateEffects(
    repository: StationRepository,
    onStationsChange: (List<Station>) -> Unit,
    onAppVolumeChange: (Float) -> Unit,
    onStationDividersChange: (StationDividerStyle) -> Unit,
    onShowPlaybackIndicatorChange: (Boolean) -> Unit,
    onAnimatePlaybackIndicatorChange: (Boolean) -> Unit,
    onShowFavoriteButtonsChange: (Boolean) -> Unit,
    onShowEmptyFavoritesTabChange: (Boolean) -> Unit,
    onConfirmStationDeletionChange: (Boolean) -> Unit,
    onShowStationArtworkChange: (Boolean) -> Unit,
    onRememberLastStationChange: (Boolean) -> Unit,
    onStopServiceAfterPauseMinutesChange: (Int) -> Unit,
    onKeepMediaCardChange: (Boolean) -> Unit,
    onShowBitrateInControlPanelChange: (Boolean) -> Unit,
    onShowUnavailableBitrateChange: (Boolean) -> Unit,
    onMarqueeTrackTitleChange: (Boolean) -> Unit,
    onAutoExpandPlayerPanelOnPlaybackChange: (Boolean) -> Unit,
    onCollapsePlayerPanelInSearchChange: (Boolean) -> Unit,
    onAddRadioBrowserTagsChange: (Boolean) -> Unit,
    onRemoveTrackingParametersChange: (Boolean) -> Unit,
    onWidgetFollowAppThemeChange: (Boolean) -> Unit,
    onWidgetMarqueeTrackTitleChange: (Boolean) -> Unit,
    onShowAndroidAutoArtworkChange: (Boolean) -> Unit,
    onPlayerPanelCollapsedChange: (Boolean) -> Unit,
    onLastMainPageChange: (MainPage) -> Unit,
    onSelectedPageRestore: (MainPage) -> Unit,
    onStationSortStateChange: (StationSortState) -> Unit,
    onFavoriteSortStateChange: (StationSortState) -> Unit,
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
        repository.stationDividers.collect(onStationDividersChange)
    }

    LaunchedEffect(repository) {
        repository.showPlaybackIndicator.collect(onShowPlaybackIndicatorChange)
    }

    LaunchedEffect(repository) {
        repository.animatePlaybackIndicator.collect(onAnimatePlaybackIndicatorChange)
    }

    LaunchedEffect(repository) {
        repository.showFavoriteButtons.collect(onShowFavoriteButtonsChange)
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

    LaunchedEffect(repository) {
        repository.showStationArtwork.collect { savedShowStationArtwork ->
            onShowStationArtworkChange(savedShowStationArtwork)
        }
    }

    LaunchedEffect(repository) {
        repository.rememberLastStation.collect { savedRememberLastStation ->
            onRememberLastStationChange(savedRememberLastStation)
        }
    }

    LaunchedEffect(repository) {
        repository.stopServiceAfterPauseMinutes.collect { savedMinutes ->
            onStopServiceAfterPauseMinutesChange(savedMinutes)
        }
    }

    LaunchedEffect(repository) {
        repository.keepMediaCard.collect { keep ->
            onKeepMediaCardChange(keep)
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
        repository.autoExpandPlayerPanelOnPlayback.collect { savedAutoExpand ->
            onAutoExpandPlayerPanelOnPlaybackChange(savedAutoExpand)
        }
    }

    LaunchedEffect(repository) {
        repository.collapsePlayerPanelInSearch.collect { savedCollapseInSearch ->
            onCollapsePlayerPanelInSearchChange(savedCollapseInSearch)
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
        repository.widgetFollowAppTheme.collect(onWidgetFollowAppThemeChange)
    }

    LaunchedEffect(repository) {
        repository.widgetMarqueeTrackTitle.collect(onWidgetMarqueeTrackTitleChange)
    }

    LaunchedEffect(repository) {
        repository.showAndroidAutoArtwork.collect(onShowAndroidAutoArtworkChange)
    }

    LaunchedEffect(repository) {
        repository.playerPanelCollapsed.collect { savedPlayerPanelCollapsed ->
            onPlayerPanelCollapsedChange(savedPlayerPanelCollapsed)
        }
    }

    LaunchedEffect(repository) {
        val savedPage = repository.lastMainPage.first()
        val restoredPage = if (savedPage?.startsWith("Playlist:") == true) {
            MainPage.Playlist(savedPage.removePrefix("Playlist:"))
        } else {
            MainPage.tabPages.firstOrNull { it.name == savedPage } ?: MainPage.Stations
        }
        onLastMainPageChange(restoredPage)
        onSelectedPageRestore(restoredPage)
    }

    LaunchedEffect(repository) {
        repository.stationSortState.collect { savedSortState ->
            onStationSortStateChange(savedSortState)
        }
    }

    LaunchedEffect(repository) {
        repository.favoriteSortState.collect(onFavoriteSortStateChange)
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
