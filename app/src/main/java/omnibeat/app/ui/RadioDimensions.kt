package omnibeat.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RadioSpacing {
    val extraSmall = 4.5.dp
    val labelGap = 5.5.dp
    val compact = 7.dp
    val small = 9.dp
    val fieldGap = 11.dp
    val medium = 13.dp
    val rowVertical = 15.dp
    val large = 18.dp
    val groupGap = 20.dp
    val page = 22.dp
    val section = 24.dp
    val wide = 26.dp
    val spacious = 31.dp
    val inset = 35.dp
}

object RadioSizes {
    val iconTiny = 15.dp
    val iconInline = 20.dp
    val iconCompact = 22.dp
    val loadingIcon = 24.dp
    val icon = 26.dp
    val iconLarge = 31.dp
    val iconPlayback = 40.dp
    val playerCollapseButton = 48.dp
    val playerCollapseIcon = 28.dp
    val playerDividerThickness = 2.dp
    val button = 53.dp
    val primaryPlaybackButton = 76.dp
    val textFieldMinHeight = 62.dp
    val actionButtonMinHeight = 44.dp
    val menuItemMinHeight = 53.dp
    val drawerItemMinHeight = 62.dp
    val topBarHeight = 57.dp
    val playerHeaderHeight = 38.dp
    val artwork = 57.dp
    val drawerLogo = 57.dp
    val aboutLogo = 79.dp
    val drawerWidth = 300.dp
    val themeSegmentWidth = 57.dp
    val themeSegmentHeight = 35.dp
    val volumeSliderHeight = 352.dp
    val volumeSliderWidth = 79.dp
    val volumeTrackWidth = 20.dp
    val volumeThumbWidth = 44.dp
    val volumeThumbHeight = 9.dp
    val signalWidth = 18.dp
    val signalHeight = 20.dp
    val screenshotHeight = 330.dp
    val screenshotPlaceholderHeight = 242.dp
    val onboardingTopGap = 29.dp
    val onboardingTitleGap = 37.dp
    val selectedPageDot = 11.dp
    val pageDot = 9.dp
}

object RadioCorners {
    val small = 7.dp
    val medium = 15.dp
    val large = 20.dp
    val extraLarge = 24.dp
    val pill = 31.dp
}

object RadioTextSizes {
    val badge = 12.sp
    val caption = 14.sp
    val bodySmall = 15.sp
    val body = 17.sp
    val bodyLarge = 18.sp
    val stationTitle = 19.sp
    val subtitle = 20.sp
    val title = 22.sp
    val headline = 24.sp
    val appName = 29.sp
    val compactLineHeight = 20.sp
    val bodyLineHeight = 22.sp
    val onboardingTitle = 31.sp
    val onboardingLineHeight = 37.sp
    val onboardingBodyLineHeight = 25.sp
}

private val BaseTypography = Typography()

val RadioTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontSize = 63.sp, lineHeight = 70.sp),
    displayMedium = BaseTypography.displayMedium.copy(fontSize = 50.sp, lineHeight = 57.sp),
    displaySmall = BaseTypography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp),
    headlineLarge = BaseTypography.headlineLarge.copy(fontSize = 35.sp, lineHeight = 44.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontSize = 31.sp, lineHeight = 40.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 35.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontSize = RadioTextSizes.headline, lineHeight = 31.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontSize = RadioTextSizes.bodyLarge, lineHeight = 26.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontSize = RadioTextSizes.bodySmall, lineHeight = 22.sp),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = RadioTextSizes.bodyLarge, lineHeight = 26.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = RadioTextSizes.bodySmall, lineHeight = 22.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontSize = RadioTextSizes.bodySmall, lineHeight = 22.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontSize = RadioTextSizes.badge, lineHeight = 18.sp),
)
