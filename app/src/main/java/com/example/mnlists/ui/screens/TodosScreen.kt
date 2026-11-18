package com.example.mnlists.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mnlists.R
import com.example.mnlists.ui.components.PlaceholderContent

/** To-Do's tab. Full list + CRUD UI arrives in Step 6. */
@Composable
fun TodosScreen() {
    PlaceholderContent(
        icon = Icons.Filled.CheckCircle,
        title = stringResource(R.string.tab_todos),
        description = stringResource(R.string.empty_todos),
    )
}
