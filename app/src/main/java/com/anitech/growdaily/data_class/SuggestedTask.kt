package com.anitech.growdaily.data_class

import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import java.util.UUID

data class SuggestedTask(
    val title: String = "",
    val note: String? = null,
    val taskType: String? = TaskType.DAILY.name,
    val repeatType: String? = RepeatType.DAILY.name,
    val taskColor: String? = TaskColor.DARK_BLUE.name,
    val scheduleTime: String? = null,
    val isAdded: Boolean = false
) {
    val safeTaskType: String get() = taskType ?: TaskType.DAILY.name
    val safeRepeatType: String get() = repeatType ?: RepeatType.DAILY.name
    val safeTaskColor: String get() = taskColor ?: TaskColor.DARK_BLUE.name

    fun toTaskEntity(selectedDate: String, manualOrder: Int = 0): TaskEntity {
        val parsedTaskType = runCatching { TaskType.valueOf(safeTaskType) }.getOrDefault(TaskType.DAILY)
        val parsedRepeatType = runCatching { RepeatType.valueOf(safeRepeatType) }.getOrDefault(RepeatType.DAILY)
        val parsedColor = runCatching { TaskColor.valueOf(safeTaskColor) }.getOrDefault(TaskColor.DARK_BLUE)
        val taskId = UUID.randomUUID().toString()

        val startMins = scheduleTime?.let { CommonMethods.timeToMinutes(it) }
        val calculatedEndTime = startMins?.let { CommonMethods.minutesToTime((it + 15) % 1440) }

        return TaskEntity(
            id = taskId,
            seriesId = taskId,
            title = title.ifBlank { "New Habit" },
            note = note,
            weight = TaskWeight.VERY_LOW,
            scheduledTime = scheduleTime,
            endTime = calculatedEndTime,
            reminderTime = null,
            reminderEnabled = false,
            isScheduled = scheduleTime != null,
            taskAddedDate = selectedDate,
            taskRemovedDate = null,
            inactiveReason = null,
            iconResId = TaskIcon.TROPHY.name,
            colorCode = parsedColor.name,
            taskType = parsedTaskType,
            showUntilCompleted = false,
            repeatType = parsedRepeatType,
            repeatDays = null,
            dailyTargetCount = 1,
            manualOrder = manualOrder,
            scheduledMinutes = startMins
        )
    }
}
