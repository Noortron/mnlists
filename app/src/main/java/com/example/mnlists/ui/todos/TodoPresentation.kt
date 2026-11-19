package com.example.mnlists.ui.todos

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.example.mnlists.R

/**
 * Shared presentation helpers for To-Do rows and subtask rows.
 *
 * The strike-through is applied as a span style (not just a colour change) so
 * assistive tech and semantics-based tests can observe it.
 */
internal fun String.decorated(completed: Boolean): AnnotatedString =
    if (completed) {
        AnnotatedString(
            text = this,
            spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        )
    } else {
        AnnotatedString(this)
    }

/**
 * Due/deadline label. A `null` date renders nothing at all, so unplanned tasks
 * (and subtasks without a deadline) show no date and can never look overdue.
 */
@Composable
internal fun TodoDateLabel(
    dueDate: Long?,
    completed: Boolean,
    modifier: Modifier = Modifier,
    today: Long = TodoDates.today(),
) {
    if (dueDate == null) return

    val text = when {
        dueDate < today -> stringResource(R.string.todo_overdue_on, TodoDates.format(dueDate))
        dueDate == today -> stringResource(R.string.todo_due_today_label)
        dueDate == today + 1 -> stringResource(R.string.todo_due_tomorrow_label)
        else -> stringResource(R.string.todo_due_on, TodoDates.format(dueDate))
    }
    val color = when {
        completed -> MaterialTheme.colorScheme.onSurfaceVariant
        dueDate < today -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}
