package com.ounben.amaradio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R
import kotlinx.coroutines.launch

/** Actions offered by the overflow menu of the main top bars. */
data class MainMenuActions(
    val isGridView: Boolean,
    val onViewToggleClick: () -> Unit,
    val onSleepTimerClick: () -> Unit,
    val onSaveClick: () -> Unit,
    val onLoadClick: () -> Unit,
    val onDeleteClick: (() -> Unit)?
)

/** The search field shared by the collapsed app bar and the expanded search screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StationSearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    isLoading: Boolean
) {
    val scope = rememberCoroutineScope()
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = { },
        placeholder = { Text(stringResource(R.string.fork_search_hint)) },
        leadingIcon = {
            if (expanded) {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.accessibility_search_back))
                }
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null)
            }
        },
        trailingIcon = {
            when {
                isLoading && expanded -> Box(Modifier.size(24.dp)) { LoadingIndicator() }
                expanded && textFieldState.text.isNotEmpty() -> IconButton(onClick = { textFieldState.clearText() }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.accessibility_clear_text))
                }
                else -> Unit
            }
        }
    )
}

/** Top bar of the station tabs: a Material 3 search app bar plus cast, filter and menu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainSearchTopBar(
    searchBarState: SearchBarState,
    inputField: @Composable () -> Unit,
    showFilter: Boolean,
    onFilterClick: () -> Unit,
    menu: MainMenuActions
) {
    AppBarWithSearch(
        state = searchBarState,
        inputField = inputField,
        navigationIcon = {
            Image(
                painter = painterResource(R.drawable.ic_xiangyin_logo),
                contentDescription = stringResource(R.string.fork_app_name),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp).size(36.dp)
            )
        },
        actions = {
            CastButton()
            if (showFilter) {
                IconButton(onClick = onFilterClick) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.action_filter))
                }
            }
            MainOverflowMenu(menu)
        }
    )
}

/** Plain title bar used by Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTitleTopBar(title: String) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) }
    )
}

@Composable
private fun MainOverflowMenu(menu: MainMenuActions) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.accessibility_menu))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (menu.isGridView) R.string.action_list_view else R.string.action_grid_view)) },
                leadingIcon = {
                    Icon(if (menu.isGridView) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView, contentDescription = null)
                },
                onClick = { showMenu = false; menu.onViewToggleClick() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_item_add_sleep)) },
                leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                onClick = { showMenu = false; menu.onSleepTimerClick() }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_item_save_playlist)) },
                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                onClick = { showMenu = false; menu.onSaveClick() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_item_load_playlist)) },
                leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                onClick = { showMenu = false; menu.onLoadClick() }
            )
            menu.onDeleteClick?.let { onDelete ->
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
