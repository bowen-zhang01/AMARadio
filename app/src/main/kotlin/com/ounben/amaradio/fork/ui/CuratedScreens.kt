package com.ounben.amaradio.fork.ui

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import com.ounben.amaradio.fork.curated.CuratedSource
import com.ounben.amaradio.fork.curated.CuratedSourceState
import com.ounben.amaradio.fork.curated.CuratedSources
import com.ounben.amaradio.fork.curated.CuratedViewModel
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.CenteredLoadingIndicator
import com.ounben.amaradio.ui.ListMessage
import com.ounben.amaradio.ui.StationListItem
import com.ounben.amaradio.ui.StationOptionsDialog

private val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

/**
 * Home tab: the bundled Beijing + national playlist first, then entry points to the larger
 * community playlists.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: CuratedViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onOpenPlaylist: (CuratedSource) -> Unit
) {
    val sources by viewModel.sources.collectAsState()
    val featured = sources[CuratedSources.beijingNational.id] ?: return
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item(key = "featured-header") {
            PlaylistHeader(
                state = featured,
                onAddAll = {
                    val added = viewModel.addAllToFavourites(featured.source)
                    val message = if (added > 0) context.getString(R.string.fork_curated_added, added)
                    else context.getString(R.string.fork_curated_nothing_added)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onRefresh = { viewModel.load(featured.source, forceRefresh = true) }
            )
        }
        playlistBody(
            state = featured,
            selectedGroup = selectedGroup,
            onGroupSelected = { selectedGroup = it },
            onStationClick = onStationClick,
            onFavoriteClick = { viewModel.toggleFavourite(it) },
            onLongClick = { stationWithOptions = it },
            isFavorite = isFavorite,
            onRetry = { viewModel.load(featured.source, forceRefresh = true) }
        )

        item(key = "more-header") {
            Text(
                text = stringResource(R.string.fork_curated_more),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp, start = 4.dp)
            )
        }
        CuratedSources.all.filterNot { it.isBundled }.forEach { source ->
            item(key = "card-${source.id}") {
                PlaylistCard(state = sources[source.id] ?: CuratedSourceState(source), onClick = { onOpenPlaylist(source) })
            }
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
                text = stringResource(R.string.fork_curated_stations_count, playlist.stations.size) + " · " + freshness,
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
                    count = visible.size
                )
            }
        }
    }
}

@Composable
private fun GroupChips(groups: List<String>, selected: String?, onSelected: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val all = listOf<String?>(null) + groups
        all.forEach { group ->
            val isSelected = group == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(group) },
                label = { Text(group ?: stringResource(R.string.fork_curated_all)) },
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
                    text = state.playlist?.let { stringResource(R.string.fork_curated_stations_count, it.stations.size) }
                        ?: stringResource(R.string.fork_curated_downloaded_on_open),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
