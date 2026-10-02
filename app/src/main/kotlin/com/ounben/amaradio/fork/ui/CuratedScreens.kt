package com.ounben.amaradio.fork.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.ui.graphics.RectangleShape
import com.ounben.amaradio.ui.LocalPlayingStationUuid
import com.ounben.amaradio.ui.StationIcon
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import com.ounben.amaradio.fork.curated.ScheduleSlot
import com.ounben.amaradio.fork.curated.ProgrammeSchedule
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.produceState
import com.ounben.amaradio.fork.curated.CuratedSource
import com.ounben.amaradio.fork.curated.CuratedSourceState
import com.ounben.amaradio.fork.curated.CuratedViewModel
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.CenteredLoadingIndicator
import com.ounben.amaradio.ui.ListMessage
import com.ounben.amaradio.ui.StationListItem
import com.ounben.amaradio.ui.StationOptionsDialog

private val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

/**
 * The curated part of the China page: the bundled playlist as sections (first group as a
 * carousel, the others as artwork rows) followed by the playlists. "See all" on the first
 * section opens its region (Beijing) with every station of the catalog.
 */
internal fun LazyListScope.curatedSections(
    featured: CuratedSourceState,
    morePlaylists: List<CuratedSourceState>,
    onStationClick: (DataRadioStation) -> Unit,
    onStationLongClick: (DataRadioStation) -> Unit,
    onOpenPlaylist: (CuratedSource) -> Unit,
    onOpenRegion: (String) -> Unit
) {
    val playlist = featured.playlist
    if (playlist == null && featured.isLoading) {
        item(key = "featured-loading") { CenteredLoadingIndicator(Modifier.height(200.dp)) }
    }
    // Groups of the bundled list in order, without the backup streams.
    val sections = playlist?.primaryStations.orEmpty().groupBy { it.TagsAll }.toList()
    sections.forEachIndexed { index, (group, stations) ->
        val region = if (index == 0) stations.firstNotNullOfOrNull { playlist?.regions?.get(it.StationUuid) } else null
        item(key = "featured-header-$group") {
            SectionHeader(
                title = group,
                actionLabel = if (region != null) stringResource(R.string.fork_home_see_all) else null,
                onAction = { region?.let(onOpenRegion) }
            )
        }
        item(key = "featured-row-$group") {
            if (index == 0) StationCarousel(stations, onStationClick, onStationLongClick)
            else StationTileRow(stations, onStationClick, onStationLongClick)
        }
    }
    if (morePlaylists.isNotEmpty()) {
        item(key = "more-header") { SectionHeader(title = stringResource(R.string.fork_curated_more)) }
        morePlaylists.forEach { state ->
            item(key = "card-${state.source.id}") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PlaylistCard(state = state, onClick = { onOpenPlaylist(state.source) })
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun NowPlayingBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.fork_now_playing), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Material 3 multi-browse carousel of large station artwork. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun StationCarousel(
    stations: List<DataRadioStation>,
    onStationClick: (DataRadioStation) -> Unit,
    onLongClick: (DataRadioStation) -> Unit
) {
    val playing = LocalPlayingStationUuid.current
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { stations.size },
        preferredItemWidth = 176.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(204.dp)
    ) { index ->
        val station = stations[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .combinedClickable(onClick = { onStationClick(station) }, onLongClick = { onLongClick(station) })
        ) {
            StationIcon(
                stationName = station.Name,
                stationUuid = station.StationUuid,
                iconUrl = station.IconUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape
            )
            if (station.StationUuid == playing) {
                NowPlayingBadge(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
        }
    }
}

/** Horizontally scrolling station tiles (logo and name). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StationTileRow(
    stations: List<DataRadioStation>,
    onStationClick: (DataRadioStation) -> Unit,
    onLongClick: (DataRadioStation) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stations, key = { "tile-${it.StationUuid}" }) { station ->
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .clip(MaterialTheme.shapes.large)
                    .combinedClickable(onClick = { onStationClick(station) }, onLongClick = { onLongClick(station) })
            ) {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.size(112.dp),
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = station.Name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

/** Full-screen view of one curated playlist, with its own top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuratedPlaylistScreen(
    viewModel: CuratedViewModel,
    source: CuratedSource,
    onStationClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onBack: () -> Unit
) {
    val sources by viewModel.sources.collectAsState()
    val state = sources[source.id] ?: CuratedSourceState(source)
    var selectedGroup by rememberSaveable(source.id) { mutableStateOf<String?>(null) }
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }
    val context = LocalContext.current

    LaunchedEffect(source.id) { viewModel.load(source) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            // MainScreen's Scaffold already applies the status bar inset.
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(source.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                }
            },
            actions = {
                IconButton(onClick = { viewModel.load(source, forceRefresh = true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.fork_curated_refresh))
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "header") {
                PlaylistHeader(
                    state = state,
                    showTitle = false,
                    onAddAll = {
                        val added = viewModel.addAllToFavourites(source)
                        val message = if (added > 0) context.getString(R.string.fork_curated_added, added)
                        else context.getString(R.string.fork_curated_nothing_added)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    onRefresh = null
                )
            }
            playlistBody(
                state = state,
                selectedGroup = selectedGroup,
                onGroupSelected = { selectedGroup = it },
                onStationClick = onStationClick,
                onFavoriteClick = { viewModel.toggleFavourite(it) },
                onLongClick = { stationWithOptions = it },
                isFavorite = isFavorite,
                onRetry = { viewModel.load(source, forceRefresh = true) }
            )
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { viewModel.toggleFavourite(station) },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@Composable
private fun PlaylistHeader(
    state: CuratedSourceState,
    onAddAll: () -> Unit,
    onRefresh: (() -> Unit)?,
    showTitle: Boolean = true
) {
    val uriHandler = LocalUriHandler.current
    val playlist = state.playlist
    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
        if (showTitle) {
            Text(
                text = stringResource(state.source.titleRes),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = stringResource(state.source.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (playlist != null) {
            Spacer(Modifier.height(4.dp))
            val freshness = playlist.fetchedAt?.let {
                stringResource(
                    R.string.fork_curated_updated,
                    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                )
            } ?: stringResource(R.string.fork_curated_bundled)
            Text(
                text = stationCountText(playlist.stations.size) + " · " + freshness,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onAddAll, enabled = playlist != null) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fork_curated_add_all))
            }
            val homepage = state.source.homepageUrl
            if (homepage != null && !state.source.isBundled) {
                TextButton(onClick = { runCatching { uriHandler.openUri(homepage) } }) {
                    Text(stringResource(R.string.fork_curated_source))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.fork_curated_refresh))
                }
            }
        }
    }
}

private fun LazyListScope.playlistBody(
    state: CuratedSourceState,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    onLongClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onRetry: () -> Unit
) {
    val playlist = state.playlist
    when {
        playlist == null && state.isLoading -> item(key = "${state.source.id}-loading") {
            CenteredLoadingIndicator(Modifier.height(200.dp))
        }
        playlist == null -> item(key = "${state.source.id}-error") {
            Box(Modifier.height(280.dp)) {
                ListMessage(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.fork_curated_load_failed),
                    body = state.error,
                    action = { FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.fork_action_retry)) } }
                )
            }
        }
        else -> {
            if (playlist.groups.size > 1) {
                item(key = "${state.source.id}-groups") {
                    GroupChips(
                        groups = playlist.groups,
                        selected = selectedGroup,
                        onSelected = onGroupSelected
                    )
                }
            }
            val visible = if (selectedGroup == null) playlist.stations
            else playlist.stations.filter { it.TagsAll == selectedGroup }
            itemsIndexed(visible, key = { _, station -> "${state.source.id}-${station.StationUuid}" }) { index, station ->
                StationListItem(
                    station = station,
                    isFavorite = isFavorite(station.StationUuid),
                    onClick = { onStationClick(station) },
                    onFavoriteClick = { onFavoriteClick(station) },
                    onLongClick = { onLongClick(station) },
                    index = index,
                    count = visible.size,
                    supportingOverride = playlist.schedules[station.StationUuid]?.let { scheduleText(it) }
                )
            }
        }
    }
}

@Composable
private fun GroupChips(groups: List<String>, selected: String?, onSelected: (String?) -> Unit) {
    ChoiceChips(
        options = groups,
        selected = selected,
        label = { it },
        onSelected = onSelected,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** A row of filter chips: "All" followed by [options]; selecting "All" passes null. */
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listOf<T?>(null) + options) { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(option) },
                label = { Text(if (option == null) stringResource(R.string.fork_curated_all) else label(option)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistCard(state: CuratedSourceState, onClick: () -> Unit) {
    val shape = MaterialShapes.Clover4Leaf.toShape()
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(state.source.titleRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(state.source.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.playlist?.let { stationCountText(it.stations.size) }
                        ?: stringResource(R.string.fork_curated_downloaded_on_open),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * When a scheduled programme is on, in the device's time zone: "On air now" during a
 * broadcast, otherwise the next start ("Next: today 23:10", "Next: Fri 01:10").
 */
@Composable
internal fun scheduleText(slots: List<ScheduleSlot>): String? {
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            delay(30_000)
            value = ZonedDateTime.now()
        }
    }
    val airing = remember(slots, now.withSecond(0).withNano(0)) { ProgrammeSchedule.nextAiring(slots, now) } ?: return null
    if (airing.isOnAir(now)) return stringResource(R.string.fork_schedule_on_air)
    val locale = LocalConfiguration.current.locales[0]
    val start = airing.start.withZoneSameInstant(now.zone)
    val time = start.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    val day = when (start.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.fork_schedule_today, time)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.fork_schedule_tomorrow, time)
        else -> start.format(DateTimeFormatter.ofPattern("EEE", locale)) + " " + time
    }
    return stringResource(R.string.fork_schedule_next, day)
}
