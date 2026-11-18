package com.example.mnlists.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mnlists.R
import com.example.mnlists.ui.components.PlaceholderContent

/** Calendar tab. Month grid + day list arrives in Step 12. */
@Composable
fun CalendarScreen() {
    PlaceholderContent(
        icon = Icons.Filled.DateRange,
        title = stringResource(R.string.tab_calendar),
        description = stringResource(R.string.empty_calendar),
    )
}
