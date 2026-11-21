package com.example.mnlists.ui.dailies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mnlists.R
import com.example.mnlists.data.local.entity.Subtask
import com.example.mnlists.ui.components.PlaceholderContent
import com.example.mnlists.ui.components.ReorderHandle
import com.example.mnlists.ui.components.rememberReorderDragState
import com.example.mnlists.ui.components.reorderHandle
import com.example.mnlists.ui.components.reorderItem

/** Stateful entry point for the Dailies tab. */
@Composable
fun DailiesRoute(
    onOpenSeries: (Long) -> Unit,
    viewModel: DailiesViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val today by viewModel.selectedDay.collectAsStateWithLifecycle()

    // Tab-entry generation (SPEC §8): a long-running app crossing midnight
    // still gets the new day. MainActivity covers the on-open case.
    LaunchedEffect(Unit) {
        viewModel.generateTodayOccurrences()
    }

    DailiesScreen(
        entries = entries,
        subtasksByOccurrence = subtasks,
        today = today,
        onOpenSeries = onOpenSeries,
        onToggleCompleted = viewModel::setCompleted,
        onToggleSkipped = viewModel::setSkipped,
        onToggleSubtask = viewModel::setSubtaskCompleted,
        onAddSubtask = { id, title, note, deadline ->
            viewModel.addSubtask(id, title, note, deadline) {}
        },
        onUpdateSubtask = { occurrenceId, subtaskId, title, note, deadline ->
            viewModel.updateSubtask(subtaskId, title, note, deadline) {}
        },
        onDeleteSubtask = viewModel::deleteSubtask,
        onReorder = viewModel::moveOccurrence,
    )
}

/**
 * Dailies list (SPEC §4.3/§4.6). Today's occurrences are split into open,
 * Skipped and Completed sections. Tapping a title is now the single entry
 * point to the Daily editor (including its subtasks); the old "Edit this day"
 * row button is gone. Skipped rows are only moved, never dimmed or struck.
 */
@Composable
fun DailiesScreen(
    entries: List<DailyEntry>,
    subtasksByOccurrence: Map<Long, List<Subtask>>,
    today: Long,
    onOpenSeries: (Long) -> Unit,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onToggleSkipped: (Long, Boolean) -> Unit,
    onToggleSubtask: (Long, Boolean) -> Unit,
    onAddSubtask: (Long, String, String, Long?) -> Unit,
    onUpdateSubtask: (Long, Long, String, String, Long?) -> Unit,
    onDeleteSubtask: (Long) -> Unit,
    onReorder: (fromId: Long, toId: Long) -> Unit = { _, _ -> },
) {
    if (entries.isEmpty()) {
        PlaceholderContent(
            icon = Icons.Filled.Refresh,
            title = stringResource(R.string.tab_dailies),
            description = stringResource(R.string.empty_dailies),
        )
        return
    }

    val open = entries.filter { !it.occurrence.isCompleted && !it.occurrence.isSkipped }
    val skipped = entries.filter { it.occurrence.isSkipped && !it.occurrence.isCompleted }
    val completed = entries.filter { it.occurrence.isCompleted }
    val openIds = remember(open) { open.map { it.occurrence.id } }
    val skippedIds = remember(skipped) { skipped.map { it.occurrence.id } }
    val completedIds = remember(completed) { completed.map { it.occurrence.id } }

    val listState = rememberLazyListState()
    val dragState = rememberReorderDragState(listState)

    // Subtask details are expanded by default (SPEC §4.2 row behaviour).
    var collapsedIds by remember { mutableStateOf(setOf<Long>()) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (open.isNotEmpty()) {
            item(key = "open-header") { SectionHeader(stringResource(R.string.todo_section_open)) }
            items(items = open, key = { it.occurrence.id }) { entry ->
                DailyRow(
                    entry = entry,
                    today = today,
                    subtasks = subtasksByOccurrence[entry.occurrence.id].orEmpty(),
                    collapsed = entry.occurrence.id in collapsedIds,
                    onToggleCollapsed = {
                        collapsedIds = if (entry.occurrence.id in collapsedIds) {
                            collapsedIds - entry.occurrence.id
                        } else {
                            collapsedIds + entry.occurrence.id
                        }
                    },
                    onOpenSeries = { onOpenSeries(entry.daily.id) },
                    onToggleCompleted = { onToggleCompleted(entry.occurrence.id, it) },
                    onToggleSkipped = { onToggleSkipped(entry.occurrence.id, it) },
                    onToggleSubtask = onToggleSubtask,
                    onAddSubtask = { title, note, deadline ->
                        onAddSubtask(entry.occurrence.id, title, note, deadline)
                    },
                    onUpdateSubtask = { subtaskId, title, note, deadline ->
                        onUpdateSubtask(entry.occurrence.id, subtaskId, title, note, deadline)
                    },
                    onDeleteSubtask = onDeleteSubtask,
                    dragModifier = Modifier.reorderHandle(
                        state = dragState,
                        key = entry.occurrence.id,
                        sectionKeys = when {
                            entry.occurrence.isCompleted -> completedIds
                            entry.occurrence.isSkipped -> skippedIds
                            else -> openIds
                        },
                    ) { from, to ->
                        val ids = when {
                            entry.occurrence.isCompleted -> completedIds
                            entry.occurrence.isSkipped -> skippedIds
                            else -> openIds
                        }
                        onReorder(ids[from], ids[to])
                    },
                    modifier = Modifier
                        .animateItem()
                        .reorderItem(dragState, entry.occurrence.id),
                )
            }
        }

        if (skipped.isNotEmpty()) {
            item(key = "skipped-header") {
                SectionHeader(stringResource(R.string.daily_section_skipped))
            }
            items(items = skipped, key = { it.occurrence.id }) { entry ->
                DailyRow(
                    entry = entry,
                    today = today,
                    subtasks = subtasksByOccurrence[entry.occurrence.id].orEmpty(),
                    collapsed = entry.occurrence.id in collapsedIds,
                    onToggleCollapsed = {
                        collapsedIds = if (entry.occurrence.id in collapsedIds) {
                            collapsedIds - entry.occurrence.id
                        } else {
                            collapsedIds + entry.occurrence.id
                        }
                    },
                    onOpenSeries = { onOpenSeries(entry.daily.id) },
                    onToggleCompleted = { onToggleCompleted(entry.occurrence.id, it) },
                    onToggleSkipped = { onToggleSkipped(entry.occurrence.id, it) },
                    onToggleSubtask = onToggleSubtask,
                    onAddSubtask = { title, note, deadline ->
                        onAddSubtask(entry.occurrence.id, title, note, deadline)
                    },
                    onUpdateSubtask = { subtaskId, title, note, deadline ->
                        onUpdateSubtask(entry.occurrence.id, subtaskId, title, note, deadline)
                    },
                    onDeleteSubtask = onDeleteSubtask,
                    dragModifier = Modifier.reorderHandle(
                        state = dragState,
                        key = entry.occurrence.id,
                        sectionKeys = when {
                            entry.occurrence.isCompleted -> completedIds
                            entry.occurrence.isSkipped -> skippedIds
                            else -> openIds
                        },
                    ) { from, to ->
                        val ids = when {
                            entry.occurrence.isCompleted -> completedIds
                            entry.occurrence.isSkipped -> skippedIds
                            else -> openIds
                        }
                        onReorder(ids[from], ids[to])
                    },
                    modifier = Modifier
                        .animateItem()
                        .reorderItem(dragState, entry.occurrence.id),
                )
            }
        }

        if (completed.isNotEmpty()) {
            item(key = "completed-header") {
                SectionHeader(stringResource(R.string.todo_section_completed))
            }
            items(items = completed, key = { it.occurrence.id }) { entry ->
                DailyRow(
                    entry = entry,
                    today = today,
                    subtasks = subtasksByOccurrence[entry.occurrence.id].orEmpty(),
                    collapsed = entry.occurrence.id in collapsedIds,
                    onToggleCollapsed = {
                        collapsedIds = if (entry.occurrence.id in collapsedIds) {
                            collapsedIds - entry.occurrence.id
                        } else {
                            collapsedIds + entry.occurrence.id
                        }
                    },
                    onOpenSeries = { onOpenSeries(entry.daily.id) },
                    onToggleCompleted = { onToggleCompleted(entry.occurrence.id, it) },
                    onToggleSkipped = { onToggleSkipped(entry.occurrence.id, it) },
                    onToggleSubtask = onToggleSubtask,
                    onAddSubtask = { title, note, deadline ->
                        onAddSubtask(entry.occurrence.id, title, note, deadline)
                    },
                    onUpdateSubtask = { subtaskId, title, note, deadline ->
                        onUpdateSubtask(entry.occurrence.id, subtaskId, title, note, deadline)
                    },
                    onDeleteSubtask = onDeleteSubtask,
                    dragModifier = Modifier.reorderHandle(
                        state = dragState,
                        key = entry.occurrence.id,
                        sectionKeys = when {
                            entry.occurrence.isCompleted -> completedIds
                            entry.occurrence.isSkipped -> skippedIds
                            else -> openIds
                        },
                    ) { from, to ->
                        val ids = when {
                            entry.occurrence.isCompleted -> completedIds
                            entry.occurrence.isSkipped -> skippedIds
                            else -> openIds
                        }
                        onReorder(ids[from], ids[to])
                    },
                    modifier = Modifier
                        .animateItem()
                        .reorderItem(dragState, entry.occurrence.id),
                )
            }
        }

        // Keeps the last row's inline "Add subtask" form reachable above the FAB.
        // 56dp FAB + ~16dp clearance + 16dp content padding.
        item(key = "fab-clearance") {
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** Compact » badge that marks a skipped occurrence at a glance. */
@Composable
private fun SkippedBadge(description: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(24.dp)
            .testTag(DAILY_SKIPPED_BADGE_TAG)
            // mergeDescendants keeps this badge a standalone node in the merged
            // semantics tree; otherwise the parent row's clickable merges it away.
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "»",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun DailyRow(
    entry: DailyEntry,
    today: Long,
    subtasks: List<Subtask>,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onOpenSeries: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onToggleSkipped: (Boolean) -> Unit,
    onToggleSubtask: (Long, Boolean) -> Unit,
    onAddSubtask: (String, String, Long?) -> Unit,
    onUpdateSubtask: (Long, String, String, Long?) -> Unit,
    onDeleteSubtask: (Long) -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val occurrence = entry.occurrence
    val completed = occurrence.isCompleted
    val skipped = occurrence.isSkipped
    val title = entry.daily.title
    val checkboxDescription = stringResource(
        if (completed) R.string.todo_mark_not_done else R.string.todo_mark_done,
        title,
    )
    val skipDescription = stringResource(
        if (skipped) R.string.daily_unskip_description else R.string.daily_skip_description,
        title,
    )
    val toggleDescription = stringResource(
        if (collapsed) R.string.todo_subtask_expand else R.string.todo_subtask_collapse,
        title,
    )
    // Completed rows are gray + struck through; skipped rows keep their title
    // (no strikethrough) but get a tertiary tint and a » badge so the two
    // bottom sections are easy to tell apart (Step 14).
    val contentColor = when {
        completed -> MaterialTheme.colorScheme.onSurfaceVariant
        skipped -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 5.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = completed,
                    onCheckedChange = onToggleCompleted,
                    modifier = Modifier.semantics { contentDescription = checkboxDescription },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onOpenSeries)
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (skipped && !completed) {
                            SkippedBadge(
                                description = stringResource(R.string.daily_skipped_badge),
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Text(
                            text = title.decorated(completed),
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (entry.daily.note.isNotBlank()) {
                        Text(
                            text = entry.daily.note.decorated(completed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    DailyOccurrenceDateLabel(
                        date = occurrence.date,
                        today = today,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                ReorderHandle(
                    description = toggleDescription,
                    onTap = onToggleCollapsed,
                    modifier = dragModifier,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onToggleSkipped(!skipped) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = skipDescription },
                ) {
                    Text(stringResource(if (skipped) R.string.daily_unskip else R.string.daily_skip))
                }
            }

            if (!collapsed) {
                Column(modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)) {
                    Text(
                        text = stringResource(R.string.todo_subtask_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    DailySubtaskSection(
                        subtasks = subtasks,
                        onToggleCompleted = onToggleSubtask,
                        onAdd = onAddSubtask,
                        onUpdate = onUpdateSubtask,
                        onDelete = onDeleteSubtask,
                    )
                }
            }
        }
    }
}

/** Test tag for the skipped-row » badge. */
internal const val DAILY_SKIPPED_BADGE_TAG = "dailySkippedBadge"
