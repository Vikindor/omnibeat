package omnibeat.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RadioBrowserStation(
    val stationUuid: String,
    val title: String,
    val streamUrl: String,
    val tags: List<String>,
    val countryCode: String,
    val codec: String,
    val bitrate: Int,
)

fun RadioBrowserStation.stationTags(): List<String> {
    return buildList {
        countryCode.takeIf { it.isNotBlank() }?.let(::add)
        codec.takeIf { it.isNotBlank() }?.let(::add)
        bitrate.takeIf { it > 0 }?.let { add("$it kbps") }
        addAll(tags.take(4))
    }.distinctBy { it.lowercase() }
}

data class RadioBrowserFilterOption(
    val name: String,
    val code: String = "",
)

enum class RadioBrowserSort(val label: String, val apiValue: String) {
    Clicks("Clicks", "clickcount"),
    Votes("Votes", "votes"),
    Name("Name", "name"),
    Bitrate("Bitrate", "bitrate"),
    Country("Country", "country"),
    Random("Random", "random"),
}

data class RadioBrowserSearchParams(
    val name: String,
    val tags: String,
    val country: RadioBrowserFilterOption?,
    val language: RadioBrowserFilterOption?,
    val sort: RadioBrowserSort,
    val bitrateMin: Int?,
    val bitrateMax: Int?,
    val reverse: Boolean,
    val includeBroken: Boolean,
)

class RadioBrowserClient {
    private var cachedBaseUrl: String? = null
    private var cachedCountries: List<RadioBrowserFilterOption>? = null
    private var cachedLanguages: List<RadioBrowserFilterOption>? = null

    suspend fun searchStations(params: RadioBrowserSearchParams): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        val query = buildQuery(
            listOfNotNull(
                "name" to params.name.trim(),
                params.tags.trim().takeIf { it.isNotBlank() }?.let { tags ->
                    "tagList" to tags.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(",")
                },
                params.country?.let { country ->
                    if (country.code.isNotBlank()) "countrycode" to country.code else "country" to country.name
                },
                params.language?.let { "language" to it.name },
                "order" to params.sort.apiValue,
                "reverse" to params.reverse.toString(),
                "hidebroken" to (!params.includeBroken).toString(),
                params.bitrateMin?.let { "bitrateMin" to it.toString() },
                params.bitrateMax?.let { "bitrateMax" to it.toString() },
                "limit" to "40",
            ).filter { it.second.isNotBlank() },
        )
        decodeStations(readRadioBrowserText("/json/stations/search?$query"))
    }

    suspend fun countries(): List<RadioBrowserFilterOption> = withContext(Dispatchers.IO) {
        cachedCountries ?: decodeFilterOptions(
            responseText = readRadioBrowserText("/json/countries?hidebroken=true&order=stationcount&reverse=true"),
            codeKeys = listOf("iso_3166_1", "countrycode"),
        ).also { cachedCountries = it }
    }

    suspend fun languages(): List<RadioBrowserFilterOption> = withContext(Dispatchers.IO) {
        cachedLanguages ?: decodeFilterOptions(
            responseText = readRadioBrowserText("/json/languages?hidebroken=true&order=stationcount&reverse=true"),
            codeKeys = emptyList(),
        ).also { cachedLanguages = it }
    }

    private fun readRadioBrowserText(path: String): String {
        val errors = mutableListOf<String>()
        for (baseUrl in resolveBaseUrlCandidates()) {
            val result = runCatching { readText(URL("$baseUrl$path")) }
            if (result.isSuccess) {
                cachedBaseUrl = baseUrl
                return result.getOrThrow()
            }
            errors += "$baseUrl: ${result.exceptionOrNull()?.message ?: "request failed"}"
        }
        error("Radio Browser request failed: ${errors.joinToString("; ")}")
    }

    private fun resolveBaseUrlCandidates(): List<String> {
        cachedBaseUrl?.let { return listOf(it) + FALLBACK_BASE_URLS.filterNot { fallback -> fallback == it } }
        val indexServer = runCatching {
            val servers = JSONArray(readText(URL("$SERVER_INDEX_URL/json/servers"), connectTimeout = 4_000, readTimeout = 4_000))
            servers.optJSONObject(0)
                ?.optString("name")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://$it" }
        }.getOrNull()
        return (listOfNotNull(indexServer) + FALLBACK_BASE_URLS).distinct()
    }

    private fun readText(
        requestUrl: URL,
        connectTimeout: Int = 8_000,
        readTimeout: Int = 8_000,
    ): String {
        val connection = requestUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "OmniBeat Android")

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("Radio Browser returned HTTP $responseCode")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeStations(responseText: String): List<RadioBrowserStation> {
        val items = JSONArray(responseText)
        return buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val title = item.optString("name").trim()
                val streamUrl = item.optString("url_resolved").trim().ifBlank { item.optString("url").trim() }
                if (title.isBlank() || streamUrl.isBlank()) {
                    return@repeat
                }
                add(
                    RadioBrowserStation(
                        stationUuid = item.optString("stationuuid").trim(),
                        title = title,
                        streamUrl = streamUrl,
                        tags = item.optString("tags")
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { it.lowercase() },
                        countryCode = item.optString("countrycode").trim(),
                        codec = item.optString("codec").trim(),
                        bitrate = item.optInt("bitrate", 0),
                    ),
                )
            }
        }
    }

    private fun decodeFilterOptions(
        responseText: String,
        codeKeys: List<String>,
    ): List<RadioBrowserFilterOption> {
        val items = JSONArray(responseText)
        return buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val name = item.optString("name").trim()
                if (name.isBlank()) return@repeat
                add(
                    RadioBrowserFilterOption(
                        name = name,
                        code = codeKeys.firstNotNullOfOrNull { key ->
                            item.optString(key).trim().takeIf { it.isNotBlank() }
                        }.orEmpty(),
                    ),
                )
            }
        }
    }

    private fun buildQuery(params: List<Pair<String, String>>): String {
        return params.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private companion object {
        const val SERVER_INDEX_URL = "https://all.api.radio-browser.info"
        //const val SERVER_INDEX_URL = "https://wrongAll.api.radio-browser.info"
        val FALLBACK_BASE_URLS = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
        )
//        val FALLBACK_BASE_URLS = listOf(
//            "https://wrong1.api.radio-browser.info",
//            "https://wrong2.api.radio-browser.info",
//        )
    }
}
