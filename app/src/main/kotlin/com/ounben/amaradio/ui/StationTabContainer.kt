package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun TabHeader(titleRes: Int) {
    SecondaryScrollableTabRow(
        selectedTabIndex = 0,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(0),
                color = MaterialTheme.colorScheme.primary
            )
        },
        divider = {}
    ) {
        Tab(
            selected = true,
            onClick = { },
            text = { Text(text = stringResource(id = titleRes)) }
        )
    }
}

@Composable
fun SingleTabContainer(
    titleRes: Int,
    content: @Composable () -> Unit
) {
    // The screen title now lives in the top app bar, so no tab-style header is drawn here.
    Column(modifier = Modifier.fillMaxSize()) {
        content()
    }
}
