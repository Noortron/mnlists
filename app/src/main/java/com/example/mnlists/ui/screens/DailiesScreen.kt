package com.example.mnlists.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mnlists.R
import com.example.mnlists.ui.components.PlaceholderContent

/** Dailies tab. Full list + occurrence UI arrives in Step 9. */
@Composable
fun DailiesScreen() {
    PlaceholderContent(
        icon = Icons.Filled.Refresh,
        title = stringResource(R.string.tab_dailies),
        description = stringResource(R.string.empty_dailies),
    )
}
