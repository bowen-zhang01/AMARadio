package com.ounben.amaradio.fork.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ounben.amaradio.R
import com.ounben.amaradio.fork.curated.CuratedSource
import com.ounben.amaradio.fork.curated.CuratedSources
import com.ounben.amaradio.fork.curated.CuratedViewModel
import com.ounben.amaradio.fork.world.Continent
import com.ounben.amaradio.fork.world.Countries
import com.ounben.amaradio.fork.world.CountryItem
import com.ounben.amaradio.fork.world.CountryViewModel
import com.ounben.amaradio.fork.world.WorldViewModel
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.CenteredLoadingIndicator
import com.ounben.amaradio.ui.ListMessage
import com.ounben.amaradio.ui.StationListItem
import com.ounben.amaradio.ui.StationOptionsDialog
import com.ounben.amaradio.utils.EmojiUtils
import java.text.NumberFormat
import java.util.Locale

/** Emoji of the "Popular worldwide" page: the globe showing Asia and Australia. */
private const val WORLD_EMOJI = "🌏"

/**
 * World tab, the app's start screen: shortcuts to nearby and recent countries, then every
 * country of the catalog grouped by continent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorldScreen(
    viewModel: WorldViewModel,
    onOpenCountry: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var continent by rememberSaveable { mutableStateOf<Continent?>(null) }
    val showEnglishNames = uiLocale().language != "en"

    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    when {
        state.isLoading -> CenteredLoadingIndicator()
        state.countries.isEmpty() -> ListMessage(
            icon = Icons.Rounded.Public,
            title = stringResource(R.string.fork_world_catalog_empty),
            body = stringResource(R.string.fork_world_catalog_empty_desc),
            action = { FilledTonalButton(onClick = viewModel::refresh) { Text(stringResource(R.string.fork_action_retry)) } }
        )
        else -> {
            val continents = remember(state.countries) {
                state.countries.map { it.continent }.distinct().sortedBy { it.ordinal }
            }
            val sections = remember(state.countries, continent) {
                state.countries
                    .filter { continent == null || it.continent == continent }
                    .groupBy { it.continent }
                    .toSortedMap(compareBy { it.ordinal })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") { WorldHeader(countryCount = state.countries.size, stationCount = state.totalStations) }
                item(key = "shortcuts") {
                    ShortcutRow(shortcuts = state.shortcuts, onOpenCountry = onOpenCountry)
                }
                stickyHeader(key = "continents") {
                    ChoiceChips(
                        options = continents,
                        selected = continent,
                        label = { stringResource(it.titleRes) },
                        onSelected = { continent = it },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    )
                }
                sections.forEach { (sectionContinent, countries) ->
                    if (continent == null) {
                        item(key = "continent-${sectionContinent.name}") {
                            ContinentHeader(title = stringResource(sectionContinent.titleRes), count = countries.size)
                        }
                    }
                    itemsIndexed(countries, key = { _, country -> "country-${country.code}" }) { index, country ->
                        CountryListItem(
                            country = country,
                            showEnglishName = showEnglishNames,
                            onClick = { onOpenCountry(country.code) },
                            index = index,
                            count = countries.size,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldHeader(countryCount: Int, stationCount: Int) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..10 -> R.string.fork_greeting_morning
        in 11..17 -> R.string.fork_greeting_afternoon
        in 18..22 -> R.string.fork_greeting_evening
        else -> R.string.fork_greeting_night
    }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
        Text(stringResource(greeting), style = MaterialTheme.typography.headlineLarge)
        Text(
            text = stringResource(R.string.fork_world_summary, formatCount(countryCount), formatCount(stationCount)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Popular worldwide" followed by the device, language and recently opened countries. */
@Composable
private fun ShortcutRow(shortcuts: List<CountryItem>, onOpenCountry: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        item(key = Countries.WORLD) {
            ShortcutCard(
                emoji = WORLD_EMOJI,
                title = stringResource(R.string.fork_world_popular_short),
                subtitle = stringResource(R.string.fork_world_popular_desc_short),
                highlighted = true,
                onClick = { onOpenCountry(Countries.WORLD) }
            )
        }
        items(shortcuts, key = { it.code }) { country ->
            ShortcutCard(
                emoji = EmojiUtils.getFlagEmoji(country.code).orEmpty(),
                title = country.name,
                subtitle = stationCountText(country.stationCount),
                highlighted = false,
                onClick = { onOpenCountry(country.code) }
            )
        }
    }
}

@Composable
private fun ShortcutCard(emoji: String, title: String, subtitle: String, highlighted: Boolean, onClick: () -> Unit) {
    val colors = if (highlighted) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    Card(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, colors = colors, modifier = Modifier.width(140.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 34.sp, lineHeight = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContinentHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(formatCount(count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountryListItem(
    country: CountryItem,
    showEnglishName: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    val stations = stationCountText(country.stationCount)
    val supporting = if (showEnglishName && !country.englishName.equals(country.name, ignoreCase = true)) {
        "${country.englishName} · $stations"
    } else stations
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = { FlagBadge(country.code) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
    ) {
        Text(country.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FlagBadge(code: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(EmojiUtils.getFlagEmoji(code).orEmpty(), fontSize = 24.sp)
    }
}

/** Matching countries above the station results of the search screen. */
internal fun LazyListScope.countrySearchResults(
    countries: List<CountryItem>,
    hasStations: Boolean,
    onCountryClick: (CountryItem) -> Unit
) {
    if (countries.isEmpty()) return
    item(key = "search-countries") { SearchSectionTitle(stringResource(R.string.fork_search_countries)) }
    itemsIndexed(countries, key = { _, country -> "search-country-${country.code}" }) { index, country ->
        CountryListItem(
            country = country,
            showEnglishName = uiLocale().language != "en",
            onClick = { onCountryClick(country) },
            index = index,
            count = countries.size
        )
    }
    if (hasStations) item(key = "search-stations") { SearchSectionTitle(stringResource(R.string.fork_search_stations)) }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

/**
 * One country (or [Countries.WORLD]): for China the curated playlists first, then every
 * station of the catalog, most played first, with region filters where the catalog's
 * regions can be told apart.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CountryScreen(
    viewModel: CountryViewModel,
    country: CountryItem?,
    curatedViewModel: CuratedViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (DataRadioStation) -> Unit,
    onOpenPlaylist: (CuratedSource) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val curated by curatedViewModel.sources.collectAsState()
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }
    val locale = uiLocale()
    val isChinese = locale.language == "zh"
    val showCurated = viewModel.code == "CN"
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val title = if (viewModel.isWorld) "$WORLD_EMOJI  " + stringResource(R.string.fork_world_popular)
    else EmojiUtils.getFlagEmoji(viewModel.code).orEmpty() + "  " + (country?.name ?: Countries.displayName(viewModel.code, locale))
    val subtitle = when {
        viewModel.isWorld -> stringResource(R.string.fork_world_popular_desc)
        country != null -> stationCountText(country.stationCount)
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        MediumFlexibleTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                }
            },
            // MainScreen's Scaffold already applies the status bar inset.
            windowInsets = WindowInsets(0, 0, 0, 0),
            scrollBehavior = scrollBehavior
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (showCurated) {
                val featured = curated[CuratedSources.beijingNational.id]
                if (featured != null) {
                    curatedSections(
                        featured = featured,
                        morePlaylists = CuratedSources.all.filterNot { it.isBundled }.mapNotNull { curated[it.id] },
                        onStationClick = onStationClick,
                        onStationLongClick = { stationWithOptions = it },
                        onOpenPlaylist = onOpenPlaylist
                    )
                }
                item(key = "all-header") { SectionHeader(title = stringResource(R.string.fork_country_all_stations)) }
            }
            if (state.regions.size > 1) {
                item(key = "regions") {
                    ChoiceChips(
                        options = state.regions.map { it.id },
                        selected = state.selectedRegion,
                        label = { id ->
                            val region = state.regions.first { it.id == id }
                            if (isChinese) region.labelZh ?: region.label else region.label
                        },
                        onSelected = viewModel::selectRegion,
                        modifier = Modifier.padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    )
                }
            }
            when {
                state.isLoading -> item(key = "loading") { CenteredLoadingIndicator(Modifier.height(240.dp)) }
                state.stations.isEmpty() -> item(key = "empty") {
                    Box(Modifier.height(280.dp)) {
                        ListMessage(icon = Icons.Rounded.SearchOff, title = stringResource(R.string.fork_country_no_stations))
                    }
                }
                else -> {
                    itemsIndexed(state.stations, key = { _, station -> "station-${station.StationUuid}" }) { index, station ->
                        StationListItem(
                            station = station,
                            isFavorite = isFavorite(station.StationUuid),
                            onClick = { onStationClick(station) },
                            onFavoriteClick = { onToggleFavorite(station) },
                            onLongClick = { stationWithOptions = station },
                            index = index,
                            count = state.stations.size,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap)
                        )
                    }
                    if (state.isTruncated) item(key = "truncated") {
                        Text(
                            text = stringResource(R.string.fork_country_truncated, formatCount(state.stations.size)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onToggleFavorite(station) },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@Composable
internal fun uiLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun formatCount(count: Int): String = NumberFormat.getIntegerInstance(uiLocale()).format(count)

@Composable
internal fun stationCountText(count: Int): String =
    pluralStringResource(R.plurals.fork_station_count, count, formatCount(count))
