package com.example.mnlists.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Half the platform long-press timeout: the handle should feel snappy, and
 * [ReorderHandle] overrides `LocalViewConfiguration` for its pointer input so
 * the system default never leaks in (Step 10.5).
 */
internal const val REORDER_LONG_PRESS_MS = 250L

/** Lift applied to the row under the finger while dragging (Step 10.5). */
internal const val REORDER_LIFT_ELEVATION_DP = 12f
internal const val REORDER_LIFT_SCALE = 1.02f

/**
 * How far a non-dragged row shifts to open a gap for the incoming row, in px.
 *
 * Moving down (`target > dragged`) pulls the rows in between **up** by the
 * cumulative height of the rows above them; moving up pushes them **down** by
 * the cumulative height of the rows below them. Rows outside the dragged range
 * (and the dragged row itself) do not move. Kept pure so it can be unit-tested
 * without a Compose host.
 */
internal fun gapShift(
    draggedIndex: Int,
    targetIndex: Int,
    rowIndex: Int,
    rowSizes: List<Int>,
): Float {
    if (draggedIndex == targetIndex || rowIndex == draggedIndex) return 0f
    return when {
        targetIndex > draggedIndex && rowIndex in (draggedIndex + 1)..targetIndex ->
            -(draggedIndex until rowIndex).sumOf { rowSizes.getOrElse(it) { 0 } }.toFloat()

        targetIndex < draggedIndex && rowIndex in targetIndex until draggedIndex ->
            ((rowIndex + 1)..draggedIndex).sumOf { rowSizes.getOrElse(it) { 0 } }.toFloat()

        else -> 0f
    }
}

/**
 * Shared drag-reorder plumbing for the three tab lists (Step 10).
 *
 * The handle is the only drag target: a long-press starts a drag, the row
 * follows the finger, and releasing commits the target slot. While the finger
 * moves, rows between the origin and the prospective drop slot part smoothly
 * to show where the row will land (Step 10.5). A plain tap is left to the
 * caller (To-Do's/Dailies use it to collapse/expand; Habits pass no tap action
 * so their handle is inert apart from dragging).
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
    private var rowSizes: List<Int> = emptyList()
    /** Snapshot state so neighbour rows recompose when the drop slot changes. */
    private var targetIndex by mutableStateOf<Int?>(null)
    private var onMove: (Int, Int) -> Unit = { _, _ -> }

    /**
     * Translation to apply to [key]'s row: the raw finger displacement for the
     * dragged row, or the gap-opening shift for a neighbour. The latter is
     * animated by [Modifier.reorderItem].
     */
    fun translationFor(key: Any): Float {
        if (key == draggedKey) return dragOffsetY
        val dragged = draggedKey ?: return 0f
        val from = keyToIndex[dragged] ?: return 0f
        val row = keyToIndex[key] ?: return 0f
        val to = targetIndex ?: return 0f
        return gapShift(from, to, row, rowSizes)
    }

    internal fun start(key: Any, sectionKeys: List<Any>, move: (Int, Int) -> Unit) {
        val visible = listState.layoutInfo.visibleItemsInfo.associateBy { it.key }
        val info = visible[key] ?: return
        keyToIndex = sectionKeys.withIndex().associate { (index, sectionKey) -> sectionKey to index }
        // Freeze heights for the duration of the drag: expanding/collapsing a
        // subtask can't happen mid-drag, and this keeps the gap arithmetic stable.
        rowSizes = sectionKeys.map { visible[it]?.size ?: info.size }
        dragStartOffset = info.offset
        dragStartSize = info.size
        dragOffsetY = 0f
        targetIndex = keyToIndex[key]
        onMove = move
        draggedKey = key
    }

    internal fun drag(deltaY: Float) {
        if (draggedKey == null) return
        dragOffsetY += deltaY
        // Update the prospective drop slot on every frame so neighbours part
        // before the finger reaches them.
        targetIndex = nearestTargetIndex()
    }

    /** Commits the move to the row whose centre is now closest to the finger. */
    internal fun end() {
        val key = draggedKey ?: return
        val from = keyToIndex[key]
        val to = nearestTargetIndex() ?: targetIndex
        val move = onMove
        reset()
        if (from != null && to != null && to != from) move(from, to)
    }

    internal fun cancel() = reset()

    private fun nearestTargetIndex(): Int? {
        if (keyToIndex.isEmpty()) return null
        val center = dragStartOffset + dragStartSize / 2f + dragOffsetY
        return listState.layoutInfo.visibleItemsInfo
            .filter { it.key in keyToIndex }
            .minByOrNull { abs(it.offset + it.size / 2f - center) }
            ?.key
            ?.let { keyToIndex[it] }
    }

    private fun reset() {
        draggedKey = null
        dragOffsetY = 0f
        keyToIndex = emptyMap()
        rowSizes = emptyList()
        targetIndex = null
        onMove = { _, _ -> }
    }
}

@Composable
fun rememberReorderDragState(listState: LazyListState): ReorderDragState =
    remember(listState) { ReorderDragState(listState) }

/**
 * Lifts the dragged row (elevation + slight scale) and springs every neighbour
 * towards its gap-opening offset, so rows part and close again smoothly.
 */
@Composable
fun Modifier.reorderItem(state: ReorderDragState, key: Any): Modifier {
    val isDragging = state.draggedKey == key
    val restingShift = if (isDragging) 0f else state.translationFor(key)
    val animatedShift by animateFloatAsState(
        targetValue = restingShift,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.9f),
        label = "reorder-gap-shift",
    )
    val shape = MaterialTheme.shapes.large
    return this
        .graphicsLayer {
            translationY = if (isDragging) state.dragOffsetY else animatedShift
            shadowElevation = if (isDragging) REORDER_LIFT_ELEVATION_DP.dp.toPx() else 0f
            this.shape = shape
            scaleX = if (isDragging) REORDER_LIFT_SCALE else 1f
            scaleY = if (isDragging) REORDER_LIFT_SCALE else 1f
        }
        .zIndex(if (isDragging) 1f else 0f)
}

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
    // detectDragGesturesAfterLongPress reads ViewConfiguration from the layout
    // node, so override it locally for this handle only (Step 10.5).
    val baseConfig = LocalViewConfiguration.current
    val fastConfig = remember(baseConfig) {
        object : ViewConfiguration by baseConfig {
            override val longPressTimeoutMillis: Long = REORDER_LONG_PRESS_MS
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides fastConfig) {
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
}
