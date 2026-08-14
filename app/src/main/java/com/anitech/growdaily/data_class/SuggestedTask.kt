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
    val repeatDays: String? = null,
    val taskColor: String? = TaskColor.DARK_BLUE.name,
    val taskIcon: String? = TaskIcon.BELL.name,
    val weight: String? = TaskWeight.VERY_LOW.name,
    val targetListId: String? = null,
    val listName: String? = null,
    val createNewList: String? = null,
    val scheduleTime: String? = null,
    val endTime: String? = null,
    val reminderTime: String? = null,
    val reminderEnabled: Boolean? = false,
    val showUntilCompleted: Boolean? = null,
    val trackingType: String? = TrackingType.BINARY.name,
    val dailyTargetCount: Int? = 1,
    val targetDurationSeconds: Long? = 0L,
    val checklistItems: List<String>? = null,
    val isAdded: Boolean = false,
    val draftTaskEntity: TaskEntity? = null
) {
    val safeTaskType: String get() = taskType ?: TaskType.DAILY.name
    val safeRepeatType: String get() = repeatType ?: RepeatType.DAILY.name
    val safeTaskColor: String get() = taskColor ?: TaskColor.DARK_BLUE.name
    val safeTaskIcon: String get() = taskIcon ?: TaskIcon.BELL.name
    val safeWeight: String get() = weight ?: TaskWeight.VERY_LOW.name
    val safeTrackingType: String get() = trackingType ?: TrackingType.BINARY.name

    fun toTaskEntity(selectedDate: String, manualOrder: Int = 0): TaskEntity {
        if (draftTaskEntity != null) {
            return draftTaskEntity.copy(
                taskAddedDate = selectedDate,
                manualOrder = if (manualOrder > 0) manualOrder else draftTaskEntity.manualOrder
            )
        }
        val parsedTaskType = runCatching { TaskType.valueOf(safeTaskType) }.getOrDefault(TaskType.DAILY)
        val parsedRepeatType = runCatching { RepeatType.valueOf(safeRepeatType) }.getOrDefault(RepeatType.DAILY)
        val parsedColor = runCatching { TaskColor.valueOf(safeTaskColor) }.getOrDefault(TaskColor.DARK_BLUE)
        val parsedIcon = runCatching { TaskIcon.valueOf(safeTaskIcon) }.getOrDefault(TaskIcon.BELL)
        val parsedWeight = runCatching { TaskWeight.valueOf(safeWeight) }.getOrDefault(TaskWeight.VERY_LOW)
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
            0
        }

        val startMins = scheduleTime?.let { CommonMethods.timeToMinutes(it) }
        val calculatedEndTime = if (durationMinutes > 0 && startMins != null) {
            CommonMethods.minutesToTime((startMins + durationMinutes) % 1440)
        } else null

        val finalEndTime = when {
            !endTime.isNullOrBlank() -> endTime
            calculatedEndTime != null -> calculatedEndTime
            scheduleTime != null -> com.anitech.growdaily.dialog.TaskDurationDialog.UNTIL_NEXT
            else -> null
        }

        val isShowUntilCompleted = showUntilCompleted ?: (parsedTaskType == TaskType.DAY)

        val jsonChecklistItems = if (parsedTrackingType == TrackingType.CHECKLIST && !checklistItems.isNullOrEmpty()) {
            runCatching { Gson().toJson(checklistItems) }.getOrNull()
        } else null

        val finalTargetCount = if (parsedTrackingType == TrackingType.COUNT && dailyTargetCount != null && dailyTargetCount > 0) {
            dailyTargetCount
        } else {
            1
        }

        val isReminderOn = reminderEnabled == true || !reminderTime.isNullOrBlank()

        return TaskEntity(
            id = taskId,
            seriesId = taskId,
            title = title.ifBlank { "New Habit" },
            note = note,
            weight = parsedWeight,
            scheduledTime = scheduleTime,
            endTime = finalEndTime,
            reminderTime = reminderTime,
            reminderEnabled = isReminderOn,
            isScheduled = scheduleTime != null,
            taskAddedDate = selectedDate,
            taskRemovedDate = null,
            inactiveReason = null,
            iconResId = parsedIcon.name,
            colorCode = parsedColor.name,
            taskType = parsedTaskType,
            showUntilCompleted = isShowUntilCompleted,
            repeatType = parsedRepeatType,
            repeatDays = repeatDays,
            dailyTargetCount = finalTargetCount,
            manualOrder = manualOrder,
            scheduledMinutes = startMins,
            trackingType = parsedTrackingType,
            checklistItems = jsonChecklistItems,
            targetDurationSeconds = finalTargetDuration
        )
    }
}

fun SuggestedTask.updateFromEntity(entity: TaskEntity): SuggestedTask {
    val parsedChecklist = if (entity.trackingType == TrackingType.CHECKLIST && !entity.checklistItems.isNullOrEmpty()) {
        runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            com.google.gson.Gson().fromJson<List<String>>(entity.checklistItems, type)
        }.getOrNull() ?: checklistItems
    } else checklistItems

    return copy(
        title = entity.title,
        note = entity.note,
        scheduleTime = entity.scheduledTime,
        endTime = entity.endTime,
        reminderTime = entity.reminderTime,
        reminderEnabled = entity.reminderEnabled,
        taskColor = entity.colorCode,
        taskIcon = entity.iconResId,
        weight = entity.weight.name,
        taskType = entity.taskType.name,
        trackingType = entity.trackingType.name,
        dailyTargetCount = entity.dailyTargetCount,
        targetDurationSeconds = entity.targetDurationSeconds,
        checklistItems = parsedChecklist,
        draftTaskEntity = entity
    )
}
