package omnibeat.app.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue

@Suppress("FrequentlyChangedStateReadInComposition")
fun Modifier.pagerFade(
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
fun <T> SyncPagerWithSelectedPage(
    pagerState: PagerState,
    pages: List<T>,
    selectedPage: T,
    onPageSettled: (T) -> Unit,
) {
    LaunchedEffect(selectedPage, pages) {
        val pageIndex = pages.indexOf(selectedPage)
        if (pageIndex >= 0 && !pagerState.isScrollInProgress && pagerState.currentPage != pageIndex) {
            pagerState.animateScrollToPage(pageIndex)
        }
    }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }.collect { pageIndex ->
            pages.getOrNull(pageIndex)?.let(onPageSettled)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && pagerState.currentPageOffsetFraction.absoluteValue > 0.001f) {
                    pagerState.animateScrollToPage(pagerState.currentPage)
                }
            }
    }
}
