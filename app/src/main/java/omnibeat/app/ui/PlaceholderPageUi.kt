package omnibeat.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyFuturePage(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(title, color = RadioText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Coming later",
            color = RadioTextMuted,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
