package com.ounben.amaradio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.ounben.amaradio.R
import com.ounben.amaradio.history.TrackHistoryEntry
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.utils.EmojiUtils
import kotlinx.coroutines.flow.StateFlow

private val PlayState.isActive: Boolean
    get() = this == PlayState.Playing || this == PlayState.PrePlaying

/** "Song - Artist" style line from the stream metadata, or empty. */
private fun PlayerViewModel.PlayerUiState.nowPlayingText(): Pair<String, String> {
    val title = liveInfo.track.ifEmpty { liveInfo.title }
    val artist = if (liveInfo.track.isNotEmpty()) liveInfo.artist else ""
    return title to artist
}

/**
 * Floating now-playing card shown above the navigation bar. Tap or swipe up to open the
 * full player.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    isHeaderRole: Boolean = false,
    onToggleBottomSheet: () -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val station = uiState.currentStation ?: return
    val (songTitle, artist) = uiState.nowPlayingText()

    Surface(
        onClick = onToggleBottomSheet,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { },
                onDragStopped = { velocity -> if (velocity < -300f) onToggleBottomSheet() }
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StationIcon(
                    stationName = station.Name,
                    stationUuid = station.StationUuid,
                    iconUrl = station.IconUrl,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.Name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            uiState.playState == PlayState.Error -> stringResource(R.string.error_station_load)
                            uiState.playState == PlayState.PrePlaying -> stringResource(R.string.fork_player_connecting)
                            songTitle.isNotEmpty() -> listOf(songTitle, artist).filter { it.isNotEmpty() }.joinToString(" · ")
                            uiState.playState == PlayState.Playing -> stringResource(R.string.fork_player_live)
                            else -> stringResource(R.string.fork_player_paused)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.playState == PlayState.Error) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayPauseButton(
                    isActive = uiState.playState.isActive,
                    onToggle = { viewModel.togglePlayPause() },
                    size = PlayPauseSize.Medium
                )
            }
            if (uiState.playState == PlayState.PrePlaying) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp)
                )
            }
        }
    }
}

private enum class PlayPauseSize { Medium, ExtraLarge }

/** Shape-morphing play/pause toggle (round when paused, squircle when playing). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayPauseButton(isActive: Boolean, onToggle: () -> Unit, size: PlayPauseSize) {
    val containerSize = when (size) {
        PlayPauseSize.Medium -> IconButtonDefaults.mediumContainerSize()
        PlayPauseSize.ExtraLarge -> IconButtonDefaults.extraLargeContainerSize()
    }
    val iconSize = when (size) {
        PlayPauseSize.Medium -> IconButtonDefaults.mediumIconSize
        PlayPauseSize.ExtraLarge -> IconButtonDefaults.extraLargeIconSize
    }
    FilledIconToggleButton(
        checked = isActive,
        onCheckedChange = { onToggle() },
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier.size(containerSize)
    ) {
        Icon(
            imageVector = if (isActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = stringResource(if (isActive) R.string.fork_player_pause else R.string.fork_player_play),
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Expressive artwork: the station logo inside a 12-sided "cookie" that turns while playing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerArtwork(
    stationName: String,
    stationUuid: String,
    iconUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    val transition = rememberInfiniteTransition(label = "artwork")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "artwork-rotation"
    )
    var frozenAngle by remember { mutableStateOf(0f) }
    if (isPlaying) frozenAngle = angle

    // Bundled station artwork fills the cookie; the outline turns, the artwork stays upright.
    val isArtwork = iconUrl?.contains("/curated_logos/") == true
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = frozenAngle }
                .clip(cookie)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (isArtwork) {
                StationIcon(
                    stationName = stationName,
                    stationUuid = stationUuid,
                    iconUrl = iconUrl,
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = -frozenAngle },
                    shape = RectangleShape
                )
            }
        }
        if (!isArtwork) {
            StationIcon(
                stationName = stationName,
                stationUuid = stationUuid,
                iconUrl = iconUrl,
                modifier = Modifier.fillMaxSize(0.62f),
                shape = CircleShape
            )
        }
    }
}

@Composable
private fun LiveBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.fork_player_live), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Content of the full-screen player sheet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayer(
    playerViewModel: PlayerViewModel,
    trackHistoryViewModel: TrackHistoryViewModel,
    onTrackClick: (TrackHistoryEntry) -> Unit,
    onSleepTimerClick: () -> Unit = {}
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val tracks = trackHistoryViewModel.allHistoryPaged.collectAsLazyPagingItems()
    val context = LocalContext.current
    var trackWithOptions by remember { mutableStateOf<TrackHistoryEntry?>(null) }

    val station = uiState.currentStation
    val (songTitle, artist) = uiState.nowPlayingText()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item(key = "artwork") {
            val artworkSize = (LocalConfiguration.current.screenWidthDp * 0.7f).dp.coerceAtMost(320.dp)
            PlayerArtwork(
                stationName = station?.Name.orEmpty(),
                stationUuid = station?.StationUuid.orEmpty(),
                iconUrl = station?.IconUrl,
                isPlaying = uiState.playState == PlayState.Playing,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 24.dp)
                    .size(artworkSize)
            )
        }

        item(key = "titles") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = station?.Name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val secondary = when {
                    songTitle.isNotEmpty() -> listOf(songTitle, artist).filter { it.isNotEmpty() }.joinToString(" · ")
                    station != null -> {
                        val flag = EmojiUtils.getFlagEmoji(station.CountryCode).orEmpty()
                        listOf(flag, station.TagsAll.ifEmpty { station.getShortDetails(context) })
                            .filter { it.isNotBlank() }.joinToString(" ")
                    }
                    else -> ""
                }
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item(key = "status") {
            Box(modifier = Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (uiState.playState) {
                    PlayState.PrePlaying -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearWavyProgressIndicator(modifier = Modifier.width(160.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.fork_player_connecting),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PlayState.Playing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveBadge()
                        Spacer(Modifier.width(10.dp))
                        BandwidthText(playerViewModel.bandwidth)
                    }
                    PlayState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.error_station_load),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Text(
                        stringResource(R.string.fork_player_paused),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "controls") {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { playerViewModel.skipToPrevious() },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize())
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.fork_player_previous),
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                    )
                }
                PlayPauseButton(
                    isActive = uiState.playState.isActive,
                    onToggle = { playerViewModel.togglePlayPause() },
                    size = PlayPauseSize.ExtraLarge
                )
                FilledTonalIconButton(
                    onClick = { playerViewModel.skipToNext() },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize())
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.fork_player_next),
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                    )
                }
            }
        }

        item(key = "secondary-actions") {
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconToggleButton(
                    checked = uiState.isFavorite,
                    onCheckedChange = { playerViewModel.toggleFavorite() },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        checkedContentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.fork_player_favorite)
                    )
                }
                IconButton(onClick = onSleepTimerClick) {
                    Icon(Icons.Rounded.Timer, contentDescription = stringResource(R.string.fork_player_sleep_timer))
                }
            }
        }

        if (tracks.itemCount > 0) {
            item(key = "history-title") {
                Text(
                    text = stringResource(R.string.tab_player_history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            items(
                count = tracks.itemCount,
                key = { index -> tracks.peek(index)?.let { "track_${it.uid}_$index" } ?: "track_placeholder_$index" }
            ) { index ->
                tracks[index]?.let { track ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TrackListItem(
                            track = track,
                            onClick = { onTrackClick(track) },
                            onLongClick = { trackWithOptions = track },
                            index = index,
                            count = tracks.itemCount
                        )
                    }
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(32.dp)) }
    }

    trackWithOptions?.let { track ->
        TrackOptionsDialog(
            track = track,
            onDismiss = { trackWithOptions = null }
        )
    }
}

@Composable
private fun BandwidthText(bandwidthFlow: StateFlow<String>) {
    val bandwidth by bandwidthFlow.collectAsState()
    if (bandwidth.isNotEmpty()) {
        Text(
            text = bandwidth,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
