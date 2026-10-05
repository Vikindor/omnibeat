package omnibeat.app.ui

import androidx.compose.runtime.Composable

interface AppUpdateProvider {
    @Composable
    fun rememberUpdates(): AppUpdateActions
}

data class AppUpdateActions(
    val statusText: String?,
    val onCheck: () -> Unit,
)
