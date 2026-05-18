package omnibeat.app.ui

import omnibeat.app.R

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        packageInfo.versionName.orEmpty()
    }

    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .padding(top = 32.dp, bottom = 24.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(88.dp),
        )
        Text(
            text = "OmniBeat",
            color = RadioText,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "A streaming audio player built to handle the messy reality of internet audio links, from direct streams to common playlist formats.",
            color = RadioTextMuted,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
        )

        AboutInfoRow(label = "Version", value = versionName.ifBlank { "Unknown" })

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )

        AboutLinkRow(label = "Privacy Policy", value = "Coming later")
        AboutLinkRow(label = "License", value = "Coming later")
        AboutLinkRow(label = "Source Code", value = "Coming later")

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Made by Vikindor",
            color = RadioPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { uriHandler.openUri("https://vikindor.github.io") }
                .padding(top = 24.dp),
        )
        Text(
            text = "2026",
            color = RadioTextMuted,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun AboutLinkRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 13.dp),
    ) {
        Text(
            text = label,
            color = if (onClick == null) RadioText else RadioPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = RadioTextMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    valueColor: Color = RadioText,
    onClick: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
    ) {
        Text(label, color = RadioTextMuted, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
