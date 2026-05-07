package omnibeat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerContent(
    selectedPage: MainPage,
    onStationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
) {
    ModalDrawerSheet(
        drawerContainerColor = RadioSurface,
        drawerContentColor = RadioText,
        modifier = Modifier
            .width(304.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(top = 28.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(RadioBackground),
            )
            Text(
                text = "OmniBeat",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp),
            )
            Text(
                text = "Streaming audio player",
                color = RadioTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 12.dp),
            )
            HorizontalDivider(
                color = RadioOutline.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            )
            DrawerItem(
                text = "Stations",
                iconRes = R.drawable.ic_list,
                selected = selectedPage == MainPage.Stations || selectedPage == MainPage.Favorites,
                onClick = onStationsClick,
            )
            DrawerItem(
                text = "Settings",
                iconRes = R.drawable.ic_settings,
                selected = selectedPage == MainPage.Settings,
                onClick = onSettingsClick,
            )
            DrawerItem(
                text = "About",
                iconRes = R.drawable.ic_info,
                selected = selectedPage == MainPage.About,
                onClick = onAboutClick,
            )
        }
    }
}

@Composable
private fun DrawerItem(
    text: String,
    iconRes: Int,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
        label = {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = RadioSurfaceHigh,
            selectedIconColor = RadioText,
            selectedTextColor = RadioText,
            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unselectedIconColor = RadioTextMuted,
            unselectedTextColor = RadioText,
        ),
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
