package omnibeat.app.stream

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class ResolvedStream(
    val playableUrl: String,
    val bitrateKbps: Int?,
)

private data class PlaylistResponse(
    val lines: List<String>?,
    val bitrateKbps: Int?,
)

private object StreamResolverContract {
    object Extension {
        const val PLS = ".pls"
        const val XSPF = ".xspf"
        const val ASX = ".asx"
        const val WAX = ".wax"
        const val WMX = ".wmx"
        const val M3U = ".m3u"
        const val M3U8 = ".m3u8"
        const val MPD = ".mpd"
    }

    object PathMarker {
        const val XSPF = "/xspf/"
    }

    object Pls {
        const val FILE_PREFIX = "file"
        const val BITRATE_PREFIX = "bitrate"
    }

    object Xml {
        const val LOCATION = "location"
        const val REF = "ref"
        const val HREF = "href"
        const val ANY_TAG = "*"
        const val DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl"
        const val EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
        const val EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
    }

    object Header {
        const val USER_AGENT = "User-Agent"
        const val ICY_METADATA = "Icy-MetaData"
        const val ICY_BITRATE = "icy-br"
    }

    object ContentType {
        const val AUDIO_PREFIX = "audio/"
        val PLAYLIST_AUDIO_TYPES = setOf(
            "audio/x-scpls",
            "audio/scpls",
            "audio/mpegurl",
            "audio/x-mpegurl",
            "audio/vnd.apple.mpegurl",
        )
    }
}

object StreamResolver {
    @Throws(IOException::class)
    fun resolveStream(streamUrl: String): ResolvedStream {
        val lower = streamUrl.lowercase(Locale.US)
        return when {
            StreamResolverContract.Extension.PLS in lower -> resolvePls(streamUrl)
            StreamResolverContract.Extension.XSPF in lower ||
                StreamResolverContract.PathMarker.XSPF in lower -> resolveXspf(streamUrl)
            StreamResolverContract.Extension.ASX in lower ||
                StreamResolverContract.Extension.WAX in lower ||
                StreamResolverContract.Extension.WMX in lower -> resolveAsx(streamUrl)
            StreamResolverContract.Extension.M3U in lower &&
                StreamResolverContract.Extension.M3U8 !in lower -> resolveM3u(streamUrl)
            StreamResolverContract.Extension.M3U8 in lower -> ResolvedStream(streamUrl, null)
            StreamResolverContract.Extension.MPD in lower -> ResolvedStream(streamUrl, null)
            else -> ResolvedStream(streamUrl, null)
        }
    }

    @Throws(IOException::class)
    private fun resolvePls(streamUrl: String): ResolvedStream {
        val response = readPlaylistResponse(streamUrl)
        val lines = response.lines ?: return ResolvedStream(streamUrl, response.bitrateKbps)
        lines.forEach { line ->
            val trimmed = line.trim()
            val equals = trimmed.indexOf('=')
            if (
                equals > 0 &&
                trimmed.substring(0, equals).lowercase(Locale.US).startsWith(StreamResolverContract.Pls.FILE_PREFIX)
            ) {
                val url = trimmed.substring(equals + 1).trim()
                if (url.startsWith("http")) {
                    val index = trimmed.substring(4, equals).toIntOrNull()
                    return ResolvedStream(
                        playableUrl = url,
                        bitrateKbps = readPlsBitrate(lines, index),
                    )
                }
            }
        }
        throw IOException("PLS playlist has no stream URL")
    }

    @Throws(IOException::class)
    private fun resolveM3u(streamUrl: String): ResolvedStream {
        val response = readPlaylistResponse(streamUrl)
        val lines = response.lines ?: return ResolvedStream(streamUrl, response.bitrateKbps)
        readM3uCandidates(lines, streamUrl).firstOrNull()?.let { candidate ->
            return ResolvedStream(candidate, null)
        }
        throw IOException("M3U playlist has no stream URL")
    }

    @Throws(IOException::class)
    private fun resolveXspf(streamUrl: String): ResolvedStream {
        val response = readPlaylistResponse(streamUrl)
        val lines = response.lines ?: return ResolvedStream(streamUrl, response.bitrateKbps)
        val xml = lines.joinToString("\n")
        readXspfLocation(xml)?.let { location ->
            return ResolvedStream(URL(URL(streamUrl), location).toString(), null)
        }
        throw IOException("XSPF playlist has no stream URL")
    }

    @Throws(IOException::class)
    private fun resolveAsx(streamUrl: String): ResolvedStream {
        val response = readPlaylistResponse(streamUrl)
        val lines = response.lines ?: return ResolvedStream(streamUrl, response.bitrateKbps)
        val xml = lines.joinToString("\n")
        readAsxRefHref(xml)?.let { href ->
            return ResolvedStream(URL(URL(streamUrl), href).toString(), null)
        }
        throw IOException("ASX playlist has no stream URL")
    }

    private fun readM3uCandidates(lines: List<String>, baseUrl: String): List<String> {
        return lines
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { URL(URL(baseUrl), it).toString() }
            .toList()
    }

    private fun readPlsBitrate(lines: List<String>, index: Int?): Int? {
        val expectedKey = index?.let { "${StreamResolverContract.Pls.BITRATE_PREFIX}$it" }
        lines.forEach { line ->
            val trimmed = line.trim()
            val equals = trimmed.indexOf('=')
            if (equals <= 0) return@forEach

            val key = trimmed.substring(0, equals).lowercase(Locale.US)
            if (
                key == expectedKey ||
                (expectedKey == null && key.startsWith(StreamResolverContract.Pls.BITRATE_PREFIX))
            ) {
                return trimmed.substring(equals + 1).trim().toIntOrNull()?.takeIf { it > 0 }
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun readPlaylistResponse(streamUrl: String): PlaylistResponse {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty(StreamResolverContract.Header.USER_AGENT, USER_AGENT)
        connection.setRequestProperty(StreamResolverContract.Header.ICY_METADATA, "1")
        return try {
            val contentType = connection.contentType
                .orEmpty()
                .lowercase(Locale.US)
                .substringBefore(";")
                .trim()
            val bitrateKbps = readFirstNumber(connection.getHeaderField(StreamResolverContract.Header.ICY_BITRATE))
            if (contentType.isLikelyAudioStream() || (contentType.isBlank() && bitrateKbps != null)) {
                return PlaylistResponse(lines = null, bitrateKbps = bitrateKbps)
            }
            val lines = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.lineSequence().toList()
            }
            PlaylistResponse(lines = lines, bitrateKbps = bitrateKbps)
        } finally {
            connection.disconnect()
        }
    }

    private fun readFirstNumber(value: String?): Int? {
        return value?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
    }

    private fun readXspfLocation(xml: String): String? {
        return readXmlElements(xml)
            .firstOrNull { it.localTagName().equals(StreamResolverContract.Xml.LOCATION, ignoreCase = true) }
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun readAsxRefHrefAttribute(xml: String): String? {
        return readXmlElements(xml)
            .firstOrNull { it.localTagName().equals(StreamResolverContract.Xml.REF, ignoreCase = true) }
            ?.readAttributeIgnoreCase(StreamResolverContract.Xml.HREF)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun readAsxRefHref(asx: String): String? {
        Regex(
            pattern = """<\s*ref\b[^>]*\bhref\s*=\s*(['"])(.*?)\1""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(asx)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        return runCatching {
            readAsxRefHrefAttribute(asx)
        }.getOrNull()
    }

    private fun readXmlElements(xml: String): Sequence<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setParserFeature(StreamResolverContract.Xml.DISALLOW_DOCTYPE_DECL, true)
            setParserFeature(StreamResolverContract.Xml.EXTERNAL_GENERAL_ENTITIES, false)
            setParserFeature(StreamResolverContract.Xml.EXTERNAL_PARAMETER_ENTITIES, false)
        }
        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val elements = document.getElementsByTagName(StreamResolverContract.Xml.ANY_TAG)
        return (0 until elements.length)
            .asSequence()
            .mapNotNull { elements.item(it) as? Element }
    }

    private fun Element.localTagName(): String {
        return tagName.substringAfter(':')
    }

    private fun Element.readAttributeIgnoreCase(attributeName: String): String? {
        val attributes = attributes
        repeat(attributes.length) { index ->
            val attribute = attributes.item(index)
            if (attribute.nodeName.equals(attributeName, ignoreCase = true)) {
                return attribute.nodeValue
            }
        }
        return null
    }

    private fun DocumentBuilderFactory.setParserFeature(feature: String, enabled: Boolean) {
        runCatching {
            setFeature(feature, enabled)
        }
    }

    private fun String.isLikelyAudioStream(): Boolean {
        return startsWith(StreamResolverContract.ContentType.AUDIO_PREFIX) &&
            this !in StreamResolverContract.ContentType.PLAYLIST_AUDIO_TYPES
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
