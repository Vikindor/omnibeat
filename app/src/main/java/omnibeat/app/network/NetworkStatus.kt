package omnibeat.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

const val NO_INTERNET_MESSAGE = "No internet connection"

object NetworkStatus {
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
