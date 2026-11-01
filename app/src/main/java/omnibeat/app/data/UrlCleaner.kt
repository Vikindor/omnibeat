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
    val uri = try {
        URI(streamUrl)
    } catch (_: URISyntaxException) {
        return streamUrl
    }
    val query = uri.rawQuery ?: return streamUrl
    val parameters = query.split("&")
    val retainedParameters = parameters.filter { part ->
        val key = part.substringBefore("=")
        !key.isTrackingQueryKey()
    }
    if (retainedParameters.size == parameters.size) return streamUrl

    val cleanedQuery = retainedParameters.joinToString("&")
    val queryStart = streamUrl.indexOf('?')
    val queryEnd = queryStart + 1 + query.length
    return buildString {
        append(streamUrl, 0, queryStart)
        if (cleanedQuery.isNotEmpty()) {
            append('?')
            append(cleanedQuery)
        }
        append(streamUrl, queryEnd, streamUrl.length)
    }
}

private fun String.isTrackingQueryKey(): Boolean {
    val key = lowercase()
    return key.startsWith("utm_") || key in TRACKING_QUERY_KEYS
}
