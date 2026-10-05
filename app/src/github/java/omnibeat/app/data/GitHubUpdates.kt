package omnibeat.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

data class GitHubRelease(val version: String, val apkUrl: String)

class GitHubUpdates {
    suspend fun newerRelease(installedVersion: String): GitHubRelease? = withContext(Dispatchers.IO) {
        val connection = URI("https://api.github.com/repos/Vikindor/omnibeat/releases/latest")
            .toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "OmniBeat/$installedVersion")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub: HTTP $status ${connection.responseMessage}")
            }
            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val version = release.getString("tag_name").removePrefix("v")
            if (release.getBoolean("draft") || release.getBoolean("prerelease") ||
                compareVersions(version, installedVersion) <= 0
            ) return@withContext null

            val assets = release.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.getString("name").endsWith(".apk", ignoreCase = true) }
            val apk = apks.filter { it.getString("name").contains("github", ignoreCase = true) }
                .singleOrNull() ?: apks.singleOrNull()
                ?: throw IOException("GitHub release $version must contain one GitHub APK")
            val url = apk.getString("browser_download_url")
            val uri = URI(url)
            if (uri.scheme != "https" || uri.host != "github.com" ||
                !uri.path.startsWith("/Vikindor/omnibeat/releases/download/")
            ) throw IOException("Invalid GitHub APK URL")
            GitHubRelease(version, url)
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        fun parts(version: String): List<Int> {
            if (!version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
                throw IOException("Unsupported version: $version")
            }
            return version.split('.').map(String::toInt)
        }
        return parts(first).zip(parts(second)).firstOrNull { (a, b) -> a != b }
            ?.let { (a, b) -> a.compareTo(b) } ?: 0
    }
}
