package com.anitech.growdaily.data_class

import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import com.google.gson.Gson
import java.util.UUID

data class SuggestedTask(
    val title: String = "",
    val note: String? = null,
    val taskType: String? = TaskType.DAILY.name,
    val repeatType: String? = RepeatType.DAILY.name,
    val taskColor: String? = TaskColor.DARK_BLUE.name,
    val taskIcon: String? = TaskIcon.BELL.name,
    val scheduleTime: String? = null,
    val showUntilCompleted: Boolean? = null,
    val trackingType: String? = TrackingType.BINARY.name,
    val dailyTargetCount: Int? = 1,
    val targetDurationSeconds: Long? = 0L,
    val checklistItems: List<String>? = null,
    val isAdded: Boolean = false
) {
    val safeTaskType: String get() = taskType ?: TaskType.DAILY.name
    val safeRepeatType: String get() = repeatType ?: RepeatType.DAILY.name
    val safeTaskColor: String get() = taskColor ?: TaskColor.DARK_BLUE.name
    val safeTaskIcon: String get() = taskIcon ?: TaskIcon.BELL.name
    val safeTrackingType: String get() = trackingType ?: TrackingType.BINARY.name

    fun toTaskEntity(selectedDate: String, manualOrder: Int = 0): TaskEntity {
        val parsedTaskType = runCatching { TaskType.valueOf(safeTaskType) }.getOrDefault(TaskType.DAILY)
        val parsedRepeatType = runCatching { RepeatType.valueOf(safeRepeatType) }.getOrDefault(RepeatType.DAILY)
        val parsedColor = runCatching { TaskColor.valueOf(safeTaskColor) }.getOrDefault(TaskColor.DARK_BLUE)
        val parsedIcon = runCatching { TaskIcon.valueOf(safeTaskIcon) }.getOrDefault(TaskIcon.BELL)
        val parsedTrackingType = if (safeTrackingType.equals("COUNTER", ignoreCase = true)) {
            TrackingType.COUNT
        } else {
            runCatching { TrackingType.valueOf(safeTrackingType) }.getOrDefault(TrackingType.BINARY)
        }
        val taskId = UUID.randomUUID().toString()

        val finalTargetDuration = if (targetDurationSeconds != null && targetDurationSeconds > 0L) {
            targetDurationSeconds
        } else {
            0L
        }

        val durationMinutes = if (finalTargetDuration > 0L) {
            (finalTargetDuration / 60L).toInt()
        } else {
            15
        }

        val startMins = scheduleTime?.let { CommonMethods.timeToMinutes(it) }
        val calculatedEndTime = startMins?.let { CommonMethods.minutesToTime((it + durationMinutes) % 1440) }

        val isShowUntilCompleted = showUntilCompleted ?: (parsedTaskType == TaskType.DAY)

        val jsonChecklistItems = if (parsedTrackingType == TrackingType.CHECKLIST && !checklistItems.isNullOrEmpty()) {
            runCatching { Gson().toJson(checklistItems) }.getOrNull()
        } else null

        val finalTargetCount = if (parsedTrackingType == TrackingType.COUNT && dailyTargetCount != null && dailyTargetCount > 0) {
            dailyTargetCount
        } else {
            1
        }

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
            iconResId = parsedIcon.name,
            colorCode = parsedColor.name,
            taskType = parsedTaskType,
            showUntilCompleted = isShowUntilCompleted,
            repeatType = parsedRepeatType,
            repeatDays = null,
            dailyTargetCount = finalTargetCount,
            manualOrder = manualOrder,
            scheduledMinutes = startMins,
            trackingType = parsedTrackingType,
            checklistItems = jsonChecklistItems,
            targetDurationSeconds = finalTargetDuration
        )
    }
}
