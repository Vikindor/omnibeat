package omnibeat.app.data

import java.net.URI
import java.net.URISyntaxException

private val TRACKING_QUERY_KEYS = setOf(
    "fbclid",
    "gclid",
    "ref",
    "referrer",
    "yclid",
)

fun normalizeStreamUrl(streamUrl: String): String {
    val trimmedUrl = streamUrl.trim()
    val uri = try {
        URI(trimmedUrl)
    } catch (_: URISyntaxException) {
        return trimmedUrl
    }
    if (!uri.scheme.equals("http", ignoreCase = true) && !uri.scheme.equals("https", ignoreCase = true)) {
        return trimmedUrl
    }
    val authority = uri.rawAuthority?.takeIf { it.isNotBlank() } ?: return trimmedUrl
    if (!uri.rawPath.isNullOrEmpty()) return trimmedUrl
    val pathStart = trimmedUrl.indexOf("://") + 3 + authority.length
    return trimmedUrl.substring(0, pathStart) + "/" + trimmedUrl.substring(pathStart)
}

fun removeTrackingParameters(streamUrl: String): String {
    return runCatching {
        val uri = URI(streamUrl)
        val query = uri.rawQuery ?: return@runCatching streamUrl
        val cleanedQuery = query
            .split("&")
            .filter { part ->
                val key = part.substringBefore("=", missingDelimiterValue = part)
                !key.isTrackingQueryKey()
            }
            .joinToString("&")
            .takeIf { it.isNotBlank() }

        URI(uri.scheme, uri.rawAuthority, uri.rawPath, cleanedQuery, uri.rawFragment).toString()
    }.getOrDefault(streamUrl)
}

private fun String.isTrackingQueryKey(): Boolean {
    val key = lowercase()
    return key.startsWith("utm_") || key in TRACKING_QUERY_KEYS
}
