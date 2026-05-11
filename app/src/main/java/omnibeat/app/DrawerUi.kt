package omnibeat.app

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
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
    onStationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
) {
    val aboutFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }

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
                tint = Color.Unspecified,
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
            DrawerDivider()
            DrawerItem(
                text = "Stations",
                iconRes = R.drawable.ic_list,
                onClick = onStationsClick,
            )
            DrawerItem(
                text = "Settings",
                iconRes = R.drawable.ic_settings,
                onClick = onSettingsClick,
            )
            DrawerItem(
                text = "About",
                iconRes = R.drawable.ic_info,
                onClick = onAboutClick,
                modifier = Modifier
                    .focusRequester(aboutFocusRequester)
                    .focusProperties { down = exitFocusRequester },
            )
            Spacer(modifier = Modifier.weight(1f))
            DrawerDivider()
            DrawerItem(
                text = "Close app",
                iconRes = R.drawable.ic_exit,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

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
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = RadioSurfaceHigh,
            selectedIconColor = RadioText,
            selectedTextColor = RadioText,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = RadioTextMuted,
            unselectedTextColor = RadioText,
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (focused) RadioSurfaceHigh else Color.Transparent),
    )
}
