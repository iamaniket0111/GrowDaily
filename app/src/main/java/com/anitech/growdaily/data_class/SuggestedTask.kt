package com.anitech.growdaily.data_class

import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import java.util.UUID

data class SuggestedTask(
    val title: String,
    val note: String? = null,
    val taskType: String = TaskType.DAILY.name,
    val repeatType: String = RepeatType.DAILY.name,
    val taskColor: String = TaskColor.DARK_BLUE.name,
    val scheduleTime: String? = null,
    val isAdded: Boolean = false
) {
    fun toTaskEntity(selectedDate: String): TaskEntity {
        val parsedTaskType = runCatching { TaskType.valueOf(taskType) }.getOrDefault(TaskType.DAILY)
        val parsedRepeatType = runCatching { RepeatType.valueOf(repeatType) }.getOrDefault(RepeatType.DAILY)
        val taskId = UUID.randomUUID().toString()

        return TaskEntity(
            id = taskId,
            seriesId = taskId,
            title = title,
            note = note,
            weight = TaskWeight.LOW,
            scheduledTime = scheduleTime,
            endTime = null,
            reminderTime = null,
            reminderEnabled = false,
            isScheduled = true,
            taskAddedDate = selectedDate,
            taskRemovedDate = null,
            inactiveReason = null,
            iconResId = "ic_task",
            colorCode = "#708CFF",
            taskType = parsedTaskType,
            showUntilCompleted = false,
            repeatType = parsedRepeatType,
            repeatDays = null,
            dailyTargetCount = 1,
            manualOrder = 0,
            scheduledMinutes = null
        )
    }
}
