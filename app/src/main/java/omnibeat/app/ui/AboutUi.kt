package omnibeat.app.ui

import omnibeat.app.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import omnibeat.app.ui.appStringResource as stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AboutPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
                .padding(horizontal = RadioSpacing.page)
                .padding(top = RadioSpacing.rowVertical, bottom = RadioSpacing.page),
        ) {
            AboutHeader()
            AboutDivider()

            AboutSectionHeader(stringResource(R.string.about_section_app))
            AboutInfoRow(label = stringResource(R.string.about_version), value = versionName)
            AboutInfoRow(label = stringResource(R.string.about_build), value = versionCode.toString())
            AboutInfoRow(
                label = stringResource(R.string.about_formats),
                value = "Direct stream, PLS, M3U, HLS, XSPF, ASX/WAX/WMX, DASH",
                stacked = true,
            )
            AboutDivider()

            AboutSectionHeader(stringResource(R.string.about_section_project))
            AboutLinkRow(
                label = stringResource(R.string.about_source_code),
                value = "github.com/vikindor/omnibeat",
                url = "https://github.com/Vikindor/omnibeat",
            )
            AboutLinkRow(
                label = stringResource(R.string.about_license),
                value = "GNU AGPL 3.0",
                url = "https://github.com/Vikindor/omnibeat/blob/master/LICENSE",
            )
            AboutLinkRow(
                label = stringResource(R.string.about_radio_browser_value),
                value = "Radio Browser",
                url = "https://www.radio-browser.info",
            )
            AboutDivider()

            AboutSectionHeader(stringResource(R.string.about_section_author))
            AboutLinkRow(
                label = "Vikindor",
                value = "vikindor.github.io",
                url = "https://vikindor.github.io",
            )

            Text(
                text = stringResource(R.string.about_support),
                color = RadioTextMuted,
                fontSize = RadioTextSizes.bodySmall,
                lineHeight = RadioTextSizes.bodyLineHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = RadioSpacing.compact, bottom = RadioSpacing.small),
            )

            AboutLinkRow(
                label = stringResource(R.string.about_contact_support),
                value = "vikindor.github.io/support/",
                url = "https://vikindor.github.io/support/",
            )

            Text(
                text = "© 2026",
                color = RadioTextMuted,
                fontSize = RadioTextSizes.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RadioSpacing.groupGap),
            )
        }
        OmniScrollIndicator(
            scrollIndicatorState = scrollState.scrollIndicatorState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = RadioSpacing.extraSmall),
        )
    }
}

@Composable
private fun AboutHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RadioSpacing.large),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = RadioSpacing.groupGap),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(RadioSizes.aboutLogo),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(RadioSpacing.labelGap),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = RadioText,
                fontSize = RadioTextSizes.appName,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.about_tagline),
                color = RadioText,
                fontSize = RadioTextSizes.bodyLarge,
            )
            Text(
                text = stringResource(R.string.about_description),
                color = RadioTextMuted,
                fontSize = RadioTextSizes.bodySmall,
                lineHeight = RadioTextSizes.bodyLineHeight,
            )
        }
    }
}

@Composable
private fun AboutSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = RadioTextMuted,
        fontSize = RadioTextSizes.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = RadioSpacing.fieldGap, bottom = RadioSpacing.small),
    )
}

@Composable
private fun AboutDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = RadioOutline.copy(alpha = 0.65f),
        modifier = modifier.padding(top = RadioSpacing.rowVertical, bottom = RadioSpacing.small),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutLinkRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    url: String? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val linkCopiedText = stringResource(R.string.toast_link_copied)
    val enabled = url != null

    Row(
        horizontalArrangement = Arrangement.spacedBy(RadioSpacing.groupGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = { url?.let(uriHandler::openUri) },
                onLongClick = {
                    url?.let {
                        copyLinkToClipboard(context, label = label, url = it)
                        Toast.makeText(context, linkCopiedText, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(vertical = RadioSpacing.fieldGap),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                color = if (enabled) RadioText else RadioTextMuted,
                fontSize = RadioTextSizes.bodyLarge,
            )
            Text(
                text = value,
                color = RadioTextMuted,
                fontSize = RadioTextSizes.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_open_in_new),
                contentDescription = null,
                tint = RadioTextMuted,
                modifier = Modifier.size(RadioSizes.iconCompact),
            )
        }
    }
}

private fun copyLinkToClipboard(context: Context, label: String, url: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, url))
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
            verticalArrangement = Arrangement.spacedBy(RadioSpacing.extraSmall),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = RadioSpacing.fieldGap),
        ) {
            Text(
                text = label,
                color = RadioText,
                fontSize = RadioTextSizes.bodyLarge,
            )
            Text(
                text = value,
                color = RadioTextMuted,
                fontSize = RadioTextSizes.bodySmall,
                lineHeight = RadioTextSizes.bodyLineHeight,
            )
        }
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(RadioSpacing.groupGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = RadioSpacing.fieldGap),
    ) {
        Text(
            text = label,
            color = RadioText,
            fontSize = RadioTextSizes.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = RadioTextMuted,
            fontSize = RadioTextSizes.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
