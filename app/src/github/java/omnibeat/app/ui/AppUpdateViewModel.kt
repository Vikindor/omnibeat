package omnibeat.app.ui

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import omnibeat.app.R
import omnibeat.app.data.GitHubRelease
import omnibeat.app.data.GitHubUpdates
import omnibeat.app.data.appString
import java.io.IOException

data class AppUpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val release: GitHubRelease? = null,
    val downloadedApk: Uri? = null,
    val error: String? = null,
    val message: Int? = null,
)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("github_updates", Context.MODE_PRIVATE)
    private val downloads = application.getSystemService(DownloadManager::class.java)
    private val updates = GitHubUpdates()
    private val installedVersion = application.packageManager.getPackageInfo(
        application.packageName, PackageManager.PackageInfoFlags.of(0),
    ).versionName.orEmpty()
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != -1L && id == preferences.getLong("download_id", -1)) monitorDownload(id)
        }
    }

    init {
        application.registerReceiver(
            downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED,
        )
        val id = preferences.getLong("download_id", -1)
        val downloadInstalledVersion = preferences.getString("download_installed_version", null)
        if (id != -1L && downloadInstalledVersion == installedVersion) {
            mutableState.update { it.copy(downloading = true) }
            monitorDownload(id)
        } else {
            clearDownload()
        }
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (state.value.checking || (manual && state.value.downloading)) return
        mutableState.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val release = updates.newerRelease(installedVersion)
                mutableState.update {
                    it.copy(
                        release = if (it.downloading || it.downloadedApk != null) null else release,
                        message = if (manual && release == null) R.string.update_latest else null,
                    )
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                if (manual) showError(exception)
            } finally {
                mutableState.update { it.copy(checking = false) }
            }
        }
    }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.downloading) return
        mutableState.update { it.copy(release = null, downloading = true) }
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    val filename = "omnibeat-${release.version}-${System.currentTimeMillis()}.apk"
                    val request = DownloadManager.Request(Uri.parse(release.apkUrl))
                        .setTitle("OmniBeat ${release.version}")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    downloads.enqueue(request).also { downloadId ->
                        preferences.edit().putLong("download_id", downloadId)
                            .putString("download_installed_version", installedVersion).commit()
                    }
                }
                mutableState.update { it.copy(message = R.string.update_downloading) }
                monitorDownload(id)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                mutableState.update { it.copy(downloading = false) }
                showError(exception)
            }
        }
    }

    private fun monitorDownload(id: Long) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        if (!cursor.moveToFirst()) throw IOException("Download no longer exists")
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_FAILED) {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            throw IOException("DownloadManager: $reason")
                        }
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uri = downloads.getUriForDownloadedFile(id)
                                ?: throw IOException("Downloaded APK is unavailable")
                            getApplication<Application>().contentResolver.openFileDescriptor(uri, "r").use {
                                if (it == null) throw IOException("Downloaded APK is unavailable")
                            }
                            uri
                        } else null
                    }
                }
                if (result != null) {
                    mutableState.update { it.copy(downloading = false, downloadedApk = result) }
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                clearDownload()
                mutableState.update { it.copy(downloading = false) }
                showError(exception)
            }
        }
    }

    private fun clearDownload() {
        preferences.edit().remove("download_id").remove("download_installed_version").apply()
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(downloadReceiver)
        super.onCleared()
    }

    fun dismissRelease() { mutableState.update { it.copy(release = null) } }
    fun dismissDownloadedApk() { mutableState.update { it.copy(downloadedApk = null) } }
    fun dismissError() { mutableState.update { it.copy(error = null) } }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun showError(exception: Exception) {
        val resources = getApplication<Application>().resources
        mutableState.update {
            it.copy(error = resources.appString(R.string.update_failed, exception.message ?: exception.javaClass.simpleName))
        }
    }
}
