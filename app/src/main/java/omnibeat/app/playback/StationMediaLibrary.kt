package omnibeat.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import omnibeat.app.R
import omnibeat.app.data.StationRepository
import omnibeat.app.data.appString
import omnibeat.app.model.Station

@OptIn(UnstableApi::class)
internal class StationMediaLibrary(
    private val context: Context,
    private val repository: StationRepository,
    private val scope: CoroutineScope,
    private val onStationsLoaded: (List<Station>) -> Unit,
    private val onPlaybackRejected: () -> Unit,
) : MediaLibrarySession.Callback {
    private data class Library(
        val stations: List<Station>,
        val recentIds: List<String>,
        val showArtwork: Boolean,
    ) {
        private fun favorites(): List<Station> =
            stations.filter { it.isFavorite }.sortedBy { it.title.lowercase() }

        private fun recentlyPlayed(): List<Station> =
            recentIds.mapNotNull { id -> stations.find { it.id == id } }

        fun stationsIn(parent: String): List<Station>? = when {
            parent == ALL -> stations.sortedBy { it.title.lowercase() }
            parent == FAVORITES -> favorites()
            parent == RECENT -> recentlyPlayed()
            parent == SUGGESTED -> (recentlyPlayed() + favorites()).distinctBy { it.id }
            parent.startsWith(TAG_PREFIX) -> {
                val tag = Uri.decode(parent.removePrefix(TAG_PREFIX))
                stations.filter { station -> station.tags.any { it.equals(tag, ignoreCase = true) } }
                    .sortedBy { it.title.lowercase() }
            }
            else -> null
        }

        fun search(query: String): List<Station> = stations.filter { station ->
            station.title.contains(query, ignoreCase = true) ||
                station.tags.any { it.contains(query, ignoreCase = true) }
        }.sortedBy { it.title.lowercase() }
    }

    private suspend fun library(): Library {
        val stations = repository.stations.first()
        onStationsLoaded(stations)
        return Library(
            stations, repository.recentlyPlayedStationIds.first(), repository.showAndroidAutoArtwork.first(),
        )
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                result.set(block())
            } catch (error: Exception) {
                result.setException(error)
            }
        }
        result.addListener({ if (result.isCancelled) job.cancel() }, { it.run() })
        job.invokeOnCompletion { error -> if (error != null) result.setException(error) }
        return result
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        val root = when {
            params?.isSuggested == true -> SUGGESTED
            params?.isRecent == true -> RECENT
            else -> ROOT
        }
        LibraryResult.ofItem(folder(root), params)
    }

    private fun folder(id: String, title: String? = null): MediaItem {
        val label = title ?: context.resources.appString(when (id) {
            ROOT, SUGGESTED -> R.string.app_name
            FAVORITES -> R.string.page_favorites
            ALL -> R.string.auto_all_stations
            TAGS -> R.string.auto_tags
            RECENT -> R.string.auto_recently_played
            else -> error("Unknown folder: $id")
        })
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(label).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS).build(),
        ).build()
    }

    private fun children(library: Library, parent: String): List<MediaItem>? = when (parent) {
        ROOT -> listOf(FAVORITES, ALL, TAGS, RECENT).map { folder(it) }
        TAGS -> library.stations.flatMap { it.tags }.distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }.map { folder(TAG_PREFIX + Uri.encode(it), it) }
        else -> library.stationsIn(parent)?.map { stationItem(context, it, parent, library.showArtwork) }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        val items = children(library(), parentId)
            ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        LibraryResult.ofItemList(paginate(items, page, pageSize), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = future {
        val library = library()
        val station = library.stations.find { it.id == stationId(mediaId) }
        val item = when {
            station != null -> stationItem(context, station, parentId(mediaId), library.showArtwork)
            mediaId in listOf(ROOT, ALL, FAVORITES, TAGS, RECENT, SUGGESTED) -> folder(mediaId)
            mediaId.startsWith(TAG_PREFIX) && children(library, TAGS)?.any { it.mediaId == mediaId } == true ->
                folder(mediaId, Uri.decode(mediaId.removePrefix(TAG_PREFIX)))
            else -> return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        LibraryResult.ofItem(item, null)
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        val items = children(library(), parentId)
            ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        session.notifyChildrenChanged(browser, parentId, items.size, params)
        LibraryResult.ofVoid(params)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = future {
        session.notifySearchResultChanged(browser, query, library().search(query).size, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
        val library = library()
        val matches = library.search(query).map { stationItem(context, it, showArtwork = library.showArtwork) }
        LibraryResult.ofItemList(paginate(matches, page, pageSize), params)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val library = library()
        val requested = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex)
            ?: throw IllegalArgumentException(context.resources.appString(R.string.auto_station_not_found))
        val query = requested.requestMetadata.searchQuery
        val queue: List<Station>
        val selected: Station?
        val parent: String
        if (query != null) {
            queue = library.search(query.trim())
            selected = if (query.isBlank()) resumeStation(library) else
                queue.find { it.title.equals(query.trim(), ignoreCase = true) } ?: queue.firstOrNull()
            parent = ALL
        } else {
            parent = parentId(requested.mediaId)
            queue = library.stationsIn(parent).orEmpty()
            selected = queue.find { it.id == stationId(requested.mediaId) }
        }
        if (selected == null) {
            val message = context.resources.appString(R.string.auto_station_not_found)
            mediaSession.sendError(controller, SessionError(SessionError.ERROR_BAD_VALUE, message, Bundle.EMPTY))
            onPlaybackRejected()
            throw IllegalArgumentException(message)
        }
        MediaSession.MediaItemsWithStartPosition(
            queue.map { stationItem(context, it, parent, library.showArtwork) },
            queue.indexOfFirst { it.id == selected.id },
            C.TIME_UNSET,
        )
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val library = library()
        val selected = resumeStation(library)
        if (selected == null) {
            val message = context.resources.appString(R.string.auto_no_stations)
            mediaSession.sendError(controller, SessionError(SessionError.ERROR_BAD_VALUE, message, Bundle.EMPTY))
            onPlaybackRejected()
            throw IllegalArgumentException(message)
        }
        val queue = if (isForPlayback) library.stationsIn(ALL).orEmpty() else listOf(selected)
        MediaSession.MediaItemsWithStartPosition(
            queue.map { stationItem(context, it, showArtwork = library.showArtwork) },
            queue.indexOfFirst { it.id == selected.id }, C.TIME_UNSET,
        )
    }

    private suspend fun resumeStation(library: Library): Station? {
        val lastId = if (repository.rememberLastStation.first()) repository.lastPlayedStationId.first() else null
        return library.stations.find { it.id == lastId } ?: library.stations.firstOrNull()
    }

    suspend fun notifyChanged(session: MediaLibrarySession) {
        val library = library()
        val parents = listOf(ROOT, ALL, FAVORITES, TAGS, RECENT, SUGGESTED) +
            library.stations.flatMap { it.tags }.distinct().map { TAG_PREFIX + Uri.encode(it) }
        parents.forEach { parent -> session.notifyChildrenChanged(parent, children(library, parent).orEmpty().size, null) }
    }

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        require(page >= 0 && pageSize > 0)
        val start = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(start, (start.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt())
    }

    companion object {
        private const val ROOT = "root"
        private const val ALL = "all"
        private const val FAVORITES = "favorites"
        private const val TAGS = "tags"
        private const val RECENT = "recent"
        private const val SUGGESTED = "suggested"
        private const val TAG_PREFIX = "tag:"

        fun stationId(mediaId: String): String? = mediaId.split('/').takeIf {
            it.size == 3 && it[0] == "station"
        }?.get(2)

        private fun parentId(mediaId: String): String = Uri.decode(mediaId.split('/').getOrNull(1) ?: ALL)

        fun stationItem(
            context: Context, station: Station, parent: String = ALL, showArtwork: Boolean = true,
        ): MediaItem =
            MediaItem.Builder().setMediaId("station/${Uri.encode(parent)}/${station.id}")
                .setUri(station.streamUrl)
                .setMediaMetadata(stationMetadata(context, station, null, showArtwork)).build()

        fun stationMetadata(
            context: Context, station: Station, trackText: String?, showArtwork: Boolean = true,
        ): MediaMetadata =
            MediaMetadata.Builder().setTitle(station.title).setArtist(trackText)
                .setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setArtworkUri(station.imageUrl?.takeIf { showArtwork }
                    ?.let { StationArtworkProvider.uri(context, station.id, it) })
                .build()
    }
}
