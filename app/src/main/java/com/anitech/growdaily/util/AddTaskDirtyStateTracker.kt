package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.TaskEntity

/**
 * Tracks baselines for discard-changes detection.
 * List baselines are not compared until [onListIdsLoaded] (edit mode) or [markAddModeReady] (add mode).
 */
class AddTaskDirtyStateTracker {

    private var stateBaseline: AddTaskUiState? = null
    private var listBaseline: List<String>? = null
    private var listsBaselineReady: Boolean = false

    fun hasStateBaseline(): Boolean = stateBaseline != null

    fun markAddModeReady(state: AddTaskUiState) {
        stateBaseline = normalize(state)
        listBaseline = emptyList()
        listsBaselineReady = true
    }

    fun tryCaptureEditState(state: AddTaskUiState, task: TaskEntity): Boolean {
        if (stateBaseline != null) return true
        val looksLoaded =
            state.title == task.title &&
                state.startDate == task.taskAddedDate &&
                state.icon == task.iconResId &&
                state.color == task.colorCode
        if (looksLoaded) {
            stateBaseline = normalize(state)
        }
        return looksLoaded
    }

    fun onListIdsLoaded(ids: List<String>) {
        listBaseline = ids.sorted()
        listsBaselineReady = true
    }

    fun hasUnsavedChanges(currentState: AddTaskUiState, currentListIds: List<String>): Boolean {
        val baselineState = stateBaseline ?: return false
        if (!listsBaselineReady) return false
        val baselineLists = listBaseline ?: return false
        return normalize(currentState) != baselineState ||
            currentListIds.sorted() != baselineLists
    }

    fun normalize(state: AddTaskUiState): AddTaskUiState {
        return state.copy(
            title = state.title.trim(),
            note = state.note.trim(),
            checklistItems = state.checklistItems.map { it.trim() },
            repeatDays = state.repeatDays.distinct().sorted(),
            isLoading = false,
            manualOrder = 0
        )
    }
}
