package omnibeat.app.ui

import omnibeat.app.R

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val packageInfo = remember(context) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    }
    val versionName = packageInfo.versionName.orEmpty()
    val versionCode = packageInfo.longVersionCode

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 22.dp),
        ) {
            AboutHeader()
            AboutDivider()

            AboutSectionHeader("App")
            AboutInfoRow(label = "Version", value = versionName.ifBlank { "Unknown" })
            AboutInfoRow(label = "Build", value = versionCode.toString())
            AboutInfoRow(
                label = "Formats",
                value = "Direct streams, PLS, M3U, HLS, XSPF, ASX/WAX/WMX, DASH",
                stacked = true,
            )
            AboutDivider()

            AboutSectionHeader("Project")
            AboutLinkRow(
                label = "Source Code",
                value = "github.com/Vikindor/omnibeat",
                onClick = { uriHandler.openUri("https://github.com/Vikindor/omnibeat") },
            )
            AboutLinkRow(
                label = "License",
                value = "GNU GPL 3.0",
                onClick = { uriHandler.openUri("https://github.com/Vikindor/omnibeat/blob/master/LICENSE") },
            )
            AboutLinkRow(
                label = "Radio Browser",
                value = "radio-browser.info",
                onClick = { uriHandler.openUri("https://www.radio-browser.info") },
            )
            AboutDivider()

            AboutSectionHeader("Author")
            AboutLinkRow(
                label = "Vikindor",
                value = "vikindor.github.io",
                onClick = { uriHandler.openUri("https://vikindor.github.io") },
            )

            Text(
                text = "© 2026",
                color = RadioTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            )
        }
        OmniScrollIndicator(
            scrollIndicatorState = scrollState.scrollIndicatorState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        )
    }
}

@Composable
private fun AboutHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 4.dp, bottom = 18.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(72.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "OmniBeat",
                color = RadioText,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Streaming audio, without the fuss",
                color = RadioText,
                fontSize = 16.sp,
            )
            Text(
                text = "Built to handle internet audio links, from direct streams to common playlist formats",
                color = RadioTextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun AboutSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = RadioTextMuted,
        fontSize = 14.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun AboutDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = RadioOutline.copy(alpha = 0.65f),
        modifier = modifier.padding(top = 14.dp),
    )
}

@Composable
private fun AboutLinkRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val enabled = onClick != null

    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick?.invoke() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                color = if (enabled) RadioText else RadioTextMuted,
                fontSize = 16.sp,
            )
            Text(
                text = value,
                color = RadioTextMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_open_in_new),
                contentDescription = "Open $label in browser",
                tint = RadioTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    if (stacked) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                color = RadioText,
                fontSize = 16.sp,
            )
            Text(
                text = value,
                color = RadioTextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = RadioText,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = RadioTextMuted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
