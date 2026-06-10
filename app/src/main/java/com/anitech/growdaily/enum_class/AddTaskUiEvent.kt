package com.anitech.growdaily.enum_class

sealed class AddTaskUiEvent {
    data class ShowValidationError(val error: AddTaskValidationError) : AddTaskUiEvent()
    data class ShowMessage(
        val message: String,
        val actionLabel: String? = null,
        val retrySave: Boolean = false
    ) : AddTaskUiEvent()

    data class Saved(val isEdit: Boolean) : AddTaskUiEvent()
    data object NavigateBack : AddTaskUiEvent()
}
