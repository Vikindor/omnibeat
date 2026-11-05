package omnibeat.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.fillMaxWidth
import omnibeat.app.MainActivity
import omnibeat.app.R
import omnibeat.app.model.ThemeMode
import omnibeat.app.ui.radioColorScheme

@Composable
internal fun WidgetMarqueeTrackTitle(context: Context, text: String, themeMode: ThemeMode, spacious: Boolean) {
    val views = remember(context, text, themeMode, spacious) {
        RemoteViews(context.packageName, R.layout.widget_marquee_track_title).apply {
            setTextViewText(R.id.widget_track_title, text)
            setTextViewTextSize(R.id.widget_track_title, TypedValue.COMPLEX_UNIT_SP, if (spacious) 14f else 12f)
            setColorInt(
                R.id.widget_track_title,
                "setTextColor",
                radioColorScheme(themeMode == ThemeMode.Dark).onSurfaceVariant.toArgb(),
                radioColorScheme(themeMode != ThemeMode.Light).onSurfaceVariant.toArgb(),
            )
            setOnClickPendingIntent(
                R.id.widget_track_title,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }
    AndroidRemoteViews(remoteViews = views, modifier = GlanceModifier.fillMaxWidth())
}
