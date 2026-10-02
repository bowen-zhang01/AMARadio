package com.ounben.amaradio.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.ounben.amaradio.R
import com.ounben.amaradio.fork.ui.StationDetails
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils
import com.ounben.amaradio.utils.StationPlaceholderUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.io.FileOutputStream

/** UUID of the station currently loaded in the player; station lists highlight it. */
val LocalPlayingStationUuid = compositionLocalOf<String?> { null }

private val ListContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

/**
 * Station logo clipped to [shape]. Stations without a logo get a placeholder with their
 * short name on a tonal container color, so placeholders follow the (dynamic) theme.
 */
@Composable
fun StationIcon(
    stationName: String,
    stationUuid: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium
) {
    val context = LocalContext.current
    val placeholderText = remember(stationName) { StationPlaceholderUtils.extractPlaceholderText(stationName) }
    val colors = MaterialTheme.colorScheme
    val (placeholderContainer, placeholderContent) = remember(stationUuid, colors) {
        val pairs = listOf(
            colors.primaryContainer to colors.onPrimaryContainer,
            colors.secondaryContainer to colors.onSecondaryContainer,
            colors.tertiaryContainer to colors.onTertiaryContainer
        )
        pairs[(stationUuid.hashCode() and 0x7FFFFFFF) % pairs.size]
    }

    val iconFile = remember(stationUuid) { File(File(context.filesDir, "station_icons"), "$stationUuid.jpg") }
    // Only real logos are loaded; stations without one get the themed placeholder below.
    // station_icons/ is not consulted for them because PlayerService stores its generated
    // notification placeholders there too. Remote logos rely on Coil's disk cache and are
    // still copied to station_icons/ for the content provider (notifications, Android Auto).
    val model: Any? = remember(stationUuid, iconUrl) {
        when {
            iconUrl.isNullOrBlank() || iconUrl == "null" -> null
            iconUrl.startsWith("file:/") -> Uri.parse(iconUrl)
            iconUrl.startsWith("http") -> iconUrl
            else -> null
        }
    }

    // Full-bleed artwork bundled with the curated playlists fills its shape; other logos
    // are inset and fitted on white, as most broadcaster logos expect.
    val isArtwork = iconUrl?.contains("/curated_logos/") == true
    var showPlaceholder by remember(model) { mutableStateOf(true) }

    val imageRequest = remember(model, stationUuid) {
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(512, 512)
                .allowHardware(false)
                .crossfade(true)
                .listener(onSuccess = { _, result ->
                    if (it is String && !iconFile.exists()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                iconFile.parentFile?.mkdirs()
                                val bitmap = result.drawable.toBitmap(512, 512, Bitmap.Config.RGB_565)
                                FileOutputStream(iconFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                })
                .build()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(if (showPlaceholder) placeholderContainer else Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (showPlaceholder) {
            val fontSize = with(LocalDensity.current) {
                (maxWidth * if (placeholderText.length >= 3) 0.25f else 0.34f).toSp()
            }
            Text(
                text = placeholderText,
                color = placeholderContent,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = if (isArtwork) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(maxWidth * 0.06f),
                contentScale = if (isArtwork) ContentScale.Crop else ContentScale.Fit,
                onState = { state -> showPlaceholder = state !is AsyncImagePainter.State.Success }
            )
        }
    }
}

/** Centered empty / error state used by every station list. */
@Composable
fun ListMessage(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenteredLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StationListTemplate(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    isLoading: Boolean = false,
    error: String? = null,
    emptyMessage: String = stringResource(R.string.fork_no_stations),
    onRetry: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { onRefresh?.invoke() },
        state = refreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = isLoading && stations.isNotEmpty(),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        when {
            isLoading && stations.isEmpty() -> CenteredLoadingIndicator()
            error != null -> ListMessage(
                icon = Icons.Outlined.WifiOff,
                title = error,
                action = onRetry?.let { retry ->
                    { FilledTonalButton(onClick = retry) { Text(stringResource(R.string.fork_action_retry)) } }
                }
            )
            stations.isEmpty() -> ListMessage(icon = Icons.Outlined.Radio, title = emptyMessage)
            else -> StationList(
                stations = stations,
                isGrid = isGrid,
                onStationClick = onStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = isFavorite,
                onDeleteClick = onDeleteClick
            )
        }
    }
}

@Composable
fun StationList(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Items above the stations (list mode only), e.g. matching countries in search. */
    header: (LazyListScope.() -> Unit)? = null
) {
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = stations,
                key = { _, station -> station.StationUuid },
                contentType = { _, _ -> "station" }
            ) { _, station ->
                StationGridItem(
                    station = station,
                    isFavorite = isFavorite(station.StationUuid),
                    onClick = { onStationClick(station) },
                    onFavoriteClick = { onFavoriteClick(station) },
                    onLongClick = { stationWithOptions = station }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            header?.invoke(this)
            itemsIndexed(
                items = stations,
                key = { _, station -> station.StationUuid },
                contentType = { _, _ -> "station" }
            ) { index, station ->
                StationListItem(
                    station = station,
                    isFavorite = isFavorite(station.StationUuid),
                    onClick = { onStationClick(station) },
                    onFavoriteClick = { onFavoriteClick(station) },
                    onLongClick = { stationWithOptions = station },
                    index = index,
                    count = stations.size
                )
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDeleteClick = onDeleteClick?.let { { it(station) } },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackList(
    tracks: LazyPagingItems<TrackHistoryEntry>,
    onTrackClick: (TrackHistoryEntry) -> Unit,
    onTrackLongClick: (TrackHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRefreshing = tracks.loadState.refresh is LoadState.Loading
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { tracks.refresh() },
        state = refreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            items(
                count = tracks.itemCount,
                key = { index ->
                    val track = tracks.peek(index)
                    if (track != null) "${track.uid}_$index" else "placeholder_$index"
                },
                contentType = { "track" }
            ) { index ->
                tracks[index]?.let { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onLongClick = { onTrackLongClick(track) },
                        index = index,
                        count = tracks.itemCount
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackListItem(
    track: TrackHistoryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    index: Int = 0,
    count: Int = 1
) {
    val context = LocalContext.current
    val displayTitle = track.track.ifBlank { track.title }
    val displayArtist = if (track.track.isNotBlank()) track.artist else ""

    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = {
            StationIcon(
                stationName = track.stationName.ifEmpty { track.stationUuid },
                stationUuid = track.stationUuid,
                iconUrl = track.stationIconUrl,
                modifier = Modifier.size(44.dp)
            )
        },
        supportingContent = if (displayArtist.isNotBlank() && displayArtist != displayTitle) {
            { Text(displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        trailingContent = {
            Text(
                text = track.getFormattedTime(context),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FavoriteToggle(isFavorite: Boolean, onFavoriteClick: () -> Unit) {
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onFavoriteClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = stringResource(
                if (isFavorite) R.string.accessibility_favorite_selected
                else R.string.accessibility_favorite_not_selected
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StationListItem(
    station: DataRadioStation,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") useInternalClickable: Boolean = true,
    dragHandle: (@Composable (Modifier) -> Unit)? = null,
    index: Int = 0,
    count: Int = 1
) {
    val context = LocalContext.current
    val isPlaying = LocalPlayingStationUuid.current == station.StationUuid
    val flagEmoji = remember(station.CountryCode) { EmojiUtils.getFlagEmoji(station.CountryCode) ?: "" }
    val locale = LocalConfiguration.current.locales[0]
    val details = remember(station.ClickCount, station.Language, station.Bitrate, station.Codec, locale) {
        StationDetails.short(station, context, locale)
    }
    val supporting = listOf(flagEmoji, details, station.TagsAll.takeIf { details.isBlank() }.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" ")

    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { stringResource(R.string.not_applicable) },
        station.TagsAll.ifEmpty { stringResource(R.string.not_applicable) }
    )

    SegmentedListItem(
        selected = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = accessibilityDesc },
        leadingContent = {
            StationIcon(
                stationName = station.Name,
                stationUuid = station.StationUuid,
                iconUrl = station.IconUrl,
                modifier = Modifier.size(52.dp)
            )
        },
        supportingContent = if (supporting.isNotBlank() || isPlaying) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlaying) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = stringResource(R.string.accessibility_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else null,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dragHandle?.invoke(Modifier)
                FavoriteToggle(isFavorite, onFavoriteClick)
            }
        }
    ) {
        Text(station.Name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationGridItem(
    station: DataRadioStation,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    useInternalClickable: Boolean = true,
    dragHandle: (@Composable (Modifier) -> Unit)? = null
) {
    val isPlaying = LocalPlayingStationUuid.current == station.StationUuid
    val accessibilityDesc = stringResource(
        R.string.accessibility_station_description,
        station.Name,
        station.Language.ifEmpty { stringResource(R.string.not_applicable) },
        station.TagsAll.ifEmpty { stringResource(R.string.not_applicable) }
    )

    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = accessibilityDesc },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (useInternalClickable) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    else Modifier
                )
                .padding(10.dp)
        ) {
            Box {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    FavoriteToggle(isFavorite, onFavoriteClick)
                }
                if (dragHandle != null) {
                    Box(modifier = Modifier.align(Alignment.TopStart)) { dragHandle(Modifier) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = stringResource(R.string.accessibility_playing),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = station.Name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DragHandleIcon(modifier: Modifier) {
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReorderableStationList(
    stations: List<DataRadioStation>,
    isGrid: Boolean,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onReorder: (List<DataRadioStation>) -> Unit,
    onDeleteClick: ((DataRadioStation) -> Unit)? = null,
    onLongClick: ((DataRadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stationsLocal = remember { mutableStateListOf<DataRadioStation>() }
    var stationWithOptions by remember { mutableStateOf<DataRadioStation?>(null) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(stations) {
        if (stationsLocal.toList() != stations) {
            stationsLocal.clear()
            stationsLocal.addAll(stations)
        }
    }

    val longClickFor: (DataRadioStation) -> Unit = { station ->
        if (onLongClick != null) onLongClick(station) else stationWithOptions = station
    }

    if (isGrid) {
        val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
            stationsLocal.add(to.index, stationsLocal.removeAt(from.index))
            onReorder(stationsLocal.toList())
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(152.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(stationsLocal, key = { _, s -> s.StationUuid }) { _, station ->
                ReorderableItem(reorderableState, key = station.StationUuid) { isDragging ->
                    val itemScope = this
                    StationGridItem(
                        station = station,
                        isFavorite = isFavorite(station.StationUuid),
                        onClick = { onStationClick(station) },
                        onFavoriteClick = { onFavoriteClick(station) },
                        onLongClick = { longClickFor(station) },
                        modifier = Modifier.graphicsLayer {
                            val scale = if (isDragging) 1.05f else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                        dragHandle = { with(itemScope) { DragHandleIcon(Modifier.draggableHandle()) } }
                    )
                }
            }
        }
    } else {
        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
            stationsLocal.add(to.index, stationsLocal.removeAt(from.index))
            onReorder(stationsLocal.toList())
        }

        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            itemsIndexed(stationsLocal, key = { _, s -> s.StationUuid }) { index, station ->
                ReorderableItem(reorderableState, key = station.StationUuid) { isDragging ->
                    val itemScope = this
                    val lift by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag-lift")
                    StationListItem(
                        station = station,
                        isFavorite = isFavorite(station.StationUuid),
                        onClick = { onStationClick(station) },
                        onFavoriteClick = { onFavoriteClick(station) },
                        onLongClick = { longClickFor(station) },
                        modifier = Modifier.graphicsLayer {
                            shadowElevation = lift.toPx()
                            val scale = if (isDragging) 1.02f else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                        dragHandle = { with(itemScope) { DragHandleIcon(Modifier.draggableHandle()) } },
                        index = index,
                        count = stationsLocal.size
                    )
                }
            }
        }
    }

    stationWithOptions?.let { station ->
        StationOptionsDialog(
            station = station,
            isFavorite = isFavorite(station.StationUuid),
            onFavoriteClick = { onFavoriteClick(station) },
            onDeleteClick = onDeleteClick?.let { { it(station) } },
            onDismiss = { stationWithOptions = null }
        )
    }
}

@Composable
fun CategoryList(
    categories: List<DataCategory>,
    isGrid: Boolean,
    onCategoryClick: (DataCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.Name}_$index" },
                contentType = { _, _ -> "category" }
            ) { _, category ->
                CategoryGridItem(category = category, onClick = { onCategoryClick(category) })
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.Name}_$index" },
                contentType = { _, _ -> "category" }
            ) { index, category ->
                CategoryListItem(
                    category = category,
                    onClick = { onCategoryClick(category) },
                    index = index,
                    count = categories.size
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryListItem(
    category: DataCategory,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 1
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { CategoryIcon(category = category, modifier = Modifier.size(24.dp)) },
        trailingContent = if (category.UsedCount > 0) {
            {
                Text(
                    text = category.UsedCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null
    ) {
        Text(category.Name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CategoryGridItem(
    category: DataCategory,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryIcon(category = category, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.Name,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryIcon(category: DataCategory, modifier: Modifier = Modifier) {
    val emoji = remember(category.Name) {
        if (category.Name.length == 2) EmojiUtils.getFlagEmoji(category.Name) else null
    }

    if (emoji != null) {
        Text(text = emoji, modifier = modifier, textAlign = TextAlign.Center)
    } else {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
