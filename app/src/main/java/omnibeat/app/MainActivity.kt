package omnibeat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import omnibeat.app.ui.OmniBeatApp
import omnibeat.app.playback.PlaybackService

class MainActivity : ComponentActivity() {
    private val closeAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(closeAppReceiver, IntentFilter(ACTION_CLOSE_APP), Context.RECEIVER_NOT_EXPORTED)
        setContent {
            OmniBeatApp()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(closeAppReceiver)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CLOSE_APP = "omnibeat.app.action.CLOSE_APP"

        fun closeApp(context: Context) {
            PlaybackService.stop(context)
            context.sendBroadcast(Intent(ACTION_CLOSE_APP).setPackage(context.packageName))
        }
    }
}
