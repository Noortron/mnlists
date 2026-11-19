package com.example.mnlists.ui.dailies

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.example.mnlists.R
import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.local.entity.DailyOccurrence

/**
 * One row in the Dailies list: today's occurrence plus the series that owns it.
 * The occurrence carries completion/skip; the series carries title/note/schedule.
 */
data class DailyEntry(
    val occurrence: DailyOccurrence,
    val daily: Daily,
)

/** Gray + strikethrough as a span so semantics-based tests can observe it. */
internal fun String.decorated(completed: Boolean): AnnotatedString =
    if (completed) {
        AnnotatedString(
            text = this,
            spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        )
    } else {
        AnnotatedString(this)
    }

/** Today's date label; no label is rendered for other dates. */
@Composable
internal fun DailyOccurrenceDateLabel(
    date: Long,
    modifier: Modifier = Modifier,
    today: Long = DailyDates.today(),
) {
    val text = when (date) {
        today -> stringResource(R.string.daily_date_today)
        today - 1 -> stringResource(R.string.daily_date_yesterday)
        else -> stringResource(R.string.daily_date_on, DailyDates.format(date))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
