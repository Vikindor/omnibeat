package omnibeat.app

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ResolvedStream(
    val playableUrl: String,
    val bitrateLabel: String?,
)

object StreamResolver {
    @Throws(IOException::class)
    fun resolvePlayableUrl(streamUrl: String): String {
        return resolveStream(streamUrl).playableUrl
    }

    @Throws(IOException::class)
    fun resolveStream(streamUrl: String): ResolvedStream {
        val lower = streamUrl.lowercase(Locale.US)
        val playlistResult = when {
            ".pls" in lower -> resolvePls(streamUrl)
            ".m3u" in lower && ".m3u8" !in lower -> resolveM3u(streamUrl)
            ".m3u8" in lower -> ResolvedStream(streamUrl, readHlsBitrate(streamUrl))
            else -> ResolvedStream(streamUrl, null)
        }
        return playlistResult.copy(
            bitrateLabel = playlistResult.bitrateLabel ?: readIcyBitrate(playlistResult.playableUrl),
        )
    }

    @Throws(IOException::class)
    private fun resolvePls(streamUrl: String): ResolvedStream {
        val lines = readRemoteText(streamUrl)
        lines.forEach { line ->
            val trimmed = line.trim()
            val equals = trimmed.indexOf('=')
            if (equals > 0 && trimmed.substring(0, equals).lowercase(Locale.US).startsWith("file")) {
                val url = trimmed.substring(equals + 1).trim()
                if (url.startsWith("http")) {
                    val index = trimmed.substring(4, equals).toIntOrNull()
                    return ResolvedStream(
                        playableUrl = url,
                        bitrateLabel = readPlsBitrate(lines, index),
                    )
                }
            }
        }
        throw IOException("PLS playlist has no stream URL")
    }

    @Throws(IOException::class)
    private fun resolveM3u(streamUrl: String): ResolvedStream {
        readRemoteText(streamUrl).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                return ResolvedStream(URL(URL(streamUrl), trimmed).toString(), null)
            }
        }
        throw IOException("M3U playlist has no stream URL")
    }

    private fun readPlsBitrate(lines: List<String>, index: Int?): String? {
        val expectedKey = index?.let { "bitrate$it" }
        lines.forEach { line ->
            val trimmed = line.trim()
            val equals = trimmed.indexOf('=')
            if (equals <= 0) return@forEach

            val key = trimmed.substring(0, equals).lowercase(Locale.US)
            if (key == expectedKey || (expectedKey == null && key.startsWith("bitrate"))) {
                return formatKbps(trimmed.substring(equals + 1).trim().toIntOrNull())
            }
        }
        return null
    }

    private fun readHlsBitrate(streamUrl: String): String? {
        return runCatching {
            readRemoteText(streamUrl)
                .asSequence()
                .filter { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
                .mapNotNull { line ->
                    Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }
                .maxOrNull()
                ?.let { bitsPerSecond -> formatKbps(bitsPerSecond / 1000) }
        }.getOrNull()
    }

    private fun readIcyBitrate(streamUrl: String): String? {
        return runCatching {
            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Icy-MetaData", "1")
            try {
                connection.connect()
                formatKbps(readFirstNumber(connection.getHeaderField("icy-br")))
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    @Throws(IOException::class)
    private fun readRemoteText(streamUrl: String): List<String> {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return try {
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().toList()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun formatKbps(kbps: Int?): String? {
        return kbps?.takeIf { it > 0 }?.let { "$it kbps" }
    }

    private fun readFirstNumber(value: String?): Int? {
        return value?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
    }

    private const val USER_AGENT = "OmniBeat"
}

object IcyMetadataParser {
    fun readStreamTitle(metadataText: String): String? {
        val marker = "StreamTitle="
        var start = metadataText.indexOf(marker)
        if (start < 0) {
            return null
        }
        start += marker.length
        while (start < metadataText.length && metadataText[start] in charArrayOf('\'', '"', ' ')) {
            start++
        }
        var end = metadataText.indexOf(';', start)
        if (end < 0) {
            end = metadataText.length
        }
        return metadataText
            .substring(start, end)
            .trim()
            .trimEnd('\'', '"')
    }
}
