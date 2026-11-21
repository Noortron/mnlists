package com.example.mnlists.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * Read side of the persisted To-Do sort choice, kept as a tiny interface so
 * ViewModel tests can supply a fixed mode without an Android DataStore.
 */
interface TodoSortPreferences {
    val todoSortMode: Flow<TodoSortMode>
}
