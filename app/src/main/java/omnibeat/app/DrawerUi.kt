package omnibeat.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerContent() {
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
            Text("OmniBeat", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
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
