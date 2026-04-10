package com.anitech.growdaily.database.util

import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import java.time.LocalDate

fun buildTaskDaySnapshots(
    tasks: List<TaskEntity>,
    completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
    trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
    startDate: LocalDate,
    endDate: LocalDate
): List<TaskDaySnapshotEntity> {
    if (tasks.isEmpty() || endDate.isBefore(startDate)) return emptyList()

    val snapshots = ArrayList<TaskDaySnapshotEntity>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
        val dateString = currentDate.toString()
        val tasksForDate = CommonMethods.filterTasksForDate(tasks, dateString)
        tasksForDate.forEach { task ->
            val completion = completionEntityMap[dateString]?.get(task.id)
            val settings = resolveTrackingSettings(task, dateString, trackingVersionsMap[task.id].orEmpty())
            snapshots.add(
                TaskDaySnapshotEntity(
                    taskId = task.id,
                    date = dateString,
                    completionCount = completion?.count ?: 0,
                    progressPercent = completionPercent(task, completion, settings),
                    isCompleted = isCompletedDerived(task, completion, settings)
                )
            )
        }
        currentDate = currentDate.plusDays(1)
    }

    return snapshots
}
