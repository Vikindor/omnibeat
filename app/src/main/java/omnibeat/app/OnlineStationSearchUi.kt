package omnibeat.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnlineStationSearchState(
    val nameQuery: String = "",
    val tagsQuery: String = "",
    val selectedCountry: RadioBrowserFilterOption? = null,
    val selectedLanguage: RadioBrowserFilterOption? = null,
    val selectedSort: RadioBrowserSort = RadioBrowserSort.Clicks,
    val bitrateMin: String = "",
    val bitrateMax: String = "",
    val reverse: Boolean = true,
    val includeBroken: Boolean = false,
)

@Composable
fun OnlineStationSearchPage(
    searchState: OnlineStationSearchState,
    countries: List<RadioBrowserFilterOption>,
    languages: List<RadioBrowserFilterOption>,
    results: List<RadioBrowserStation>,
    loading: Boolean,
    optionsExpanded: Boolean,
    addedStreamUrls: Set<String>,
    selectedStreamUrl: String?,
    onSearchStateChange: (OnlineStationSearchState) -> Unit,
    onSearch: () -> Unit,
    onPreviewStation: (RadioBrowserStation) -> Unit,
    onAddStation: (RadioBrowserStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val overlayMaxHeight = (maxHeight - 16.dp).coerceAtLeast(240.dp)
        val listState = rememberLazyListState()

        if (!loading && results.isEmpty()) {
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
                    items(
                        items = results,
                        key = { it.stationUuid.ifBlank { it.streamUrl } },
                    ) { station ->
                        OnlineStationResultItem(
                            station = station,
                            added = station.streamUrl in addedStreamUrls,
                            selected = station.streamUrl == selectedStreamUrl,
                            onPreviewStation = { onPreviewStation(station) },
                            onAddStation = { onAddStation(station) },
                        )
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

        if (optionsExpanded) {
            SearchOptionsOverlay(
                searchState = searchState,
                countries = countries,
                languages = languages,
                loading = loading,
                onSearchStateChange = onSearchStateChange,
                onSearch = onSearch,
                maxHeight = overlayMaxHeight,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp),
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
                    text = "Search options",
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
                    contentDescription = if (expanded) "Hide options" else "Show options",
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
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
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
            label = "Search by title",
            placeholder = "Station title",
            imeAction = ImeAction.Search,
            keyboardType = KeyboardType.Text,
            onSearch = onSearch,
        )
        SearchTextField(
            value = searchState.tagsQuery,
            onValueChange = { onSearchStateChange(searchState.copy(tagsQuery = it)) },
            label = "Search by tags",
            placeholder = "Comma separated",
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
            text = "Filters",
            color = RadioText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SearchDropdown(
            label = "Countries",
            selectedText = searchState.selectedCountry?.name ?: "All countries",
            options = listOf<RadioBrowserFilterOption?>(null) + countries,
            optionText = { it?.name ?: "All countries" },
            onOptionSelected = { onSearchStateChange(searchState.copy(selectedCountry = it)) },
            modifier = Modifier.padding(top = 10.dp),
        )
        SearchDropdown(
            label = "Languages",
            selectedText = searchState.selectedLanguage?.name ?: "All languages",
            options = listOf<RadioBrowserFilterOption?>(null) + languages,
            optionText = { it?.name ?: "All languages" },
            onOptionSelected = { onSearchStateChange(searchState.copy(selectedLanguage = it)) },
            modifier = Modifier.padding(top = 10.dp),
        )

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )
        SearchDropdown(
            label = "Sort by",
            selectedText = searchState.selectedSort.label,
            options = RadioBrowserSort.entries,
            optionText = { it.label },
            onOptionSelected = { onSearchStateChange(searchState.copy(selectedSort = it)) },
        )
        SearchTextField(
            value = searchState.bitrateMin,
            onValueChange = { onSearchStateChange(searchState.copy(bitrateMin = it.digitsOnly())) },
            label = "Bitrate min",
            placeholder = "0",
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Number,
            onSearch = onSearch,
            modifier = Modifier.padding(top = 10.dp),
        )
        SearchTextField(
            value = searchState.bitrateMax,
            onValueChange = { onSearchStateChange(searchState.copy(bitrateMax = it.digitsOnly())) },
            label = "Bitrate max",
            placeholder = "max",
            imeAction = ImeAction.Search,
            keyboardType = KeyboardType.Number,
            onSearch = onSearch,
            modifier = Modifier.padding(top = 10.dp),
        )
        SearchCheckbox(
            checked = searchState.reverse,
            text = "Show results in reverse order",
            onCheckedChange = { onSearchStateChange(searchState.copy(reverse = it)) },
            modifier = Modifier.padding(top = 8.dp),
        )
        SearchCheckbox(
            checked = searchState.includeBroken,
            text = "Include stations marked as broken",
            onCheckedChange = { onSearchStateChange(searchState.copy(includeBroken = it)) },
        )
        OmniPrimaryButton(
            text = "Search",
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
    optionText: (T) -> String,
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
                        expanded = false
                        onOptionSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchCheckbox(
    checked: Boolean,
    text: String,
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
            text = text,
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
    onPreviewStation: () -> Unit,
    onAddStation: () -> Unit,
) {
    StationListItem(
        title = station.title,
        tags = station.resultTags(),
        selected = selected,
        enabled = true,
        onClick = onPreviewStation,
        trailingContent = {
            OmniListActionIconButton(
                painter = painterResource(R.drawable.ic_add_circle_outline),
                contentDescription = if (added) "Station added" else "Add station",
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
            text = if (hasQuery) "No stations found" else "Search Radio-Browser.info",
            color = RadioText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (hasQuery) {
                "Try a different search or filters"
            } else {
                "Find stations online and add them to your library"
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
        reverse = reverse,
        includeBroken = includeBroken,
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

private fun OnlineStationSearchState.summary(): String {
    return buildList {
        nameQuery.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        tagsQuery.trim().takeIf { it.isNotBlank() }?.let { add("tags: $it") }
        selectedCountry?.name?.let(::add)
        selectedLanguage?.name?.let(::add)
        add("sort: ${selectedSort.label}")
    }.joinToString(" / ")
}

private fun RadioBrowserStation.resultTags(): List<String> {
    return buildList {
        countryCode.takeIf { it.isNotBlank() }?.let(::add)
        codec.takeIf { it.isNotBlank() }?.let(::add)
        bitrate.takeIf { it > 0 }?.let { add("$it kbps") }
        addAll(tags.take(4))
    }
}

private fun String.digitsOnly(): String {
    return filter(Char::isDigit).take(5)
}
