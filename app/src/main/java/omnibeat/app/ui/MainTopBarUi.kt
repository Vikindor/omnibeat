package omnibeat.app.ui

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import omnibeat.app.ui.appStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import omnibeat.app.R
import omnibeat.app.model.MainPage
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState

@Composable
fun MainTopBar(
    selectedPage: MainPage,
    visualSelectedPage: MainPage = selectedPage,
    tabPages: List<MainPage>,
    sortState: StationSortState,
    reordering: Boolean,
    onPageSelected: (MainPage) -> Unit,
    onSortModeSelected: (StationSortMode) -> Unit,
    onCancelReorder: () -> Unit,
    onConfirmReorder: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateBack: () -> Unit,
    onAddStation: () -> Unit,
    onSearchOnline: () -> Unit,
    onlineSearchControl: (@Composable (Modifier) -> Unit)? = null,
) {
    val density = LocalDensity.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(RadioSizes.topBarHeight)
            .padding(start = RadioSpacing.extraSmall, end = RadioSpacing.small),
    ) {
        if (selectedPage in tabPages) {
            OmniIconButton(
                painter = painterResource(R.drawable.ic_menu),
                onClick = onOpenDrawer,
            )
        } else {
            OmniIconButton(
                painter = painterResource(R.drawable.ic_arrow_back),
                onClick = onNavigateBack,
            )
        }
        if (selectedPage == MainPage.SearchOnline && onlineSearchControl != null) {
            onlineSearchControl(
                Modifier
                    .weight(1f)
                    .padding(start = RadioSpacing.small, end = RadioSpacing.medium),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (selectedPage in tabPages) {
                    tabPages.forEach { tab ->
                        var tabTextWidth by remember(tab) { mutableStateOf(0.dp) }
                        Column(
                            modifier = Modifier
                                .selectable(
                                    selected = visualSelectedPage == tab,
                                    enabled = !reordering,
                                    role = Role.Tab,
                                    onClick = { onPageSelected(tab) },
                                )
                                .padding(horizontal = RadioSpacing.medium, vertical = RadioSpacing.small),
                        ) {
                            Text(
                                text = stringResource(tab.titleRes()),
                                color = if (visualSelectedPage == tab) RadioText else RadioTextMuted,
                                fontSize = RadioTextSizes.subtitle,
                                fontWeight = if (visualSelectedPage == tab) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.onSizeChanged { size ->
                                    tabTextWidth = with(density) { size.width.toDp() }
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = RadioSpacing.compact)
                                    .width(tabTextWidth)
                                    .height(2.dp)
                                    .background(if (visualSelectedPage == tab) RadioPrimary else RadioOutline),
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(selectedPage.titleRes()),
                        color = RadioText,
                        fontSize = RadioTextSizes.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = RadioSpacing.medium),
                    )
                }
            }
        }
        if (selectedPage in tabPages) {
            if (reordering) {
                OmniIconButton(
                    painter = painterResource(R.drawable.ic_close),
                    onClick = onCancelReorder,
                    tint = Color(0xFFFF5C6C),
                )
                OmniIconButton(
                    painter = painterResource(R.drawable.ic_check),
                    onClick = onConfirmReorder,
                    tint = Color(0xFF66D17A),
                )
            } else {
                SortMenuButton(
                    sortState = sortState,
                    expanded = sortMenuExpanded,
                    onExpandedChange = { sortMenuExpanded = it },
                    onSortModeSelected = onSortModeSelected,
                )
                AddMenuButton(
                    expanded = addMenuExpanded,
                    onExpandedChange = { addMenuExpanded = it },
                    onAddStation = onAddStation,
                    onSearchOnline = onSearchOnline,
                )
            }
        }
    }
}

@Composable
private fun SortMenuButton(
    sortState: StationSortState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSortModeSelected: (StationSortMode) -> Unit,
) {
    Box {
        OmniIconButton(
            painter = painterResource(R.drawable.ic_sort),
            onClick = { onExpandedChange(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = RadioSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(RadioCorners.medium),
            offset = DpOffset(x = 0.dp, y = RadioSpacing.extraSmall),
        ) {
            StationSortMode.entries.forEach { option ->
                val selected = sortState.mode == option
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = RadioSizes.menuItemMinHeight),
                    contentPadding = PaddingValues(horizontal = RadioSpacing.large),
                    text = {
                        Text(
                            text = stringResource(option.labelRes()),
                            color = RadioText,
                        )
                    },
                    onClick = {
                        onSortModeSelected(option)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(
                                if (selected) {
                                    R.drawable.ic_radio_button_checked
                                } else {
                                    R.drawable.ic_radio_button_unchecked
                                },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(RadioSizes.icon),
                            tint = if (selected) RadioPrimary else RadioTextMuted,
                        )
                    },
                    trailingIcon = if (selected && option != StationSortMode.Custom) {
                        {
                            Icon(
                                painter = painterResource(
                                    if (sortState.ascending) {
                                        R.drawable.ic_keyboard_arrow_up
                                    } else {
                                        R.drawable.ic_keyboard_arrow_down
                                    },
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(RadioSizes.icon),
                                tint = RadioText,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun AddMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddStation: () -> Unit,
    onSearchOnline: () -> Unit,
) {
    Box {
        OmniIconButton(
            painter = painterResource(R.drawable.ic_add),
            onClick = { onExpandedChange(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = RadioSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(RadioCorners.medium),
            offset = DpOffset(x = 0.dp, y = RadioSpacing.extraSmall),
        ) {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = RadioSizes.menuItemMinHeight),
                contentPadding = PaddingValues(horizontal = RadioSpacing.large),
                text = {
                    Text(
                        text = stringResource(R.string.action_add_manually),
                        color = RadioText,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onAddStation()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_manually),
                        contentDescription = null,
                        modifier = Modifier.size(RadioSizes.icon),
                        tint = RadioText,
                    )
                },
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = RadioSizes.menuItemMinHeight),
                contentPadding = PaddingValues(horizontal = RadioSpacing.large),
                text = {
                    Text(
                        text = stringResource(R.string.action_search_online),
                        color = RadioText,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onSearchOnline()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(RadioSizes.icon),
                        tint = RadioText,
                    )
                },
            )
        }
    }
}
