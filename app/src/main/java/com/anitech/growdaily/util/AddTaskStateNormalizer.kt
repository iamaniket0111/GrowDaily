package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.AddTaskUiState

object AddTaskStateNormalizer {
    fun forPersistence(state: AddTaskUiState): AddTaskUiState {
        return state.copy(
            title = state.title.trim(),
            note = state.note.trim(),
            checklistItems = state.checklistItems.map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
}
