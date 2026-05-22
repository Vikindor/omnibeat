package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.model.ThemeMode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(
    val title: String,
    val text: AnnotatedString,
    val imageRes: Int? = null,
)

private val OnboardingPages = listOf(
    OnboardingPage(
        title = "Build your library",
        text = onboardingText("Add stations manually or search online"),
        imageRes = R.drawable.onboarding_build_library,
    ),
    OnboardingPage(
        title = "Organize your library",
        text = onboardingText("Sort stations and add to favorites for quick access"),
        imageRes = R.drawable.onboarding_organize_library,
    ),
    OnboardingPage(
        title = "Manage stations",
        text = onboardingText {
            appendBold("Tap")
            append(" a station to start playback\n")
            appendBold("Press and hold")
            append(" to edit title, URL, tags, or delete the station")
        },
        imageRes = R.drawable.onboarding_manage_stations,
    ),
    OnboardingPage(
        title = "Use stream actions",
        text = onboardingText {
            appendBold("Tap")
            append(" stream info to copy the current track name\n")
            appendBold("Press and hold")
            append(" to open detailed stream information")
        },
        imageRes = R.drawable.onboarding_stream_actions,
    ),
)

@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPages.size })
    val scope = rememberCoroutineScope()
    val pageIndex = pagerState.currentPage
    val isLastPage = pageIndex == OnboardingPages.lastIndex

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(RadioBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemeModeSegmentedControl(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
            TextButton(onClick = onFinished) {
                Text("Skip", color = RadioTextMuted)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            OnboardingPageContent(
                page = OnboardingPages[page],
                modifier = Modifier.onboardingPagerFade(pagerState, page),
            )
        }
        OnboardingDots(pageCount = OnboardingPages.size, selectedIndex = pageIndex)
        Spacer(modifier = Modifier.height(22.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (pageIndex > 0) {
                OmniSecondaryButton(
                    text = "Back",
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            OmniPrimaryButton(
                text = if (isLastPage) "Continue" else "Next",
                onClick = {
                    if (isLastPage) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun onboardingText(text: String): AnnotatedString {
    return AnnotatedString(text)
}

private fun onboardingText(builder: AnnotatedString.Builder.() -> Unit): AnnotatedString {
    return buildAnnotatedString(builder)
}

private fun AnnotatedString.Builder.appendBold(text: String) {
    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
    append(text)
    pop()
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        OnboardingScreenshot(imageRes = page.imageRes)
        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = page.title,
            color = RadioText,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Text(
            text = page.text,
            color = RadioTextMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun NotificationPermissionIntro(
    onGrant: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RadioBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onSkip) {
                Text("Skip", color = RadioTextMuted)
            }
        }

        Spacer(modifier = Modifier.weight(0.8f))
        Text(
            text = "Enable playback controls",
            color = RadioText,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 34.sp,
        )
        Text(
            text = "OmniBeat does not send push notifications. This permission is only used to show the media notification with playback controls.",
            color = RadioTextMuted,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "You can change this later in Settings.",
            color = RadioTextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(modifier = Modifier.height(30.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(RadioSurface)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Notifications",
                    color = RadioText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Required for media controls",
                    color = RadioTextMuted,
                    fontSize = 14.sp,
                )
            }
            TextButton(onClick = onGrant) {
                Text("Grant", color = RadioPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Suppress("FrequentlyChangedStateReadInComposition")
private fun Modifier.onboardingPagerFade(
    pagerState: PagerState,
    pageIndex: Int,
): Modifier = graphicsLayer {
    val pageOffset = (
        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
    ).absoluteValue.coerceIn(0f, 1f)
    val minPageAlpha = 0.35f
    val fadeProgress = (pageOffset * 2.2f).coerceIn(0f, 1f)
    alpha = 1f - fadeProgress * (1f - minPageAlpha)
}

@Composable
private fun OnboardingScreenshot(
    imageRes: Int?,
    modifier: Modifier = Modifier,
) {
    val frameShape = RoundedCornerShape(28.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(frameShape)
            .background(RadioSurface)
            .border(width = 1.dp, color = RadioText.copy(alpha = 0.32f), shape = frameShape),
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(RadioSurfaceHigh),
            ) {
            Text(
                text = "Screenshot placeholder",
                color = RadioTextMuted,
                fontSize = 14.sp,
            )
            }
        }
    }
}

@Composable
private fun OnboardingDots(
    pageCount: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == selectedIndex) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (index == selectedIndex) RadioPrimary else RadioOutline),
            )
        }
    }
}
