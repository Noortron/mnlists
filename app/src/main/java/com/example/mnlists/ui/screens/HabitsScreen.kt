package com.example.mnlists.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mnlists.R
import com.example.mnlists.ui.components.PlaceholderContent

/** Habits tab. Full list + counter UI arrives in Step 4. */
@Composable
fun HabitsScreen() {
    PlaceholderContent(
        icon = Icons.Filled.Favorite,
        title = stringResource(R.string.tab_habits),
        description = stringResource(R.string.empty_habits),
    )
}
