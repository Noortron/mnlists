package com.example.mnlists.data.settings

/**
 * How the To-Do tab orders its rows (Step 14).
 *
 * [MANUAL] keeps the order produced by drag reordering (the default, and the
 * only mode in which dragging changes the visible order). [DUE_DATE] puts the
 * earliest deadline first, with undated to-dos last; the saved manual order is
 * kept intact as the tie-breaker so switching back is lossless.
 */
enum class TodoSortMode {
    MANUAL,
    DUE_DATE,
    ;

    val storageValue: String get() = name

    companion object {
        fun fromStorage(value: String?): TodoSortMode =
            entries.firstOrNull { it.name == value } ?: MANUAL
    }
}
