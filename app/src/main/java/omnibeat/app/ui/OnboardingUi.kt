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
import androidx.compose.ui.res.stringResource
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
    val titleRes: Int,
    val textType: OnboardingTextType,
    val imageRes: Int? = null,
)

private enum class OnboardingTextType {
    Build,
    Organize,
    Manage,
    StreamActions,
}

private val OnboardingPages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_build_title,
        textType = OnboardingTextType.Build,
        imageRes = R.drawable.onboarding_build_library,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_organize_title,
        textType = OnboardingTextType.Organize,
        imageRes = R.drawable.onboarding_organize_library,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_manage_title,
        textType = OnboardingTextType.Manage,
        imageRes = R.drawable.onboarding_manage_stations,
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_stream_actions_title,
        textType = OnboardingTextType.StreamActions,
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
            .padding(horizontal = 20.dp, vertical = 22.dp),
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
                Text(stringResource(R.string.action_skip), color = RadioTextMuted)
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
                    text = stringResource(R.string.action_back),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            OmniPrimaryButton(
                text = if (isLastPage) {
                    stringResource(R.string.action_continue)
                } else {
                    stringResource(R.string.action_next)
                },
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

private fun onboardingText(builder: AnnotatedString.Builder.() -> Unit): AnnotatedString {
    return buildAnnotatedString(builder)
}

private fun AnnotatedString.Builder.appendBold(text: String) {
    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
    append(text)
    pop()
}

private fun AnnotatedString.Builder.appendSpace() {
    append(" ")
}

private fun AnnotatedString.Builder.appendLineBreak() {
    append("\n")
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    val text = onboardingText(page.textType)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        OnboardingScreenshot(imageRes = page.imageRes)
        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = stringResource(page.titleRes),
            color = RadioText,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
        )
        Text(
            text = text,
            color = RadioTextMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Start,
            lineHeight = 23.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun onboardingText(textType: OnboardingTextType): AnnotatedString {
    return when (textType) {
        OnboardingTextType.Build -> AnnotatedString(stringResource(R.string.onboarding_build_text))
        OnboardingTextType.Organize -> AnnotatedString(stringResource(R.string.onboarding_organize_text))
        OnboardingTextType.Manage -> {
            val tap = stringResource(R.string.onboarding_tap)
            val tapSuffix = stringResource(R.string.onboarding_manage_tap_suffix)
            val pressAndHold = stringResource(R.string.onboarding_press_and_hold)
            val holdSuffix = stringResource(R.string.onboarding_manage_hold_suffix)
            onboardingText {
                appendBold(tap)
                appendSpace()
                append(tapSuffix)
                appendLineBreak()
                appendLineBreak()
                appendBold(pressAndHold)
                append(holdSuffix)
            }
        }
        OnboardingTextType.StreamActions -> {
            val tap = stringResource(R.string.onboarding_tap)
            val tapSuffix = stringResource(R.string.onboarding_stream_tap_suffix)
            val pressAndHold = stringResource(R.string.onboarding_press_and_hold)
            val holdSuffix = stringResource(R.string.onboarding_stream_hold_suffix)
            onboardingText {
                appendBold(tap)
                appendSpace()
                append(tapSuffix)
                appendLineBreak()
                appendLineBreak()
                appendBold(pressAndHold)
                append(holdSuffix)
            }
        }
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
                text = stringResource(R.string.onboarding_screenshot_placeholder),
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
