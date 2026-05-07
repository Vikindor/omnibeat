package omnibeat.app

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

object StreamResolver {
    @Throws(IOException::class)
    fun resolvePlayableUrl(streamUrl: String): String {
        val lower = streamUrl.lowercase(Locale.US)
        return when {
            ".pls" in lower -> resolvePls(streamUrl)
            ".m3u" in lower && ".m3u8" !in lower -> resolveM3u(streamUrl)
            else -> streamUrl
        }
    }

    @Throws(IOException::class)
    private fun resolvePls(streamUrl: String): String {
        readRemoteText(streamUrl).forEach { line ->
            val trimmed = line.trim()
            val equals = trimmed.indexOf('=')
            if (equals > 0 && trimmed.substring(0, equals).lowercase(Locale.US).startsWith("file")) {
                val url = trimmed.substring(equals + 1).trim()
                if (url.startsWith("http")) {
                    return url
                }
            }
        }
        throw IOException("PLS playlist has no stream URL")
    }

    @Throws(IOException::class)
    private fun resolveM3u(streamUrl: String): String {
        readRemoteText(streamUrl).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                return URL(URL(streamUrl), trimmed).toString()
            }
        }
        throw IOException("M3U playlist has no stream URL")
    }

    @Throws(IOException::class)
    private fun readRemoteText(streamUrl: String): List<String> {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "OmniBeat/0.2.0")
        return try {
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().toList()
            }
        } finally {
            connection.disconnect()
        }
    }
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
