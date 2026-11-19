package com.example.mnlists.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Shared drag-reorder plumbing for the three tab lists (Step 10).
 *
 * The handle is the only drag target: a long-press starts a drag, the row
 * follows the finger, and releasing commits the target slot. A plain tap is
 * left to the caller (To-Do's/Dailies use it to collapse/expand; Habits pass
 * no tap action so their handle is inert apart from dragging).
 */
@Stable
class ReorderDragState internal constructor(private val listState: LazyListState) {

    /** Key of the row currently under the finger, or null when idle. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /** Vertical displacement of the dragged row from its laid-out slot. */
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    private var dragStartOffset = 0
    private var dragStartSize = 0
    private var keyToIndex: Map<Any, Int> = emptyMap()
    private var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** Translation to apply to [key]'s row while it is being dragged. */
    fun translationFor(key: Any): Float = if (draggedKey == key) dragOffsetY else 0f

    internal fun start(key: Any, sectionKeys: List<Any>, move: (Int, Int) -> Unit) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        keyToIndex = sectionKeys.withIndex().associate { (index, sectionKey) -> sectionKey to index }
        dragStartOffset = info.offset
        dragStartSize = info.size
        dragOffsetY = 0f
        onMove = move
        draggedKey = key
    }

    internal fun drag(deltaY: Float) {
        if (draggedKey != null) dragOffsetY += deltaY
    }

    /** Commits the move to the row whose centre is now closest to the finger. */
    internal fun end() {
        val key = draggedKey ?: return
        val from = keyToIndex[key]
        val move = onMove
        val target = if (from == null) {
            null
        } else {
            val center = dragStartOffset + dragStartSize / 2f + dragOffsetY
            listState.layoutInfo.visibleItemsInfo
                .filter { it.key in keyToIndex }
                .minByOrNull { abs(it.offset + it.size / 2f - center) }
                ?.key
                ?.let { keyToIndex[it] }
        }
        reset()
        if (from != null && target != null && target != from) move(from, target)
    }

    internal fun cancel() = reset()

    private fun reset() {
        draggedKey = null
        dragOffsetY = 0f
        keyToIndex = emptyMap()
        onMove = { _, _ -> }
    }
}

@Composable
fun rememberReorderDragState(listState: LazyListState): ReorderDragState =
    remember(listState) { ReorderDragState(listState) }

/** Lifts and translates the dragged row so it slides over its neighbours. */
fun Modifier.reorderItem(state: ReorderDragState, key: Any): Modifier = this
    .graphicsLayer { translationY = state.translationFor(key) }
    .zIndex(if (state.draggedKey == key) 1f else 0f)

/**
 * Long-press drag gesture for one handle. [sectionKeys] is the ordered key
 * list of the section this row belongs to; moves are reported as indexes into
 * that section so callers never reorder across open/Skipped/Completed groups.
 */
@Composable
fun Modifier.reorderHandle(
    state: ReorderDragState,
    key: Any,
    sectionKeys: List<Any>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): Modifier {
    val move by rememberUpdatedState(onMove)
    return pointerInput(state, key, sectionKeys) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(key, sectionKeys) { from, to -> move(from, to) } },
            onDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.cancel() },
        )
    }
}

/** The visible drag grip. [onTap] is null for lists that do not collapse. */
@Composable
fun ReorderHandle(
    description: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (onTap == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onTap)
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
