package omnibeat.app

import android.app.Application
import androidx.compose.runtime.Composable
import omnibeat.app.ui.AppUpdateActions
import omnibeat.app.ui.AppUpdateProvider
import omnibeat.app.ui.rememberAppUpdates

class GitHubApplication : Application(), AppUpdateProvider {
    @Composable
    override fun rememberUpdates(): AppUpdateActions = rememberAppUpdates()
}
