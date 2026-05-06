package allradio.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllRadioApp(stations = sampleStations())
        }
    }
}

private data class Station(
    val name: String,
    val formatLabel: String,
    val sourceUrl: String,
)

private val RadioBackground = Color(0xFF0D0D10)
private val RadioSurface = Color(0xFF18181D)
private val RadioSurfaceHigh = Color(0xFF24242B)
private val RadioOutline = Color(0xFF353540)
private val RadioPrimary = Color(0xFF8F5CFF)
private val RadioPrimaryDark = Color(0xFF3F236E)
private val RadioText = Color(0xFFF4F1FA)
private val RadioTextMuted = Color(0xFFB7B2C3)

private val RadioColorScheme = darkColorScheme(
    primary = RadioPrimary,
    onPrimary = Color.White,
    secondary = RadioPrimary,
    onSecondary = Color.White,
    background = RadioBackground,
    onBackground = RadioText,
    surface = RadioSurface,
    onSurface = RadioText,
    surfaceVariant = RadioSurfaceHigh,
    onSurfaceVariant = RadioTextMuted,
    outline = RadioOutline,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllRadioApp(stations: List<Station>) {
    MaterialTheme(colorScheme = RadioColorScheme) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val player = remember {
            ExoPlayer.Builder(context).build()
        }

        var selectedIndex by remember { mutableIntStateOf(-1) }
        var selectedStation by remember { mutableStateOf<Station?>(null) }
        var playableUrl by remember { mutableStateOf("") }
        var trackText by remember { mutableStateOf("No stream is playing") }
        var resolving by remember { mutableStateOf(false) }
        var buffering by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf<String?>(null) }
        var appVolume by remember { mutableFloatStateOf(0.75f) }
        var isPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(player, appVolume) {
            player.volume = appVolume
        }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == Player.STATE_BUFFERING
                    isPlaying = player.isPlaying
                }

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title?.toString().orEmpty()
                    val artist = mediaMetadata.artist?.toString().orEmpty()
                    if (title.isNotBlank()) {
                        trackText = if (artist.isNotBlank()) "$artist - $title" else title
                    }
                }

                override fun onMetadata(metadata: Metadata) {
                    repeat(metadata.length()) { index ->
                        val streamTitle = readIcyStreamTitle(metadata[index].toString())
                        if (!streamTitle.isNullOrBlank()) {
                            trackText = streamTitle
                            return
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    errorText = error.message ?: "Playback error"
                    resolving = false
                    buffering = false
                }
            }

            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }

        LaunchedEffect(errorText) {
            errorText?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                DrawerContent()
            },
        ) {
            Scaffold(
                containerColor = RadioBackground,
                topBar = {
                    TopAppBar(
                        title = { Text("AllRadio", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = RadioBackground,
                            titleContentColor = RadioText,
                            navigationIconContentColor = RadioText,
                        ),
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                },
                bottomBar = {
                    PlayerPanel(
                        station = selectedStation,
                        playableUrl = playableUrl,
                        trackText = errorText ?: trackText,
                        loading = resolving || buffering,
                        resolving = resolving,
                        isPlaying = isPlaying,
                        appVolume = appVolume,
                        onPlayPause = {
                            if (player.isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.play()
                            }
                        },
                        onVolumeChange = { appVolume = it },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Text(
                        text = "Stations",
                        color = RadioText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    StationList(
                        stations = stations,
                        selectedIndex = selectedIndex,
                        onStationClick = { index, station ->
                            selectedIndex = index
                            selectedStation = station
                            playableUrl = station.sourceUrl
                            trackText = "Resolving stream..."
                            errorText = null
                            resolving = true
                            buffering = false
                            scope.launch { drawerState.close() }
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        resolvePlayableUrl(station.sourceUrl)
                                    }
                                }.onSuccess { resolvedUrl ->
                                    resolving = false
                                    playableUrl = resolvedUrl
                                    trackText = "Waiting for metadata..."
                                    player.setMediaItem(MediaItem.fromUri(Uri.parse(resolvedUrl)))
                                    player.prepare()
                                    player.play()
                                }.onFailure { error ->
                                    resolving = false
                                    buffering = false
                                    errorText = "Could not resolve stream: ${error.message}"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StationList(
    stations: List<Station>,
    selectedIndex: Int,
    onStationClick: (Int, Station) -> Unit,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(stations) { index, station ->
                StationRow(
                    station = station,
                    selected = selectedIndex == index,
                    onClick = { onStationClick(index, station) },
                )
            }
        }
        StationScrollIndicator(
            listState = listState,
            itemCount = stations.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        )
    }
}

@Composable
private fun StationScrollIndicator(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (itemCount == 0 || visibleItems.isEmpty() || visibleItems.size >= itemCount) {
        return
    }

    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat().coerceAtLeast(1f)
    val averageItemHeight = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val contentHeight = averageItemHeight * itemCount
    val scrollOffset = listState.firstVisibleItemIndex * averageItemHeight + listState.firstVisibleItemScrollOffset
    val thumbHeightFraction = (viewportHeight / contentHeight).coerceIn(0.12f, 1f)
    val thumbTopFraction = (scrollOffset / (contentHeight - viewportHeight).coerceAtLeast(1f))
        .coerceIn(0f, 1f - thumbHeightFraction)

    Canvas(
        modifier = modifier
            .width(3.dp)
            .fillMaxHeight(),
    ) {
        drawRect(
            color = RadioOutline.copy(alpha = 0.55f),
            size = size,
        )
        drawRect(
            color = RadioPrimary,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = 0f,
                y = size.height * thumbTopFraction,
            ),
            size = androidx.compose.ui.geometry.Size(
                width = size.width,
                height = size.height * thumbHeightFraction,
            ),
        )
    }
}

@Composable
private fun DrawerContent() {
    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = RadioSurface,
        drawerContentColor = RadioText,
        modifier = Modifier
            .width(304.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp),
        ) {
            Text("AllRadio", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Prototype",
                color = RadioTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            DrawerLine("Stations")
            DrawerLine("Formats: direct, PLS, M3U, HLS")
            DrawerLine("Theme: dark Material 3")
        }
    }
}

@Composable
private fun DrawerLine(text: String) {
    NavigationDrawerItem(
        label = { Text(text) },
        selected = false,
        onClick = {},
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = RadioText,
        ),
    )
}

@Composable
private fun StationRow(
    station: Station,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) RadioPrimaryDark else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = station.name,
            color = RadioText,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = station.formatLabel,
            color = RadioTextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PlayerPanel(
    station: Station?,
    playableUrl: String,
    trackText: String,
    loading: Boolean,
    resolving: Boolean,
    isPlaying: Boolean,
    appVolume: Float,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioSurface)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DividerDefaults.Thickness)
                .background(RadioOutline),
        )
        Text(
            text = station?.name ?: "Choose a station",
            color = RadioText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = trackText,
            color = RadioTextMuted,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (playableUrl.isNotBlank()) {
            Text(
                text = playableUrl,
                color = RadioTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Button(
                enabled = station != null && !resolving,
                onClick = onPlayPause,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadioOutline,
                    contentColor = RadioText,
                    disabledContainerColor = RadioSurfaceHigh,
                    disabledContentColor = RadioTextMuted,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .width(122.dp)
                    .height(44.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = RadioPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(28.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = RadioPrimary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            VolumeSlider(
                volume = appVolume,
                onVolumeChange = onVolumeChange,
            )
        }
    }
}

@Composable
private fun VolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(122.dp)
            .height(44.dp)
            .background(RadioOutline, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = RadioText,
                activeTrackColor = RadioPrimary,
                inactiveTrackColor = RadioOutline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

@Throws(IOException::class)
private fun resolvePlayableUrl(sourceUrl: String): String {
    val lower = sourceUrl.lowercase(Locale.US)
    return when {
        ".pls" in lower -> resolvePls(sourceUrl)
        ".m3u" in lower && ".m3u8" !in lower -> resolveM3u(sourceUrl)
        else -> sourceUrl
    }
}

@Throws(IOException::class)
private fun resolvePls(sourceUrl: String): String {
    readRemoteText(sourceUrl).forEach { line ->
        val trimmed = line.trim()
        val equals = trimmed.indexOf('=')
        if (equals > 0 && trimmed.substring(0, equals).lowercase(Locale.US).startsWith("file")) {
            val url = trimmed.substring(equals + 1).trim()
            if (url.startsWith("http")) {
                return url
            }
        }
    }
    throw IOException("PLS playlist has no stream URL")
}

@Throws(IOException::class)
private fun resolveM3u(sourceUrl: String): String {
    readRemoteText(sourceUrl).forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            return URL(URL(sourceUrl), trimmed).toString()
        }
    }
    throw IOException("M3U playlist has no stream URL")
}

@Throws(IOException::class)
private fun readRemoteText(sourceUrl: String): List<String> {
    val connection = URL(sourceUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "AllRadio/1.0")
    return try {
        BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.lineSequence().toList()
        }
    } finally {
        connection.disconnect()
    }
}

private fun readIcyStreamTitle(metadataText: String): String? {
    val marker = "StreamTitle="
    var start = metadataText.indexOf(marker)
    if (start < 0) {
        return null
    }
    start += marker.length
    while (start < metadataText.length && metadataText[start] in charArrayOf('\'', '"', ' ')) {
        start++
    }
    var end = metadataText.indexOf(';', start)
    if (end < 0) {
        end = metadataText.length
    }
    return metadataText
        .substring(start, end)
        .trim()
        .trimEnd('\'', '"')
}

private fun sampleStations() = listOf(
    Station("AniSon.FM 320", "PLS-like direct MP3 endpoint", "https://pool.anison.fm/AniSonFM(320)"),
    Station("Nightride FM", "Direct MP3", "https://stream.nightride.fm/nightride.mp3"),
    Station("Party Vibe Radio", "PLS playlist with sid", "http://www.partyviberadio.com:8004/listen.pls?sid=1"),
    Station("Magic Streams", "Icecast/Shoutcast stream", "http://cast.magicstreams.gr:9111/stream"),
    Station("SomaFM Groove Salad", "PLS playlist", "https://somafm.com/groovesalad.pls"),
    Station("SomaFM Drone Zone", "M3U playlist", "https://somafm.com/m3u/dronezone130.m3u"),
    Station("CBC Radio One Montreal", "HLS M3U8", "https://cbcradiolive.akamaized.net/hls/live/2041030/ES_R1EMT/master.m3u8"),
    Station("BBC World Service", "HLS M3U8", "https://as-hls-ww-live.akamaized.net/pool_904/live/ww/bbc_world_service/bbc_world_service.isml/bbc_world_service-audio=128000.norewind.m3u8"),
    Station("Bloomberg Radio", "HLS M3U8", "https://bloomberg-live-prod-us-east-1.s3.amazonaws.com/rad/Channel-RAD-AWS-virginia-1/Source-RadBOS-96-1_live.m3u8"),
    Station("FIP", "Direct MP3 Icecast", "https://icecast.radiofrance.fr/fip-midfi.mp3"),
)
