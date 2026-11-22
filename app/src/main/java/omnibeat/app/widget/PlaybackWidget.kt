package omnibeat.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import omnibeat.app.MainActivity
import omnibeat.app.R
import omnibeat.app.data.StationArtworkCache
import omnibeat.app.data.StationRepository
import omnibeat.app.model.ThemeMode
import omnibeat.app.playback.PlaybackService
import omnibeat.app.playback.PlaybackTrackStatus
import omnibeat.app.ui.radioColorScheme

class PlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaybackWidget()
}

private data class WidgetAppearance(val themeMode: ThemeMode, val marqueeTrackTitle: Boolean)

private data class WidgetContent(
    val playback: WidgetPlaybackState,
    val artwork: Bitmap?,
    val appearance: WidgetAppearance,
)

class PlaybackWidget : GlanceAppWidget() {
    override val stateDefinition = null
    override val sizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) = coroutineScope {
        val repository = StationRepository(context.applicationContext)
        val appearanceFlow = combine(
            repository.widgetFollowAppTheme,
            repository.widgetMarqueeTrackTitle,
            repository.themeMode,
        ) { followAppTheme, marqueeTrackTitle, themeMode ->
            WidgetAppearance(if (followAppTheme) themeMode else ThemeMode.System, marqueeTrackTitle)
        }.distinctUntilChanged()
        val appearance = appearanceFlow.stateIn(this, SharingStarted.Eagerly, appearanceFlow.first())
        val playbackState = PlaybackService.state
            .map(WidgetPlaybackState::from)
            .distinctUntilChanged()
            .stateIn(this, SharingStarted.Eagerly, WidgetPlaybackState.from(PlaybackService.state.value))
        val artwork = playbackState.map { it.imageUrl }.distinctUntilChanged()
            .flatMapLatest { url ->
                flow<Bitmap?> {
                    emit(null)
                    if (url != null) {
                        StationArtworkCache.updates(url).collect {
                            try {
                                emit(widgetArtwork(context, url))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Log.w("PlaybackWidget", "Cannot load station artwork", error)
                            }
                        }
                    }
                }
            }
            .stateIn(this, SharingStarted.Eagerly, null)

        provideContent {
            val playback by playbackState.collectAsState()
            val image by artwork.collectAsState()
            val preferences by appearance.collectAsState()
            GlanceTheme(
                colors = ColorProviders(
                    light = radioColorScheme(preferences.themeMode == ThemeMode.Dark),
                    dark = radioColorScheme(preferences.themeMode != ThemeMode.Light),
                ),
            ) {
                PlayerContent(context, WidgetContent(playback, image, preferences))
            }
        }
    }

    @Composable
    private fun PlayerContent(context: Context, content: WidgetContent) {
        val size = LocalSize.current
        val expanded = size.height >= 120.dp
        val showSkipButtons = size.width >= 200.dp
        val showArtwork = size.width >= 280.dp
        val padding = when {
            size.width < 120.dp || size.height < 64.dp -> 4.dp
            expanded && size.width >= 200.dp && size.height >= 168.dp -> 16.dp
            expanded && size.width >= 200.dp -> 12.dp
            else -> 8.dp
        }
        val preferredPlaySize = when {
            expanded && size.height >= 144.dp -> 64.dp
            size.height >= 72.dp && !expanded -> 56.dp
            else -> 48.dp
        }
        val skipButtonsWidth = if (showSkipButtons) 96.dp + (if (expanded) 24.dp else 0.dp) else 0.dp
        val playSize = minOf(preferredPlaySize, size.width - padding - padding - skipButtonsWidth)
        val modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(28.dp)
            .padding(padding)

        if (expanded) {
            val artworkSize = if (showArtwork) {
                (size.height - padding - padding - playSize - 8.dp).coerceIn(40.dp, 128.dp)
            } else {
                null
            }
            Column(modifier) {
                StationInfo(
                    context = context,
                    content = content,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    artworkSize = artworkSize,
                    spacious = size.width >= 200.dp && size.height >= 168.dp,
                    trackLines = if (size.height >= 200.dp) 2 else 1,
                )
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PlaybackControls(context, content.playback, playSize, expanded = true, showSkipButtons = showSkipButtons)
                }
            }
        } else {
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                StationInfo(
                    context = context,
                    content = content,
                    modifier = GlanceModifier.defaultWeight(),
                    artworkSize = if (showArtwork) minOf(56.dp, size.height - padding - padding) else null,
                )
                Spacer(GlanceModifier.width(8.dp))
                PlaybackControls(context, content.playback, playSize, expanded = false, showSkipButtons = showSkipButtons)
            }
        }
    }

    @Composable
    private fun StationInfo(
        context: Context,
        content: WidgetContent,
        modifier: GlanceModifier,
        artworkSize: Dp?,
        spacious: Boolean = false,
        trackLines: Int = 1,
    ) {
        Row(
            modifier.clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (artworkSize != null) {
                Artwork(content.artwork, artworkSize)
                Spacer(GlanceModifier.width(12.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    text = content.playback.stationTitle ?: context.getString(R.string.player_choose_station),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (spacious) 20.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(if (spacious) 6.dp else 2.dp))
                if (content.appearance.marqueeTrackTitle && content.playback.trackStatus == null) {
                    WidgetMarqueeTrackTitle(
                        context = context,
                        text = content.playback.trackText,
                        themeMode = content.appearance.themeMode,
                        spacious = spacious,
                    )
                } else {
                    Text(
                        text = trackText(context, content.playback),
                        maxLines = trackLines,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = if (spacious) 14.sp else 12.sp,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun Artwork(bitmap: Bitmap?, size: Dp) {
        Box(
            GlanceModifier.size(size)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(if (size >= 72.dp) 20.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_volume),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(if (size >= 72.dp) 36.dp else 24.dp),
                )
            }
        }
    }

    @Composable
    private fun PlaybackControls(
        context: Context,
        playback: WidgetPlaybackState,
        playSize: Dp,
        expanded: Boolean,
        showSkipButtons: Boolean,
    ) {
        val gap = if (expanded) 12.dp else 0.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showSkipButtons) {
                SkipButton(
                    context, R.drawable.ic_skip_previous, R.string.widget_previous,
                    PlaybackService.ACTION_PREVIOUS, playback.canSkip,
                )
                Spacer(GlanceModifier.width(gap))
            }
            SquareIconButton(
                imageProvider = ImageProvider(if (playback.active) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = context.getString(if (playback.active) R.string.widget_pause else R.string.widget_play),
                onClick = playbackAction(context, PlaybackService.ACTION_PLAY_PAUSE),
                modifier = GlanceModifier.size(playSize),
                backgroundColor = GlanceTheme.colors.primary,
                contentColor = GlanceTheme.colors.onPrimary,
            )
            if (showSkipButtons) {
                Spacer(GlanceModifier.width(gap))
                SkipButton(
                    context, R.drawable.ic_skip_next, R.string.widget_next,
                    PlaybackService.ACTION_NEXT, playback.canSkip,
                )
            }
        }
    }

    @Composable
    private fun SkipButton(context: Context, icon: Int, label: Int, command: String, enabled: Boolean) {
        CircleIconButton(
            imageProvider = ImageProvider(icon),
            contentDescription = context.getString(label),
            onClick = playbackAction(context, command),
            modifier = GlanceModifier.size(48.dp),
            enabled = enabled,
            backgroundColor = null,
            contentColor = if (enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
        )
    }

    private fun playbackAction(context: Context, command: String): Action = actionStartService(
        Intent(context, PlaybackService::class.java).setAction(command),
        isForegroundService = true,
    )

    private fun trackText(context: Context, playback: WidgetPlaybackState): String = when (playback.trackStatus) {
        PlaybackTrackStatus.Stopped -> context.getString(R.string.track_text_stopped)
        PlaybackTrackStatus.Paused -> context.getString(R.string.track_text_paused)
        PlaybackTrackStatus.LoadingStations -> context.getString(R.string.track_text_loading_stations)
        PlaybackTrackStatus.Resolving -> context.getString(R.string.track_text_resolving)
        PlaybackTrackStatus.WaitingMetadata -> context.getString(R.string.track_text_waiting_metadata)
        PlaybackTrackStatus.NoMetadata -> context.getString(R.string.track_text_no_metadata)
        null -> playback.trackText
    }

    companion object {
        internal fun requestUpdate(context: Context) {
            val receiver = ComponentName(context, PlaybackWidgetReceiver::class.java)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(receiver)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(receiver)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
