package omnibeat.app.data

import java.net.URI

private val TRACKING_QUERY_KEYS = setOf(
    "fbclid",
    "gclid",
    "ref",
    "referrer",
    "yclid",
)

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
