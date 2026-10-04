package omnibeat.app.data

import android.content.Context
import android.graphics.ImageDecoder
import android.util.AtomicFile
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import omnibeat.app.network.NetworkStatus
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days

object StationArtworkCache {
    private data class Key(val url: String, val width: Int, val height: Int)
    private data class Artwork(val image: ImageBitmap, val byteCount: Int)
    private data class Metadata(
        val checkedAt: Long = 0,
        val attemptedAt: Long = 0,
        val etag: String? = null,
        val lastModified: String? = null,
    )

    private val images = object : LruCache<Key, Artwork>(4_000_000) {
        override fun sizeOf(key: Key, value: Artwork): Int = value.byteCount
    }
    private val fileLocks = Array(32) { Mutex() }
    private val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun updates(url: String): Flow<Long> = revisions.map { it[url] ?: 0 }.distinctUntilChanged()

    fun revision(url: String): Long = revisions.value[url] ?: 0

    fun get(url: String, width: Int, height: Int): ImageBitmap? =
        images.get(Key(url, width, height))?.image
    suspend fun refresh(context: Context, url: String) = withContext(Dispatchers.IO) {
        fileLocks[(url.hashCode() and Int.MAX_VALUE) % fileLocks.size].withLock {
            val file = cacheFile(context, url)
            val metadataFile = AtomicFile(File(file.parentFile, "${file.name}.json"))
            val now = System.currentTimeMillis()
            val connection = openConnection(url)
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("Artwork request failed: HTTP $status")
                downloadAndReplace(connection, file, 1, 1)
                invalidate(url)
                revisions.update { it + (url to ((it[url] ?: 0) + 1)) }
                writeMetadata(
                    metadataFile,
                    Metadata(
                        checkedAt = System.currentTimeMillis(),
                        attemptedAt = now,
                        etag = connection.getHeaderField("ETag"),
                        lastModified = connection.getHeaderField("Last-Modified"),
                    ),
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun load(context: Context, url: String, width: Int, height: Int): Flow<ImageBitmap> = flow {
        require(width > 0 && height > 0)
        val key = Key(url, width, height)
        fileLocks[(url.hashCode() and Int.MAX_VALUE) % fileLocks.size].withLock {
            val file = cacheFile(context, url)
            val cached = images.get(key) ?: if (file.isFile) decode(file, width, height) else null
            cached?.let {
                images.put(key, it)
                emit(it.image)
            }
            val metadataFile = AtomicFile(File(file.parentFile, "${file.name}.json"))
            val metadata = readMetadata(metadataFile)
            val now = System.currentTimeMillis()
            if (file.isFile && now - metadata.checkedAt in 0 until 30.days.inWholeMilliseconds) return@withLock
            if (now - metadata.attemptedAt in 0 until 1.days.inWholeMilliseconds) return@withLock
            if (!NetworkStatus.isOnline(context)) return@withLock

            writeMetadata(metadataFile, metadata.copy(attemptedAt = now))
            var refreshed: Artwork? = null
            val updatedMetadata = try {
                val connection = openConnection(url)
                if (file.isFile) {
                    metadata.etag?.let { connection.setRequestProperty("If-None-Match", it) }
                    metadata.lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
                }
                try {
                    when (val status = connection.responseCode) {
                        HttpURLConnection.HTTP_NOT_MODIFIED -> {
                            if (!file.isFile) throw IOException("Artwork is missing for HTTP 304 response")
                            metadata.copy(
                                checkedAt = System.currentTimeMillis(),
                                attemptedAt = now,
                                etag = connection.getHeaderField("ETag") ?: metadata.etag,
                                lastModified = connection.getHeaderField("Last-Modified") ?: metadata.lastModified,
                            )
                        }
                        in 200..299 -> {
                            refreshed = downloadAndReplace(connection, file, width, height)
                            Metadata(
                                checkedAt = System.currentTimeMillis(),
                                attemptedAt = now,
                                etag = connection.getHeaderField("ETag"),
                                lastModified = connection.getHeaderField("Last-Modified"),
                            )
                        }
                        else -> throw IOException("Artwork request failed: HTTP $status")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: IOException) {
                metadata.copy(attemptedAt = now)
            } catch (_: IllegalArgumentException) {
                metadata.copy(attemptedAt = now)
            } catch (_: SecurityException) {
                metadata.copy(attemptedAt = now)
            }
            writeMetadata(metadataFile, updatedMetadata)
            refreshed?.let { artwork ->
                invalidate(url)
                images.put(key, artwork)
                emit(artwork.image)
            }
        }
    }.flowOn(Dispatchers.IO).catch { error ->
        when (error) {
            is IOException, is IllegalArgumentException, is SecurityException, is JSONException -> Unit
            else -> throw error
        }
    }

    suspend fun fileForMedia(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val file = cacheFile(context, url)
        if (!file.isFile) {
            images.remove(Key(url, 256, 256))
            load(context, url, 256, 256).first()
        }
        file
    }

    private fun cacheFile(context: Context, url: String): File {
        val directory = File(context.applicationContext.cacheDir, "station_artwork")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create station artwork cache directory")
        }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, hash)
    }

    private fun invalidate(url: String) {
        images.snapshot().keys.filter { it.url == url }.forEach { images.remove(it) }
    }

    private suspend fun downloadAndReplace(
        connection: HttpURLConnection,
        file: File,
        width: Int,
        height: Int,
    ): Artwork = withContext(Dispatchers.IO) {
        val temporaryFile = File.createTempFile("artwork-", ".tmp", file.parentFile)
        try {
            download(connection, temporaryFile)
            currentCoroutineContext().ensureActive()
            val decoded = decode(temporaryFile, width, height)
            currentCoroutineContext().ensureActive()
            Files.move(
                temporaryFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            decoded
        } finally {
            temporaryFile.delete()
        }
    }

    private suspend fun readMetadata(file: AtomicFile): Metadata = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext Metadata()
        val json = JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        Metadata(
            checkedAt = json.getLong("checkedAt"),
            attemptedAt = json.getLong("attemptedAt"),
            etag = if (json.isNull("etag")) null else json.getString("etag"),
            lastModified = if (json.isNull("lastModified")) null else json.getString("lastModified"),
        )
    }

    private fun writeMetadata(file: AtomicFile, metadata: Metadata) {
        val json = JSONObject()
            .put("checkedAt", metadata.checkedAt)
            .put("attemptedAt", metadata.attemptedAt)
            .put("etag", metadata.etag ?: JSONObject.NULL)
            .put("lastModified", metadata.lastModified ?: JSONObject.NULL)
        val output = file.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun decode(file: File, width: Int, height: Int): Artwork {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = max(
                width.toFloat() / info.size.width,
                height.toFloat() / info.size.height,
            ).coerceAtMost(1f)
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
        return Artwork(bitmap.asImageBitmap(), bitmap.allocationByteCount)
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as? HttpURLConnection
            ?: throw IOException("Unsupported station artwork URL")
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "OmniBeat Android")
        return connection
    }

    private suspend fun download(connection: HttpURLConnection, file: File) = withContext(Dispatchers.IO) {
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
            }
        }
    }
}
