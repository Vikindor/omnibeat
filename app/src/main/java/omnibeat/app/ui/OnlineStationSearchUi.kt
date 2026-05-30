package omnibeat.app.ui

import omnibeat.app.R
import omnibeat.app.radio.stationTags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnibeat.app.radio.RadioBrowserFilterOption
import omnibeat.app.radio.RadioBrowserSearchParams
import omnibeat.app.radio.RadioBrowserSort
import omnibeat.app.radio.RadioBrowserStation
import kotlinx.coroutines.flow.distinctUntilChanged

data class OnlineStationSearchState(
    val nameQuery: String = "",
    val tagsQuery: String = "",
    val selectedCountry: RadioBrowserFilterOption? = null,
    val selectedLanguage: RadioBrowserFilterOption? = null,
    val selectedSort: RadioBrowserSort = RadioBrowserSort.Clicks,
    val sortDirection: SearchSortDirection = SearchSortDirection.Descending,
    val bitrateMin: String = "",
    val bitrateMax: String = "",
    val includeBroken: Boolean = false,
)

enum class SearchSortDirection(val reverse: Boolean) {
    Descending(true),
    Ascending(false),
}

private fun SearchSortDirection.labelRes(): Int {
    return when (this) {
        SearchSortDirection.Descending -> R.string.online_search_sort_descending_order
        SearchSortDirection.Ascending -> R.string.online_search_sort_ascending_order
    }
}

private fun RadioBrowserSort.labelRes(): Int {
    return when (this) {
        RadioBrowserSort.Clicks -> R.string.radio_browser_sort_clicks
        RadioBrowserSort.Votes -> R.string.radio_browser_sort_votes
        RadioBrowserSort.Name -> R.string.radio_browser_sort_name
        RadioBrowserSort.Bitrate -> R.string.radio_browser_sort_bitrate
        RadioBrowserSort.Country -> R.string.radio_browser_sort_country
        RadioBrowserSort.Random -> R.string.radio_browser_sort_random
    }
}

@Composable
fun OnlineStationSearchPage(
    searchState: OnlineStationSearchState,
    countries: List<RadioBrowserFilterOption>,
    languages: List<RadioBrowserFilterOption>,
    results: List<RadioBrowserStation>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMoreResults: Boolean,
    optionsExpanded: Boolean,
    showArtwork: Boolean,
    addedStreamUrls: Set<String>,
    selectedStreamUrl: String?,
    onSearchStateChange: (OnlineStationSearchState) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onPreviewStation: (RadioBrowserStation) -> Unit,
    onAddStation: (RadioBrowserStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val overlayMaxHeight = (maxHeight - 16.dp).coerceAtLeast(240.dp)
        val listState = rememberLazyListState()

        LaunchedEffect(listState, results.size, loading, loadingMore, hasMoreResults) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .distinctUntilChanged()
                .collect { lastVisibleIndex ->
                    if (
                        lastVisibleIndex != null &&
                        hasMoreResults &&
                        !loading &&
                        !loadingMore &&
                        results.isNotEmpty() &&
                        lastVisibleIndex >= results.lastIndex - 6
                    ) {
                        onLoadMore()
                    }
                }
            }

        if (!loading && !loadingMore && results.isEmpty()) {
            EmptyOnlineSearchState(
                hasQuery = searchState.hasSearchInput(),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = results,
                        key = { index, station -> station.stationUuid.ifBlank { "${station.streamUrl}#$index" } },
                    ) { _, station ->
                        OnlineStationResultItem(
                            station = station,
                            added = station.streamUrl in addedStreamUrls,
                            selected = station.streamUrl == selectedStreamUrl,
                            showArtwork = showArtwork,
                            onPreviewStation = { onPreviewStation(station) },
                            onAddStation = { onAddStation(station) },
                        )
                    }
                    if (loadingMore) {
                        item(key = "online-search-loading-more") {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = RadioPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
                OmniLazyListScrollIndicator(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = optionsExpanded,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SearchOptionsOverlay(
                searchState = searchState,
                countries = countries,
                languages = languages,
                loading = loading,
                onSearchStateChange = onSearchStateChange,
                onSearch = onSearch,
                maxHeight = overlayMaxHeight,
            )
        }
    }
}

@Composable
fun SearchOptionsTopBarControl(
    expanded: Boolean,
    loading: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            color = RadioPrimary,
            contentColor = RadioText,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.online_search_options),
                    color = RadioText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(
                        color = RadioText,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                }
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
                    ),
                    contentDescription = if (expanded) {
                        stringResource(R.string.online_search_hide_options)
                    } else {
                        stringResource(R.string.online_search_show_options)
                    },
                    tint = RadioText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchOptionsOverlay(
    searchState: OnlineStationSearchState,
    countries: List<RadioBrowserFilterOption>,
    languages: List<RadioBrowserFilterOption>,
    loading: Boolean,
    onSearchStateChange: (OnlineStationSearchState) -> Unit,
    onSearch: () -> Unit,
    maxHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = RadioBackground,
        contentColor = RadioText,
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = maxHeight),
    ) {
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize()) {
            SearchOptionsContent(
                searchState = searchState,
                countries = countries,
            languages = languages,
            loading = loading,
            onSearchStateChange = onSearchStateChange,
                onSearch = onSearch,
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            SearchOptionsScrollIndicator(
                scrollIndicatorState = scrollState.scrollIndicatorState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchOptionsScrollIndicator(
    scrollIndicatorState: androidx.compose.foundation.ScrollIndicatorState?,
    modifier: Modifier = Modifier,
) {
    OmniScrollIndicator(
        scrollIndicatorState = scrollIndicatorState,
        modifier = modifier,
    )
}

@Composable
private fun SearchOptionsContent(
    searchState: OnlineStationSearchState,
    countries: List<RadioBrowserFilterOption>,
    languages: List<RadioBrowserFilterOption>,
    loading: Boolean,
    onSearchStateChange: (OnlineStationSearchState) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SearchTextField(
            value = searchState.nameQuery,
            onValueChange = { onSearchStateChange(searchState.copy(nameQuery = it)) },
            label = stringResource(R.string.online_search_by_title),
            placeholder = stringResource(R.string.online_search_station_title_placeholder),
            imeAction = ImeAction.Search,
            keyboardType = KeyboardType.Text,
            onSearch = onSearch,
        )
        SearchTextField(
            value = searchState.tagsQuery,
            onValueChange = { onSearchStateChange(searchState.copy(tagsQuery = it)) },
            label = stringResource(R.string.online_search_by_tags),
            placeholder = stringResource(R.string.common_comma_separated),
            imeAction = ImeAction.Search,
            keyboardType = KeyboardType.Text,
            onSearch = onSearch,
            modifier = Modifier.padding(top = 10.dp),
        )

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )
        Text(
            text = stringResource(R.string.online_search_filters),
            color = RadioText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SearchDropdown(
            label = stringResource(R.string.online_search_countries),
            selectedText = searchState.selectedCountry?.name ?: stringResource(R.string.online_search_all_countries),
            options = listOf<RadioBrowserFilterOption?>(null) + countries,
            optionText = { it?.name ?: stringResource(R.string.online_search_all_countries) },
            onOptionSelected = { onSearchStateChange(searchState.copy(selectedCountry = it)) },
            modifier = Modifier.padding(top = 10.dp),
        )
        SearchDropdown(
            label = stringResource(R.string.online_search_languages),
            selectedText = searchState.selectedLanguage?.name ?: stringResource(R.string.online_search_all_languages),
            options = listOf<RadioBrowserFilterOption?>(null) + languages,
            optionText = { it?.name ?: stringResource(R.string.online_search_all_languages) },
            onOptionSelected = { onSearchStateChange(searchState.copy(selectedLanguage = it)) },
            modifier = Modifier.padding(top = 10.dp),
        )

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchDropdown(
                label = stringResource(R.string.online_search_sort_by),
                selectedText = stringResource(searchState.selectedSort.labelRes()),
                options = RadioBrowserSort.entries,
                optionText = { stringResource(it.labelRes()) },
                onOptionSelected = { onSearchStateChange(searchState.copy(selectedSort = it)) },
                modifier = Modifier.weight(1f),
            )
            SearchDropdown(
                label = stringResource(R.string.online_search_sort_in),
                selectedText = stringResource(searchState.sortDirection.labelRes()),
                options = SearchSortDirection.entries,
                optionText = { stringResource(it.labelRes()) },
                onOptionSelected = { onSearchStateChange(searchState.copy(sortDirection = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        SearchTextField(
            value = searchState.bitrateMin,
            onValueChange = { onSearchStateChange(searchState.copy(bitrateMin = it.digitsOnly())) },
            label = stringResource(R.string.online_search_bitrate_min),
            placeholder = "0",
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Number,
            onSearch = onSearch,
            modifier = Modifier.padding(top = 10.dp),
        )
        SearchTextField(
            value = searchState.bitrateMax,
            onValueChange = { onSearchStateChange(searchState.copy(bitrateMax = it.digitsOnly())) },
            label = stringResource(R.string.online_search_bitrate_max),
            placeholder = stringResource(R.string.online_search_max_placeholder),
            imeAction = ImeAction.Search,
            keyboardType = KeyboardType.Number,
            onSearch = onSearch,
            modifier = Modifier.padding(top = 10.dp),
        )
        SearchCheckbox(
            checked = searchState.includeBroken,
            onCheckedChange = { onSearchStateChange(searchState.copy(includeBroken = it)) },
            modifier = Modifier.padding(top = 8.dp),
        )
        OmniPrimaryButton(
            text = stringResource(R.string.action_search),
            onClick = onSearch,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = false,
        minLines = 1,
        maxLines = 3,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = omniTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SearchDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = false,
            minLines = 1,
            maxLines = 1,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = omniTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = RadioSurface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionText(option),
                            color = RadioText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = RadioPrimary,
                uncheckedColor = RadioTextMuted,
                checkmarkColor = RadioText,
            ),
        )
        Text(
            text = stringResource(R.string.online_search_include_broken),
            color = RadioText,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun OnlineStationResultItem(
    station: RadioBrowserStation,
    added: Boolean,
    selected: Boolean,
    showArtwork: Boolean,
    onPreviewStation: () -> Unit,
    onAddStation: () -> Unit,
) {
    StationListItem(
        title = station.title,
        tags = station.stationTags(),
        imageUrl = station.imageUrl,
        showArtwork = showArtwork,
        selected = selected,
        enabled = true,
        onClick = onPreviewStation,
        trailingContent = {
            OmniListActionIconButton(
                painter = painterResource(if (added) R.drawable.ic_check else R.drawable.ic_add_circle_outline),
                contentDescription = if (added) {
                    stringResource(R.string.online_search_station_added)
                } else {
                    stringResource(R.string.action_add_station)
                },
                enabled = !added,
                onClick = onAddStation,
                tint = if (added) RadioPrimary else RadioText,
                modifier = Modifier.padding(start = 12.dp),
            )
        },
    )
}

@Composable
private fun EmptyOnlineSearchState(
    hasQuery: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = if (hasQuery) {
                stringResource(R.string.online_search_no_stations_found)
            } else {
                stringResource(R.string.online_search_empty_title)
            },
            color = RadioText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (hasQuery) {
                stringResource(R.string.online_search_try_different)
            } else {
                stringResource(R.string.online_search_empty_subtitle)
            },
            color = RadioTextMuted,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

fun OnlineStationSearchState.toRadioBrowserParams(): RadioBrowserSearchParams {
    return RadioBrowserSearchParams(
        name = nameQuery,
        tags = tagsQuery,
        country = selectedCountry,
        language = selectedLanguage,
        sort = selectedSort,
        bitrateMin = bitrateMin.toIntOrNull(),
        bitrateMax = bitrateMax.toIntOrNull(),
        reverse = sortDirection.reverse,
        includeBroken = includeBroken,
    )
}

fun OnlineStationSearchState.toRadioBrowserParams(
    offset: Int,
    limit: Int = RadioBrowserSearchParams.DEFAULT_LIMIT,
): RadioBrowserSearchParams {
    return toRadioBrowserParams().copy(
        offset = offset,
        limit = limit,
    )
}

private fun OnlineStationSearchState.hasSearchInput(): Boolean {
    return nameQuery.isNotBlank() ||
        tagsQuery.isNotBlank() ||
        selectedCountry != null ||
        selectedLanguage != null ||
        bitrateMin.isNotBlank() ||
        bitrateMax.isNotBlank()
}

private fun String.digitsOnly(): String {
    return filter(Char::isDigit).take(5)
}
