package com.ounben.amaradio.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.fork.curated.CuratedSources
import com.ounben.amaradio.fork.curated.CuratedViewModel
import com.ounben.amaradio.fork.ui.CountryScreen
import com.ounben.amaradio.fork.ui.CuratedPlaylistScreen
import com.ounben.amaradio.fork.ui.WorldScreen
import com.ounben.amaradio.fork.ui.countrySearchResults
import com.ounben.amaradio.fork.ui.rememberArtworkColorScheme
import com.ounben.amaradio.fork.world.Countries
import com.ounben.amaradio.fork.world.CountryViewModel
import com.ounben.amaradio.fork.world.WorldViewModel
import com.ounben.amaradio.history.TrackHistoryViewModel
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    object World : Screen("world", R.string.fork_nav_world, Icons.Outlined.Public, Icons.Filled.Public)
    object Country : Screen("country/{code}", R.string.fork_nav_world, Icons.Outlined.Public) {
        fun routeFor(code: String) = "country/$code"
    }
    /** Upstream's station tabs (device country + custom filters), opened from the World top bar. */
    object Stations : Screen("stations", R.string.fork_advanced_filters, Icons.Rounded.Tune)
    object Favourites : Screen("starred", R.string.nav_item_starred, Icons.Outlined.StarOutline, Icons.Filled.Star)
    object History : Screen("history", R.string.nav_item_history, Icons.Outlined.History, Icons.Filled.History)
    object Settings : Screen("settings", R.string.nav_item_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
    object About : Screen("about", R.string.settings_about, Icons.Rounded.Info)
    object Statistics : Screen("statistics", R.string.settings_statistics, Icons.Rounded.BarChart)
    object Playlist : Screen("playlist/{id}", R.string.fork_curated_more, Icons.Rounded.Info) {
        fun routeFor(id: String) = "playlist/$id"
    }
}

private val topLevelScreens = listOf(Screen.World, Screen.Favourites, Screen.History, Screen.Settings)

/** Routes shown under the World tab. */
private val worldRoutes = setOf(Screen.World.route, Screen.Country.route, Screen.Playlist.route, Screen.Stations.route)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onSaveM3U: () -> Unit,
    onLoadM3U: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as AMARadioApp
    val scope = rememberCoroutineScope()

    val mainViewModel: MainViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()
    val trackHistoryViewModel: TrackHistoryViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val serverInfoViewModel: ServerInfoViewModel = viewModel()
    val curatedViewModel: CuratedViewModel = viewModel()
    val worldViewModel: WorldViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val mainUiState by mainViewModel.uiState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val curatedSources by curatedViewModel.sources.collectAsState()
    val worldState by worldViewModel.uiState.collectAsState()
    val playerWarning by playerViewModel.warningMessage.collectAsState()

    // Country and curated station names follow the interface language.
    val uiLocale = LocalConfiguration.current.locales[0]
    LaunchedEffect(uiLocale) {
        curatedViewModel.setLocale(uiLocale)
        worldViewModel.setLocale(uiLocale)
    }

    LaunchedEffect(playerWarning) {
        playerWarning?.let { msgRes ->
            android.widget.Toast.makeText(context, msgRes, android.widget.Toast.LENGTH_LONG).show()
            playerViewModel.clearWarning()
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showPlayerSelectorDialog by remember { mutableStateOf<DataRadioStation?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Int?>(null) }
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Startup destination (settings: world / history / favorites / last view). "home" is the
    // stored value of the World option; it named the curated Home tab in 1.40-bz1.
    val startRoute = remember(settingsUiState.startupAction) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val lastRoute = sharedPref.getString("last_view_route", Screen.World.route) ?: Screen.World.route
        when (settingsUiState.startupAction) {
            "history" -> Screen.History.route
            "favorites" -> Screen.Favourites.route
            "home", "stations" -> Screen.World.route
            else -> lastRoute.takeIf { route -> topLevelScreens.any { it.route == route } } ?: Screen.World.route
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && topLevelScreens.any { it.route == currentRoute } && currentRoute != Screen.Settings.route) {
            PreferenceManager.getDefaultSharedPreferences(context).edit { putString("last_view_route", currentRoute) }
        }
    }

    val playStation: (DataRadioStation) -> Unit = { station ->
        if (sharedPrefHasExternalPlayer(context)) showPlayerSelectorDialog = station
        else playerViewModel.play(station)
    }
    val toggleFavourite: (DataRadioStation) -> Unit = { station ->
        if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
        else app.favouriteManager.add(station)
    }
    val isFavourite: (String) -> Boolean = { uuid -> mainUiState.favoriteIds.contains(uuid) }

    // Search: one text field drives both the collapsed app bar and the full-screen results.
    val searchBarState = rememberSearchBarState()
    val searchFieldState = rememberTextFieldState()
    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collect { query ->
            mainViewModel.setSearchQuery(query)
            searchViewModel.search(query)
        }
    }
    val searchQuery = mainUiState.searchQuery.trim()
    val curatedMatches = remember(searchQuery, curatedSources) {
        if (searchQuery.isEmpty()) emptyList()
        else curatedSources.values
            .flatMap { it.playlist?.stations.orEmpty() }
            .filter { it.Name.contains(searchQuery, ignoreCase = true) }
            .distinctBy { it.StreamUrl }
    }
    val searchResults = remember(curatedMatches, searchUiState.results) {
        (curatedMatches + searchUiState.results).distinctBy { it.StationUuid }
    }
    val countryMatches = remember(searchQuery, worldState) { worldState.search(searchQuery).take(MAX_COUNTRY_MATCHES) }
    val openCountry: (String) -> Unit = { code ->
        if (code != Countries.WORLD) worldViewModel.markVisited(code)
        navController.navigate(Screen.Country.routeFor(code))
    }
    val searchInputField: @Composable () -> Unit = {
        StationSearchInputField(
            textFieldState = searchFieldState,
            searchBarState = searchBarState,
            isLoading = searchUiState.isSearching
        )
    }

    val isTopLevel = topLevelScreens.any { it.route == currentRoute }
    val deleteTarget = when (currentRoute) {
        Screen.Favourites.route -> R.string.alert_delete_favorites
        Screen.History.route -> R.string.alert_delete_history
        else -> null
    }
    val menuActions = MainMenuActions(
        isGridView = mainUiState.isGridView,
        onViewToggleClick = { mainViewModel.toggleGridView() },
        onSleepTimerClick = { showSleepTimerDialog = true },
        onSaveClick = onSaveM3U,
        onLoadClick = onLoadM3U,
        onDeleteClick = deleteTarget?.let { target -> { showDeleteConfirmDialog = target } }
    )

    // The player surfaces take their colors from the current station's artwork.
    val artworkScheme = rememberArtworkColorScheme(playerUiState.currentStation?.IconUrl)
    val playerColorScheme = artworkScheme ?: MaterialTheme.colorScheme

    // Lists mark the current station only while it plays (or is connecting), not when paused.
    val playingUuid = playerUiState.currentStation?.StationUuid
        ?.takeIf { playerUiState.playState == PlayState.Playing || playerUiState.playState == PlayState.PrePlaying }
    CompositionLocalProvider(LocalPlayingStationUuid provides playingUuid) {
        Scaffold(
            topBar = {
                when {
                    currentRoute == Screen.Settings.route -> MainTitleTopBar(stringResource(R.string.nav_item_settings))
                    isTopLevel || currentRoute == null -> MainSearchTopBar(
                        searchBarState = searchBarState,
                        inputField = searchInputField,
                        showFilter = currentRoute == Screen.World.route,
                        onFilterClick = {
                            mainViewModel.setStationsInitialTab(1)
                            navController.navigate(Screen.Stations.route)
                        },
                        menu = menuActions
                    )
                    else -> Unit // detail screens draw their own app bar
                }
            },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = playerUiState.currentStation != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        MaterialTheme(colorScheme = playerColorScheme) {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                onToggleBottomSheet = { showFullPlayer = true }
                            )
                        }
                    }
                    ShortNavigationBar {
                        topLevelScreens.forEach { screen ->
                            val selected = when (screen) {
                                Screen.Settings -> currentRoute == Screen.Settings.route ||
                                    currentRoute == Screen.About.route || currentRoute == Screen.Statistics.route
                                Screen.World -> currentRoute in worldRoutes
                                else -> currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            }
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = {
                                    // Re-selecting World from a country page goes back to the list.
                                    val backToWorld = selected && screen == Screen.World && currentRoute != Screen.World.route &&
                                        navController.popBackStack(Screen.World.route, inclusive = false)
                                    if (!backToWorld) navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) screen.selectedIcon else screen.icon, contentDescription = null) },
                                label = { Text(stringResource(screen.titleRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.World.route) {
                        WorldScreen(viewModel = worldViewModel, onOpenCountry = openCountry)
                    }
                    composable(Screen.Country.route) {
                        val countryViewModel: CountryViewModel = viewModel()
                        CountryScreen(
                            viewModel = countryViewModel,
                            country = worldState.country(countryViewModel.code),
                            curatedViewModel = curatedViewModel,
                            onStationClick = playStation,
                            isFavorite = isFavourite,
                            onToggleFavorite = toggleFavourite,
                            onOpenPlaylist = { source -> navController.navigate(Screen.Playlist.routeFor(source.id)) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Playlist.route) { entry ->
                        val source = CuratedSources.byId(entry.arguments?.getString("id").orEmpty())
                        if (source != null) {
                            CuratedPlaylistScreen(
                                viewModel = curatedViewModel,
                                source = source,
                                onStationClick = playStation,
                                isFavorite = isFavourite,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable(Screen.Stations.route) {
                        AdvancedFiltersScreen(onBack = { navController.popBackStack() }) {
                            TabsScreen(
                                initialTab = mainUiState.stationsInitialTab,
                                onStationClick = playStation,
                                onCategoryClick = { }
                            )
                        }
                    }
                    composable(Screen.Favourites.route) {
                        val favViewModel: LocalStationsViewModel = viewModel(
                            key = "starred",
                            factory = LocalStationsViewModelFactory(app, LocalManagerType.FAVOURITES)
                        )
                        StarredScreen(
                            viewModel = favViewModel,
                            onStationClick = playStation,
                            onFavoriteClick = toggleFavourite,
                            isFavorite = isFavourite
                        )
                    }
                    composable(Screen.History.route) {
                        val historyViewModel: LocalStationsViewModel = viewModel(
                            key = "history",
                            factory = LocalStationsViewModelFactory(app, LocalManagerType.HISTORY)
                        )
                        HistoryScreen(
                            localStationsViewModel = historyViewModel,
                            trackHistoryViewModel = trackHistoryViewModel,
                            onStationClick = playStation,
                            onFavoriteClick = toggleFavourite,
                            onTrackClick = { },
                            isFavorite = isFavourite
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            serverInfoViewModel = serverInfoViewModel,
                            onOpenProxy = { showProxyDialog = true },
                            onOpenAbout = { navController.navigate(Screen.About.route) },
                            onOpenStatistics = { navController.navigate(Screen.Statistics.route) },
                            onOpenEqualizer = {
                                val intent = Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")
                                intent.putExtra("android.media.extra.PACKAGE_NAME", context.packageName)
                                intent.putExtra("android.media.extra.AUDIO_SESSION", 0)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            },
                            onBatteryOptimize = {
                                val intent = Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            },
                            onRateApp = {
                                (context as? android.app.Activity)?.let { app.reviewManager.launchReviewFlow(it) }
                            },
                            batterySummary = ""
                        )
                    }
                    composable(Screen.About.route) { AboutScreen { navController.popBackStack() } }
                    composable(Screen.Statistics.route) {
                        ServerInfoScreen(
                            viewModel = serverInfoViewModel,
                            onStationClick = playStation,
                            onFavoriteClick = toggleFavourite,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        ExpandedFullScreenSearchBar(state = searchBarState, inputField = searchInputField) {
            val hasMatches = countryMatches.isNotEmpty() || searchResults.isNotEmpty()
            when {
                searchQuery.length < 2 && !hasMatches -> Unit
                !hasMatches && searchUiState.isSearching -> CenteredLoadingIndicator()
                !hasMatches -> ListMessage(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.searchpreference_no_results)
                )
                else -> StationList(
                    stations = searchResults,
                    isGrid = false,
                    onStationClick = { station ->
                        playStation(station)
                        scope.launch { searchBarState.animateToCollapsed() }
                    },
                    onFavoriteClick = toggleFavourite,
                    isFavorite = { uuid -> searchUiState.favoriteIds.contains(uuid) || mainUiState.favoriteIds.contains(uuid) },
                    header = {
                        countrySearchResults(countryMatches, hasStations = searchResults.isNotEmpty()) { country ->
                            scope.launch { searchBarState.animateToCollapsed() }
                            searchFieldState.clearText()
                            openCountry(country.code)
                        }
                    }
                )
            }
        }

        if (showFullPlayer && playerUiState.currentStation != null) MaterialTheme(colorScheme = playerColorScheme) {
            ModalBottomSheet(
                onDismissRequest = { showFullPlayer = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                FullPlayer(
                    playerViewModel = playerViewModel,
                    trackHistoryViewModel = trackHistoryViewModel,
                    onTrackClick = { },
                    onSleepTimerClick = { showSleepTimerDialog = true }
                )
            }
        }
    }

    if (showSleepTimerDialog) SleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    if (showProxyDialog) ProxySettingsDialogCompose(onDismiss = { showProxyDialog = false })
    showPlayerSelectorDialog?.let { station ->
        PlayerSelectorDialogCompose(
            station = station,
            playerViewModel = playerViewModel,
            onDismiss = { showPlayerSelectorDialog = null }
        )
    }
    showDeleteConfirmDialog?.let { msgRes ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(msgRes)) },
            confirmButton = {
                TextButton(onClick = {
                    if (msgRes == R.string.alert_delete_favorites) app.favouriteManager.clear()
                    else app.historyManager.clear()
                    showDeleteConfirmDialog = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) { Text(stringResource(R.string.no)) }
            }
        )
    }
}

private const val MAX_COUNTRY_MATCHES = 6

/** Upstream's station tabs with a back button; they are no longer a top-level destination. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFiltersScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            // The Scaffold already applies the status bar inset.
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.fork_advanced_filters)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                }
            }
        )
        content()
    }
}

private fun sharedPrefHasExternalPlayer(context: Context): Boolean {
    return try {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("play_external", false)
    } catch (_: Exception) {
        false
    }
}
