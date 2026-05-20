package omnibeat.app.ui

import omnibeat.app.R

import omnibeat.app.model.MainPage

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerContent(
    selectedPage: MainPage,
    onStationsClick: () -> Unit = {},
    onExportImportClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
) {
    val aboutFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    val drawerScrollState = rememberScrollState()

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
                .padding(horizontal = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(drawerScrollState)
                    .padding(top = 28.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(52.dp)
                        .clip(CircleShape),
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
                DrawerDivider()
                DrawerItem(
                    text = "Stations",
                    iconRes = R.drawable.ic_list,
                    selected = selectedPage in MainPage.tabPages,
                    onClick = onStationsClick,
                )
                DrawerItem(
                    text = "Export / Import",
                    iconRes = R.drawable.ic_file_download,
                    selected = selectedPage == MainPage.ExportImport,
                    onClick = onExportImportClick,
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
                    modifier = Modifier
                        .focusRequester(aboutFocusRequester)
                        .focusProperties { down = exitFocusRequester },
                )
            }
            DrawerDivider()
            DrawerItem(
                text = "Close app",
                iconRes = R.drawable.ic_exit,
                selected = false,
                onClick = onExitClick,
                modifier = Modifier
                    .focusRequester(exitFocusRequester)
                    .focusProperties { up = aboutFocusRequester },
            )
        }
    }
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        color = RadioOutline.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun DrawerItem(
    text: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
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
                fontWeight = FontWeight.Medium,
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = RadioSurfaceHigh,
            selectedIconColor = RadioText,
            selectedTextColor = RadioText,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = RadioTextMuted,
            unselectedTextColor = RadioText,
        ),
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp)),
    )
}
