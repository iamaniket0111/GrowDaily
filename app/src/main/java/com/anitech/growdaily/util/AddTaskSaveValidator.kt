package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.enum_class.AddTaskValidationError
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TrackingType

object AddTaskSaveValidator {

    fun validate(state: AddTaskUiState, taskType: TaskType): AddTaskValidationError? {
        if (state.title.trim().isBlank()) return AddTaskValidationError.TITLE_REQUIRED
        if (state.isScheduled && state.scheduleTime == null) {
            return AddTaskValidationError.SCHEDULE_TIME_REQUIRED
        }
        if (state.isReminderEnabled && state.reminderTime == null) {
            return AddTaskValidationError.REMINDER_TIME_REQUIRED
        }
        validateChecklist(state)?.let { return it }
        validateRepeat(state, taskType)?.let { return it }
        return null
    }

    private fun validateChecklist(state: AddTaskUiState): AddTaskValidationError? {
        return if (state.trackingType == TrackingType.CHECKLIST && state.checklistItems.isEmpty()) {
            AddTaskValidationError.CHECKLIST_EMPTY
        } else {
            null
        }
    }

    private fun validateRepeat(state: AddTaskUiState, taskType: TaskType): AddTaskValidationError? {
        if (taskType != TaskType.DAILY) return null
        return when (state.repeatType) {
            RepeatType.DAYS_OF_WEEK ->
                if (state.repeatDays.isEmpty()) AddTaskValidationError.REPEAT_WEEKDAYS_REQUIRED else null
            RepeatType.DAYS_OF_MONTH ->
                if (state.repeatDays.isEmpty()) AddTaskValidationError.REPEAT_MONTH_DAYS_REQUIRED else null
            RepeatType.DAILY -> null
        }
    }
}
