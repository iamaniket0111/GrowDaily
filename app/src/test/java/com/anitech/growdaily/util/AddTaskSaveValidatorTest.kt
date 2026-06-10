package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.enum_class.AddTaskValidationError
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddTaskSaveValidatorTest {

    @Test
    fun blankTitle_returnsTitleRequired() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(title = "   "),
            TaskType.DAILY
        )
        assertEquals(AddTaskValidationError.TITLE_REQUIRED, error)
    }

    @Test
    fun scheduleEnabledWithoutTime_returnsScheduleRequired() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(title = "Task", isScheduled = true, scheduleTime = null),
            TaskType.DAILY
        )
        assertEquals(AddTaskValidationError.SCHEDULE_TIME_REQUIRED, error)
    }

    @Test
    fun reminderEnabledWithoutTime_returnsReminderRequired() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(title = "Task", isReminderEnabled = true, reminderTime = null),
            TaskType.DAILY
        )
        assertEquals(AddTaskValidationError.REMINDER_TIME_REQUIRED, error)
    }

    @Test
    fun checklistWithoutItems_returnsChecklistEmpty() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(title = "Task", trackingType = TrackingType.CHECKLIST),
            TaskType.DAILY
        )
        assertEquals(AddTaskValidationError.CHECKLIST_EMPTY, error)
    }

    @Test
    fun weeklyRepeatWithoutDays_returnsWeekdaysRequired() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(
                title = "Task",
                repeatType = RepeatType.DAYS_OF_WEEK,
                repeatDays = emptyList()
            ),
            TaskType.DAILY
        )
        assertEquals(AddTaskValidationError.REPEAT_WEEKDAYS_REQUIRED, error)
    }

    @Test
    fun monthlyRepeatWithoutDays_returnsMonthDaysRequired() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(
                title = "Task",
                repeatType = RepeatType.DAYS_OF_MONTH,
                repeatDays = emptyList()
            ),
            TaskType.DAILY
        )
        assertEquals(AddTaskValidationError.REPEAT_MONTH_DAYS_REQUIRED, error)
    }

    @Test
    fun dayTask_skipsRepeatValidation() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(
                title = "Task",
                repeatType = RepeatType.DAYS_OF_WEEK,
                repeatDays = emptyList()
            ),
            TaskType.DAY
        )
        assertNull(error)
    }

    @Test
    fun validDailyTask_returnsNull() {
        val error = AddTaskSaveValidator.validate(
            AddTaskUiState(
                title = "Morning run",
                repeatType = RepeatType.DAYS_OF_WEEK,
                repeatDays = listOf(1, 3, 5),
                checklistItems = listOf("Shoes")
            ),
            TaskType.DAILY
        )
        assertNull(error)
    }
}
