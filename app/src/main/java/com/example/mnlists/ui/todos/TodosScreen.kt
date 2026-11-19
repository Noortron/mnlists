package com.example.mnlists.ui.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.mnlists.data.local.entity.Todo
import com.example.mnlists.ui.components.PlaceholderContent
import com.example.mnlists.ui.components.ReorderHandle
import com.example.mnlists.ui.components.rememberReorderDragState
import com.example.mnlists.ui.components.reorderHandle
import com.example.mnlists.ui.components.reorderItem

/** Stateful entry point for the To-Do's tab. */
@Composable
fun TodosRoute(
    onOpenTodo: (Long) -> Unit,
    viewModel: TodosViewModel = hiltViewModel(),
) {
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()

    TodosScreen(
        todos = todos,
        subtasksByTodo = subtasks,
        onOpenTodo = onOpenTodo,
        onToggleCompleted = viewModel::setCompleted,
        onToggleSubtask = viewModel::setSubtaskCompleted,
        onReorder = viewModel::moveTodo,
    )
}

/**
 * To-Do list (SPEC §4.2/§4.6). Rows are split into an open section and a
 * "Completed" section at the bottom; completed titles are muted and struck
 * through. Each row's drag handle collapses its subtasks on tap and reorders
 * the list on long-press drag (Step 10).
 */
@Composable
fun TodosScreen(
    todos: List<Todo>,
    subtasksByTodo: Map<Long, List<Subtask>> = emptyMap(),
    onOpenTodo: (Long) -> Unit,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onToggleSubtask: (Long, Boolean) -> Unit = { _, _ -> },
    onReorder: (fromId: Long, toId: Long) -> Unit = { _, _ -> },
) {
    if (todos.isEmpty()) {
        PlaceholderContent(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.tab_todos),
            description = stringResource(R.string.empty_todos),
        )
        return
    }

    val open = todos.filterNot { it.isCompleted }
    val completed = todos.filter { it.isCompleted }
    val openIds = remember(open) { open.map { it.id } }
    val completedIds = remember(completed) { completed.map { it.id } }

    val listState = rememberLazyListState()
    val dragState = rememberReorderDragState(listState)
    var collapsedIds by remember { mutableStateOf(setOf<Long>()) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (open.isNotEmpty()) {
            item(key = "open-header") {
                SectionHeader(stringResource(R.string.todo_section_open))
            }
            items(items = open, key = { it.id }) { todo ->
                TodoRow(
                    todo = todo,
                    subtasks = subtasksByTodo[todo.id].orEmpty(),
                    collapsed = todo.id in collapsedIds,
                    onOpen = { onOpenTodo(todo.id) },
                    onToggleCompleted = { completedNow -> onToggleCompleted(todo.id, completedNow) },
                    onToggleCollapsed = {
                        collapsedIds = if (todo.id in collapsedIds) {
                            collapsedIds - todo.id
                        } else {
                            collapsedIds + todo.id
                        }
                    },
                    onToggleSubtask = onToggleSubtask,
                    dragModifier = Modifier.reorderHandle(
                        state = dragState,
                        key = todo.id,
                        sectionKeys = openIds,
                    ) { from, to -> onReorder(openIds[from], openIds[to]) },
                    modifier = Modifier
                        .animateItem()
                        .reorderItem(dragState, todo.id),
                )
            }
        }

        if (completed.isNotEmpty()) {
            item(key = "completed-header") {
                SectionHeader(stringResource(R.string.todo_section_completed))
            }
            items(items = completed, key = { it.id }) { todo ->
                TodoRow(
                    todo = todo,
                    subtasks = subtasksByTodo[todo.id].orEmpty(),
                    collapsed = todo.id in collapsedIds,
                    onOpen = { onOpenTodo(todo.id) },
                    onToggleCompleted = { completedNow -> onToggleCompleted(todo.id, completedNow) },
                    onToggleCollapsed = {
                        collapsedIds = if (todo.id in collapsedIds) {
                            collapsedIds - todo.id
                        } else {
                            collapsedIds + todo.id
                        }
                    },
                    onToggleSubtask = onToggleSubtask,
                    dragModifier = Modifier.reorderHandle(
                        state = dragState,
                        key = todo.id,
                        sectionKeys = completedIds,
                    ) { from, to -> onReorder(completedIds[from], completedIds[to]) },
                    modifier = Modifier
                        .animateItem()
                        .reorderItem(dragState, todo.id),
                )
            }
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

@Composable
private fun TodoRow(
    todo: Todo,
    subtasks: List<Subtask>,
    collapsed: Boolean,
    onOpen: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onToggleCollapsed: () -> Unit,
    onToggleSubtask: (Long, Boolean) -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val completed = todo.isCompleted
    val titleText = todo.title.decorated(completed)
    val checkboxDescription = stringResource(
        if (completed) R.string.todo_mark_not_done else R.string.todo_mark_done,
        todo.title,
    )
    val toggleDescription = stringResource(
        if (collapsed) R.string.todo_subtask_expand else R.string.todo_subtask_collapse,
        todo.title,
    )
    val contentColor = if (completed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = completed,
                    onCheckedChange = onToggleCompleted,
                    modifier = Modifier.semantics {
                        contentDescription = checkboxDescription
                    },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (todo.note.isNotBlank()) {
                        Text(
                            text = todo.note.decorated(completed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    TodoDateLabel(
                        dueDate = todo.dueDate,
                        completed = completed,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                ReorderHandle(
                    description = toggleDescription,
                    onTap = onToggleCollapsed,
                    modifier = dragModifier,
                )
            }

            if (!collapsed && subtasks.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 44.dp, end = 8.dp, bottom = 4.dp)) {
                    subtasks.forEach { subtask ->
                        TodoSubtaskRow(
                            subtask = subtask,
                            onToggleCompleted = { checked -> onToggleSubtask(subtask.id, checked) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoSubtaskRow(
    subtask: Subtask,
    onToggleCompleted: (Boolean) -> Unit,
) {
    val completed = subtask.isCompleted
    val description = stringResource(
        if (completed) R.string.todo_mark_not_done else R.string.todo_mark_done,
        subtask.title,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = completed,
            onCheckedChange = onToggleCompleted,
            modifier = Modifier.semantics { contentDescription = description },
        )
        Text(
            text = subtask.title.decorated(completed),
            style = MaterialTheme.typography.bodyMedium,
            color = if (completed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
