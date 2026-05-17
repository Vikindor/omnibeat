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
        decodeStations(readText(URL("${resolveBaseUrl()}/json/stations/search?$query")))
    }

    suspend fun countries(): List<RadioBrowserFilterOption> = withContext(Dispatchers.IO) {
        cachedCountries ?: decodeFilterOptions(
            responseText = readText(URL("${resolveBaseUrl()}/json/countries?hidebroken=true&order=stationcount&reverse=true")),
            codeKeys = listOf("iso_3166_1", "countrycode"),
        ).also { cachedCountries = it }
    }

    suspend fun languages(): List<RadioBrowserFilterOption> = withContext(Dispatchers.IO) {
        cachedLanguages ?: decodeFilterOptions(
            responseText = readText(URL("${resolveBaseUrl()}/json/languages?hidebroken=true&order=stationcount&reverse=true")),
            codeKeys = emptyList(),
        ).also { cachedLanguages = it }
    }

    private fun resolveBaseUrl(): String {
        cachedBaseUrl?.let { return it }
        val resolved = runCatching {
            val servers = JSONArray(readText(URL("$SERVER_INDEX_URL/json/servers"), connectTimeout = 4_000, readTimeout = 4_000))
            servers.optJSONObject(0)
                ?.optString("name")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://$it" }
                ?: FALLBACK_BASE_URL
        }.getOrDefault(FALLBACK_BASE_URL)
        cachedBaseUrl = resolved
        return resolved
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
        //const val SERVER_INDEX_URL = "https://wrong.api.radio-browser.info"
        const val FALLBACK_BASE_URL = "https://de1.api.radio-browser.info"
        //const val FALLBACK_BASE_URL = "https://wrong.api.radio-browser.info"
    }
}
