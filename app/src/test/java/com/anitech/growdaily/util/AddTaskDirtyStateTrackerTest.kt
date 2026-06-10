package com.anitech.growdaily.util

import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TrackingType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTaskDirtyStateTrackerTest {

    @Test
    fun addMode_noChanges_returnsFalse() {
        val tracker = AddTaskDirtyStateTracker()
        val baseline = AddTaskUiState(title = "Task")
        tracker.markAddModeReady(baseline)

        assertFalse(
            tracker.hasUnsavedChanges(
                currentState = baseline,
                currentListIds = emptyList()
            )
        )
    }

    @Test
    fun addMode_titleChanged_returnsTrue() {
        val tracker = AddTaskDirtyStateTracker()
        val baseline = AddTaskUiState(title = "Task")
        tracker.markAddModeReady(baseline)

        assertTrue(
            tracker.hasUnsavedChanges(
                currentState = baseline.copy(title = "Updated"),
                currentListIds = emptyList()
            )
        )
    }

    @Test
    fun editMode_beforeListBaselineLoaded_returnsFalse() {
        val tracker = AddTaskDirtyStateTracker()
        val task = sampleTask()
        val state = AddTaskUiState(
            title = task.title,
            startDate = task.taskAddedDate,
            icon = task.iconResId,
            color = task.colorCode
        )
        tracker.tryCaptureEditState(state, task)

        assertFalse(
            tracker.hasUnsavedChanges(
                currentState = state,
                currentListIds = listOf("list-1")
            )
        )
    }

    @Test
    fun editMode_listChangedAfterBaseline_returnsTrue() {
        val tracker = AddTaskDirtyStateTracker()
        val task = sampleTask()
        val state = AddTaskUiState(
            title = task.title,
            startDate = task.taskAddedDate,
            icon = task.iconResId,
            color = task.colorCode
        )
        tracker.tryCaptureEditState(state, task)
        tracker.onListIdsLoaded(listOf("list-1"))

        assertTrue(
            tracker.hasUnsavedChanges(
                currentState = state,
                currentListIds = listOf("list-1", "list-2")
            )
        )
    }

    private fun sampleTask(): TaskEntity {
        return TaskEntity(
            id = "task-1",
            seriesId = "task-1",
            title = "Sample",
            note = null,
            weight = com.anitech.growdaily.enum_class.TaskWeight.VERY_LOW,
            scheduledTime = null,
            reminderTime = null,
            reminderEnabled = false,
            isScheduled = false,
            taskAddedDate = "2026-01-01",
            taskRemovedDate = null,
            inactiveReason = null,
            iconResId = "TROPHY",
            colorCode = "DARK_BLUE",
            taskType = TaskType.DAILY,
            showUntilCompleted = false,
            repeatType = null,
            repeatDays = null,
            dailyTargetCount = 0,
            manualOrder = 0,
            scheduledMinutes = null,
            trackingType = TrackingType.BINARY,
            checklistItems = null,
            targetDurationSeconds = 0L
        )
    }
}
