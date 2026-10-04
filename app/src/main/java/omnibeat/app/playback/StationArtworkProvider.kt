package omnibeat.app.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import omnibeat.app.data.StationArtworkCache
import omnibeat.app.data.StationRepository
import java.io.FileNotFoundException

class StationArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val stationId = stationId(uri)
        val appContext = requireNotNull(context)
        val file = try {
            runBlocking(Dispatchers.IO) {
                val repository = StationRepository(appContext)
                if (!repository.showAndroidAutoArtwork.first()) throw FileNotFoundException("Artwork is disabled")
                val station = repository.stations.first().find { it.id == stationId }
                val url = station?.imageUrl ?: throw FileNotFoundException("Station has no artwork")
                StationArtworkCache.fileForMedia(appContext, url)
            }
        } catch (error: Exception) {
            throw FileNotFoundException("Cannot load station artwork").apply { initCause(error) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String {
        stationId(uri)
        return "image/*"
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val id = stationId(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) id else null })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Read-only artwork")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only artwork")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only artwork")

    private fun stationId(uri: Uri): String {
        val segments = uri.pathSegments
        if (uri.authority != "${requireNotNull(context).packageName}.station-artwork" ||
            segments.size != 2 || segments[0] != "station" || segments[1].isBlank()
        ) throw FileNotFoundException("Unknown artwork URI")
        return segments[1]
    }

    companion object {
        fun uri(context: Context, stationId: String, imageUrl: String): Uri = Uri.Builder().scheme("content")
            .authority("${context.packageName}.station-artwork").appendPath("station").appendPath(stationId)
            .appendQueryParameter("source", imageUrl.hashCode().toString())
            .appendQueryParameter("revision", StationArtworkCache.revision(imageUrl).toString()).build()
    }
}
