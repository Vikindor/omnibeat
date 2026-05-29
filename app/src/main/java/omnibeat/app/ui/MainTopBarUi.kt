package omnibeat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnibeat.app.R
import omnibeat.app.model.MainPage
import omnibeat.app.model.StationSortMode
import omnibeat.app.model.StationSortState

@Composable
fun MainTopBar(
    selectedPage: MainPage,
    tabPages: List<MainPage>,
    sortState: StationSortState,
    reordering: Boolean,
    onPageSelected: (MainPage) -> Unit,
    onSortModeSelected: (StationSortMode) -> Unit,
    onCancelReorder: () -> Unit,
    onConfirmReorder: () -> Unit,
    onOpenDrawer: () -> Unit,
    onAddStation: () -> Unit,
    onFindOnline: () -> Unit,
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
            .height(52.dp)
            .padding(start = 4.dp, end = 8.dp),
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "Open drawer",
                tint = RadioText,
            )
        }
        if (selectedPage == MainPage.FindOnline && onlineSearchControl != null) {
            onlineSearchControl(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
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
                                    selected = selectedPage == tab,
                                    enabled = !reordering,
                                    role = Role.Tab,
                                    onClick = { onPageSelected(tab) },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = tab.title,
                                color = if (selectedPage == tab) RadioText else RadioTextMuted,
                                fontSize = 18.sp,
                                fontWeight = if (selectedPage == tab) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.onSizeChanged { size ->
                                    tabTextWidth = with(density) { size.width.toDp() }
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .width(tabTextWidth)
                                    .height(2.dp)
                                    .background(if (selectedPage == tab) RadioPrimary else RadioOutline),
                            )
                        }
                    }
                } else {
                    Text(
                        text = selectedPage.title,
                        color = RadioText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        if (selectedPage in tabPages) {
            if (reordering) {
                OmniTopBarIconButton(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Cancel reorder",
                    onClick = onCancelReorder,
                    tint = Color(0xFFFF5C6C),
                )
                OmniTopBarIconButton(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Save reorder",
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
                    onFindOnline = onFindOnline,
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
        OmniTopBarIconButton(
            painter = painterResource(R.drawable.ic_filter_list),
            contentDescription = "Sort stations",
            onClick = { onExpandedChange(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = RadioSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            offset = DpOffset(x = 0.dp, y = 4.dp),
        ) {
            StationSortMode.entries.forEach { option ->
                val selected = sortState.mode == option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = RadioText,
                        )
                    },
                    onClick = {
                        onSortModeSelected(option)
                        onExpandedChange(false)
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
                                contentDescription = if (sortState.ascending) {
                                    "Ascending"
                                } else {
                                    "Descending"
                                },
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
    onFindOnline: () -> Unit,
) {
    Box {
        OmniTopBarIconButton(
            painter = painterResource(R.drawable.ic_add_circle_outline),
            contentDescription = "Add station",
            onClick = { onExpandedChange(true) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = RadioSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            offset = DpOffset(x = 0.dp, y = 4.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Add manually",
                        color = RadioText,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onAddStation()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_circle_outline),
                        contentDescription = null,
                        tint = RadioText,
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Find online",
                        color = RadioText,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onFindOnline()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = RadioText,
                    )
                },
            )
        }
    }
}
