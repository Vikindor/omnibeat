package omnibeat.app.radio

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
    Clicks("Clicks", RadioBrowserApi.Sort.CLICK_COUNT),
    Votes("Votes", RadioBrowserApi.Sort.VOTES),
    Name("Name", RadioBrowserApi.Sort.NAME),
    Bitrate("Bitrate", RadioBrowserApi.Sort.BITRATE),
    Country("Country", RadioBrowserApi.Sort.COUNTRY),
    Random("Random", RadioBrowserApi.Sort.RANDOM),
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

private object RadioBrowserApi {
    object Path {
        const val SERVERS = "/json/servers"
        const val STATIONS_SEARCH = "/json/stations/search"
        const val COUNTRIES = "/json/countries"
        const val LANGUAGES = "/json/languages"
    }

    object Query {
        const val NAME = "name"
        const val TAG_LIST = "tagList"
        const val COUNTRY = "country"
        const val COUNTRY_CODE = "countrycode"
        const val LANGUAGE = "language"
        const val ORDER = "order"
        const val REVERSE = "reverse"
        const val HIDE_BROKEN = "hidebroken"
        const val BITRATE_MIN = "bitrateMin"
        const val BITRATE_MAX = "bitrateMax"
        const val LIMIT = "limit"
    }

    object Sort {
        const val CLICK_COUNT = "clickcount"
        const val VOTES = "votes"
        const val NAME = "name"
        const val BITRATE = "bitrate"
        const val COUNTRY = "country"
        const val RANDOM = "random"
        const val STATION_COUNT = "stationcount"
    }

    object Json {
        const val NAME = "name"
        const val URL_RESOLVED = "url_resolved"
        const val URL = "url"
        const val STATION_UUID = "stationuuid"
        const val TAGS = "tags"
        const val COUNTRY_CODE = "countrycode"
        const val CODEC = "codec"
        const val BITRATE = "bitrate"
        const val ISO_3166_1 = "iso_3166_1"
    }

    object Value {
        const val TRUE = "true"
        const val SEARCH_LIMIT = "40"
    }
}

class RadioBrowserClient {
    private var cachedBaseUrl: String? = null
    private var cachedCountries: List<RadioBrowserFilterOption>? = null
    private var cachedLanguages: List<RadioBrowserFilterOption>? = null

    suspend fun searchStations(params: RadioBrowserSearchParams): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        val query = buildQuery(
            listOfNotNull(
                RadioBrowserApi.Query.NAME to params.name.trim(),
                params.tags.trim().takeIf { it.isNotBlank() }?.let { tags ->
                    RadioBrowserApi.Query.TAG_LIST to tags.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(",")
                },
                params.country?.let { country ->
                    if (country.code.isNotBlank()) {
                        RadioBrowserApi.Query.COUNTRY_CODE to country.code
                    } else {
                        RadioBrowserApi.Query.COUNTRY to country.name
                    }
                },
                params.language?.let { RadioBrowserApi.Query.LANGUAGE to it.name },
                RadioBrowserApi.Query.ORDER to params.sort.apiValue,
                RadioBrowserApi.Query.REVERSE to params.reverse.toString(),
                RadioBrowserApi.Query.HIDE_BROKEN to (!params.includeBroken).toString(),
                params.bitrateMin?.let { RadioBrowserApi.Query.BITRATE_MIN to it.toString() },
                params.bitrateMax?.let { RadioBrowserApi.Query.BITRATE_MAX to it.toString() },
                RadioBrowserApi.Query.LIMIT to RadioBrowserApi.Value.SEARCH_LIMIT,
            ).filter { it.second.isNotBlank() },
        )
        decodeStations(readRadioBrowserText("${RadioBrowserApi.Path.STATIONS_SEARCH}?$query"))
    }

    suspend fun countries(): List<RadioBrowserFilterOption> = withContext(Dispatchers.IO) {
        val query = buildDefaultFilterQuery()
        cachedCountries ?: decodeFilterOptions(
            responseText = readRadioBrowserText("${RadioBrowserApi.Path.COUNTRIES}?$query"),
            codeKeys = listOf(RadioBrowserApi.Json.ISO_3166_1, RadioBrowserApi.Json.COUNTRY_CODE),
        ).also { cachedCountries = it }
    }

    suspend fun languages(): List<RadioBrowserFilterOption> = withContext(Dispatchers.IO) {
        val query = buildDefaultFilterQuery()
        cachedLanguages ?: decodeFilterOptions(
            responseText = readRadioBrowserText("${RadioBrowserApi.Path.LANGUAGES}?$query"),
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
            val servers = JSONArray(
                readText(
                    URL("$SERVER_INDEX_URL${RadioBrowserApi.Path.SERVERS}"),
                    connectTimeout = 4_000,
                    readTimeout = 4_000,
                ),
            )
            servers.optJSONObject(0)
                ?.optString(RadioBrowserApi.Json.NAME)
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
                val title = item.optString(RadioBrowserApi.Json.NAME).trim()
                val streamUrl = item.optString(RadioBrowserApi.Json.URL_RESOLVED)
                    .trim()
                    .ifBlank { item.optString(RadioBrowserApi.Json.URL).trim() }
                if (title.isBlank() || streamUrl.isBlank()) {
                    return@repeat
                }
                add(
                    RadioBrowserStation(
                        stationUuid = item.optString(RadioBrowserApi.Json.STATION_UUID).trim(),
                        title = title,
                        streamUrl = streamUrl,
                        tags = item.optString(RadioBrowserApi.Json.TAGS)
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { it.lowercase() },
                        countryCode = item.optString(RadioBrowserApi.Json.COUNTRY_CODE).trim(),
                        codec = item.optString(RadioBrowserApi.Json.CODEC).trim(),
                        bitrate = item.optInt(RadioBrowserApi.Json.BITRATE, 0),
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
                val name = item.optString(RadioBrowserApi.Json.NAME).trim()
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

    private fun buildDefaultFilterQuery(): String {
        return buildQuery(
            listOf(
                RadioBrowserApi.Query.HIDE_BROKEN to RadioBrowserApi.Value.TRUE,
                RadioBrowserApi.Query.ORDER to RadioBrowserApi.Sort.STATION_COUNT,
                RadioBrowserApi.Query.REVERSE to RadioBrowserApi.Value.TRUE,
            ),
        )
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
