package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.ManagedRepeatTaskUi
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskExtraDateEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.data_class.UntilCompleteChildEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.util.isCompletedDerived
import com.anitech.growdaily.database.util.resolveTrackingSettings
import com.anitech.growdaily.enum_class.ManageTaskSection
import com.anitech.growdaily.enum_class.TaskInactiveReason
import com.anitech.growdaily.enum_class.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class ManageRepeatTasksViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val todayString = CommonMethods.getTodayDate()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val _busySeriesIds = MutableLiveData<Set<String>>(emptySet())
    val busySeriesIds: LiveData<Set<String>> = _busySeriesIds

    private data class AllDayManageBase(
        val tasks: List<TaskEntity>,
        val completions: List<TaskCompletionEntity>,
        val trackingVersions: List<TaskTrackingVersionEntity>,
        val snapshots: List<TaskDaySnapshotEntity>,
        val extraDates: List<TaskExtraDateEntity>
    )

    val allDayTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getAllTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow(),
            repository.getAllTaskExtraDatesFlow()
        ) { tasks, completions, trackingVersions, snapshots, extraDates ->
            AllDayManageBase(
                tasks = tasks,
                completions = completions,
                trackingVersions = trackingVersions,
                snapshots = snapshots,
                extraDates = extraDates
            )
        }.combine(repository.getAllUntilCompleteChildrenFlow()) { base, children ->
            buildAllDayList(
                tasks = base.tasks,
                completions = base.completions,
                trackingVersions = base.trackingVersions,
                snapshots = base.snapshots,
                extraDates = base.extraDates,
                children = children
            )
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    val allRepeatTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getRepeatTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow()
        ) { tasks, completions, trackingVersions, snapshots ->
            buildAllRepeatList(tasks, completions, trackingVersions, snapshots)
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    val activeRepeatTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getRepeatTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow()
        ) { tasks, completions, trackingVersions, snapshots ->
            buildActiveRepeatList(tasks, completions, trackingVersions, snapshots)
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    val pausedTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getRepeatTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow()
        ) { tasks, completions, trackingVersions, snapshots ->
            buildManagedList(
                tasks = tasks,
                completions = completions,
                trackingVersions = trackingVersions,
                snapshots = snapshots,
                reason = TaskInactiveReason.PAUSED
            )
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    val endedTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getRepeatTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow()
        ) { tasks, completions, trackingVersions, snapshots ->
            buildManagedList(
                tasks = tasks,
                completions = completions,
                trackingVersions = trackingVersions,
                snapshots = snapshots,
                reason = TaskInactiveReason.ENDED
            )
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    val dayActiveTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getAllTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow(),
            repository.getAllTaskExtraDatesFlow()
        ) { tasks, completions, trackingVersions, snapshots, extraDates ->
            AllDayManageBase(
                tasks = tasks,
                completions = completions,
                trackingVersions = trackingVersions,
                snapshots = snapshots,
                extraDates = extraDates
            )
        }.combine(repository.getAllUntilCompleteChildrenFlow()) { base, children ->
            buildActiveDayList(
                tasks = base.tasks,
                completions = base.completions,
                trackingVersions = base.trackingVersions,
                snapshots = base.snapshots,
                extraDates = base.extraDates,
                children = children
            )
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    val dayMissedTasks: LiveData<List<ManagedRepeatTaskUi>> =
        combine(
            repository.getAllTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow(),
            repository.getAllTaskExtraDatesFlow()
        ) { tasks, completions, trackingVersions, snapshots, extraDates ->
            AllDayManageBase(
                tasks = tasks,
                completions = completions,
                trackingVersions = trackingVersions,
                snapshots = snapshots,
                extraDates = extraDates
            )
        }.combine(repository.getAllUntilCompleteChildrenFlow()) { base, children ->
            buildMissedDayList(
                tasks = base.tasks,
                completions = base.completions,
                trackingVersions = base.trackingVersions,
                snapshots = base.snapshots,
                extraDates = base.extraDates,
                children = children
            )
        }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    private fun buildAllDayList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>,
        extraDates: List<TaskExtraDateEntity>,
        children: List<UntilCompleteChildEntity>
    ): List<ManagedRepeatTaskUi> {
        val trackingVersionMap = trackingVersions
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
        val completionByTaskId = completions.groupBy { it.taskId.trim().lowercase() }
        val snapshotByTaskId = snapshots
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry -> entry.value.associateBy { it.date } }
        val extraDateMap = extraDates
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.map { it.date }.toSet() }
        val childrenByParent = children.groupBy { it.parentTaskId }

        return tasks
            .asSequence()
            .filter { it.taskType == TaskType.DAY || it.taskType == TaskType.UNTIL_COMPLETE }
            .filter { CommonMethods.isWithinTaskLifetime(it, todayString) }
            .mapNotNull { task ->
                val metaDate = runCatching { LocalDate.parse(task.taskAddedDate, dateFormatter) }.getOrNull()
                    ?: return@mapNotNull null
                val completion = completionByTaskId[task.id.trim().lowercase()]
                    ?.firstOrNull { it.date == task.taskAddedDate }
                val snapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(task.taskAddedDate)
                val settings = resolveTrackingSettings(
                    task = task,
                    date = task.taskAddedDate,
                    versions = trackingVersionMap[task.id].orEmpty()
                )
                val completed = snapshot?.isCompleted
                    ?: isCompletedDerived(task, completion, settings)

                val activeDates = linkedSetOf(task.taskAddedDate)
                when (task.taskType) {
                    TaskType.UNTIL_COMPLETE ->
                        activeDates.addAll(childrenByParent[task.id]?.map { it.taskAddedDate }.orEmpty())
                    else ->
                        activeDates.addAll(extraDateMap[task.id].orEmpty())
                }

                ManagedRepeatTaskUi(
                    task = task,
                    section = ManageTaskSection.DAY_ALL,
                    actionKey = task.id,
                    metaDate = metaDate,
                    completionOutOf10 = if (completed) 10f else 0f,
                    bestStreak = if (completed) 1 else 0,
                    completedCount = if (completed) 1 else 0,
                    totalDays = activeDates.size.coerceAtLeast(1),
                    showAction = false,
                    showMenu = true,
                    originalDate = task.taskAddedDate,
                    activeDates = activeDates.toSet()
                )
            }
            .sortedByDescending { it.metaDate }
            .toList()
    }

    private fun buildAllRepeatList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>
    ): List<ManagedRepeatTaskUi> {
        val today = LocalDate.parse(todayString, dateFormatter)
        val completionMap = completions
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry ->
                entry.value.associateByNotNull(
                    keySelector = { runCatching { LocalDate.parse(it.date, dateFormatter) }.getOrNull() },
                    valueSelector = { it }
                )
            }
        val trackingVersionMap = trackingVersions
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
        val snapshotMap = snapshots
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry -> entry.value.associateBy { it.date } }

        return tasks
            .filter { it.taskAddedDate <= todayString }
            .groupBy { it.seriesId.ifBlank { it.id } }
            .mapNotNull { (seriesId, segments) ->
                val latest = segments.maxWithOrNull(
                    compareBy<TaskEntity> { it.taskAddedDate }
                        .thenBy { if (it.taskRemovedDate == null) 1 else 0 }
                        .thenBy { it.id }
                ) ?: return@mapNotNull null
                val orderedSegments = segments.sortedBy { it.taskAddedDate }
                val taskStart = runCatching { LocalDate.parse(orderedSegments.first().taskAddedDate, dateFormatter) }.getOrNull()
                    ?: return@mapNotNull null
                val taskIdByDate = linkedMapOf<LocalDate, String>()
                val completedDays = linkedSetOf<LocalDate>()
                val progressValues = mutableListOf<Int>()

                orderedSegments.forEach { task ->
                    val segmentStart = runCatching { LocalDate.parse(task.taskAddedDate, dateFormatter) }.getOrNull()
                        ?: return@forEach
                    val versions = trackingVersionMap[task.id].orEmpty()
                    val segmentEnd = if (task.inactiveReason != null && task.taskRemovedDate != null) {
                        runCatching { LocalDate.parse(task.taskRemovedDate, dateFormatter) }.getOrNull() ?: today
                    } else {
                        today
                    }
                    val scheduledDates = CommonMethods.scheduledDatesBetween(task, segmentStart, segmentEnd)
                    val completionsByDate = completionMap[task.id.trim().lowercase()].orEmpty()
                    val snapshotsByDate = snapshotMap[task.id.trim().lowercase()].orEmpty()

                    scheduledDates.forEach { date ->
                        taskIdByDate[date] = task.id
                        val snapshot = snapshotsByDate[date.toString()]
                        val progress = snapshot?.progressPercent ?: run {
                            val completion = completionsByDate[date]
                            if (
                                isCompletedDerived(
                                    task,
                                    completion,
                                    resolveTrackingSettings(task, date.toString(), versions)
                                )
                            ) 100 else 0
                        }
                        progressValues += progress
                        if ((snapshot?.isCompleted == true) || progress >= 100) {
                            completedDays += date
                        }
                    }
                }

                val totalDays = taskIdByDate.size
                val completedCount = completedDays.size
                val completionOutOf10 = if (progressValues.isNotEmpty()) {
                    (progressValues.sum().toFloat() / progressValues.size) / 10f
                } else {
                    0f
                }
                val bestStreak = CommonMethods.calculateBestStreak(
                    taskStart = taskIdByDate.keys.minOrNull() ?: taskStart,
                    completedDates = completedDays,
                    scheduledDates = taskIdByDate.keys
                )

                ManagedRepeatTaskUi(
                    task = latest,
                    section = ManageTaskSection.REPEAT_ALL,
                    actionKey = seriesId,
                    metaDate = taskStart,
                    completionOutOf10 = completionOutOf10,
                    bestStreak = bestStreak,
                    completedCount = completedCount,
                    totalDays = totalDays,
                    showAction = false,
                    showMenu = true
                )
            }
            .sortedByDescending { it.metaDate }
    }

    private fun buildActiveRepeatList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>
    ): List<ManagedRepeatTaskUi> {
        return buildAllRepeatList(tasks, completions, trackingVersions, snapshots)
            .filter { it.task.inactiveReason == null }
            .map { it.copy(section = ManageTaskSection.REPEAT_ACTIVE) }
    }

    private fun buildManagedList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>,
        reason: TaskInactiveReason
    ): List<ManagedRepeatTaskUi> {
        val completionMap = completions
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry ->
                entry.value.associateByNotNull(
                    keySelector = { runCatching { LocalDate.parse(it.date, dateFormatter) }.getOrNull() },
                    valueSelector = { it }
                )
            }
        val trackingVersionMap = trackingVersions
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
        val snapshotMap = snapshots
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry -> entry.value.associateBy { it.date } }

        return tasks
            .groupBy { it.seriesId.ifBlank { it.id } }
            .mapNotNull { (seriesId, segments) ->
                val latest = segments.maxWithOrNull(
                    compareBy<TaskEntity> { it.taskAddedDate }
                        .thenBy { if (it.taskRemovedDate == null) 1 else 0 }
                        .thenBy { if (it.inactiveReason == null) 1 else 0 }
                        .thenBy { it.id }
                ) ?: return@mapNotNull null
                val removedDate = latest.taskRemovedDate ?: return@mapNotNull null
                if (latest.inactiveReason != reason) return@mapNotNull null
                if (removedDate >= todayString) return@mapNotNull null
                val inactiveDate = runCatching { LocalDate.parse(removedDate) }.getOrNull()
                    ?: return@mapNotNull null
                val orderedSegments = segments.sortedBy { it.taskAddedDate }
                val taskIdByDate = linkedMapOf<LocalDate, String>()
                val completedDays = linkedSetOf<LocalDate>()
                val progressValues = mutableListOf<Int>()

                orderedSegments.forEach { task ->
                    val taskStart = runCatching { LocalDate.parse(task.taskAddedDate, dateFormatter) }.getOrNull()
                        ?: return@forEach
                    val versions = trackingVersionMap[task.id].orEmpty()
                    val scheduledDates = CommonMethods.scheduledDatesBetween(task, taskStart, inactiveDate)
                    val completionsByDate = completionMap[task.id.trim().lowercase()].orEmpty()
                    val snapshotsByDate = snapshotMap[task.id.trim().lowercase()].orEmpty()

                    scheduledDates.forEach { date ->
                        taskIdByDate[date] = task.id
                        val snapshot = snapshotsByDate[date.toString()]
                        val progress = snapshot?.progressPercent ?: run {
                            val completion = completionsByDate[date]
                            if (
                                isCompletedDerived(
                                    task,
                                    completion,
                                    resolveTrackingSettings(task, date.toString(), versions)
                                )
                            ) 100 else 0
                        }
                        progressValues += progress
                        if ((snapshot?.isCompleted == true) || progress >= 100) {
                            completedDays += date
                        }
                    }
                }

                val totalDays = taskIdByDate.size
                val completedCount = completedDays.size
                val completionOutOf10 = if (progressValues.isNotEmpty()) {
                    (progressValues.sum().toFloat() / progressValues.size) / 10f
                } else {
                    0f
                }
                val bestStreak = CommonMethods.calculateBestStreak(
                    taskStart = taskIdByDate.keys.minOrNull() ?: inactiveDate,
                    completedDates = completedDays,
                    scheduledDates = taskIdByDate.keys
                )

                ManagedRepeatTaskUi(
                    task = latest,
                    section = if (reason == TaskInactiveReason.PAUSED) {
                        ManageTaskSection.PAUSED
                    } else {
                        ManageTaskSection.ENDED
                    },
                    actionKey = seriesId,
                    metaDate = inactiveDate.plusDays(1),
                    completionOutOf10 = completionOutOf10,
                    bestStreak = bestStreak,
                    completedCount = completedCount,
                    totalDays = totalDays,
                    showMenu = true
                )
            }
            .sortedByDescending { it.metaDate }
    }

    private fun buildActiveDayList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>,
        extraDates: List<TaskExtraDateEntity>,
        children: List<UntilCompleteChildEntity>
    ): List<ManagedRepeatTaskUi> {
        val trackingVersionMap = trackingVersions
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
        val completionByTaskId = completions.groupBy { it.taskId.trim().lowercase() }
        val snapshotByTaskId = snapshots
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry -> entry.value.associateBy { it.date } }
        val extraDateMap = extraDates
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.map { it.date }.toSet() }
        val childrenByParent = children.groupBy { it.parentTaskId }

        return tasks
            .asSequence()
            .filter { it.taskType == TaskType.DAY || it.taskType == TaskType.UNTIL_COMPLETE }
            .filter { CommonMethods.isWithinTaskLifetime(it, todayString) }
            .mapNotNull { task ->
                val metaDate = runCatching { LocalDate.parse(task.taskAddedDate, dateFormatter) }.getOrNull()
                    ?: return@mapNotNull null

                val activeDates = linkedSetOf(task.taskAddedDate)
                when (task.taskType) {
                    TaskType.UNTIL_COMPLETE ->
                        activeDates.addAll(childrenByParent[task.id]?.map { it.taskAddedDate }.orEmpty())
                    else ->
                        activeDates.addAll(extraDateMap[task.id].orEmpty())
                }

                // Determine if this task is active on today's view
                val isActiveToday = when {
                    // Today is one of the scheduled dates
                    todayString in activeDates -> true
                    // DAY task with showUntilCompleted=true: active if any session is uncompleted
                    task.taskType == TaskType.DAY && task.showUntilCompleted -> {
                        activeDates.any { date ->
                            val snapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(date)
                            val completion = completionByTaskId[task.id.trim().lowercase()]
                                ?.firstOrNull { it.date == date }
                            val settings = resolveTrackingSettings(
                                task = task, date = date,
                                versions = trackingVersionMap[task.id].orEmpty()
                            )
                            val completed = snapshot?.isCompleted
                                ?: isCompletedDerived(task, completion, settings)
                            !completed
                        }
                    }
                    // UNTIL_COMPLETE task: active if parent or any child is uncompleted
                    task.taskType == TaskType.UNTIL_COMPLETE -> {
                        // Check parent completion on its original date
                        val parentSnapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(task.taskAddedDate)
                        val parentCompletion = completionByTaskId[task.id.trim().lowercase()]
                            ?.firstOrNull { it.date == task.taskAddedDate }
                        val parentSettings = resolveTrackingSettings(
                            task = task, date = task.taskAddedDate,
                            versions = trackingVersionMap[task.id].orEmpty()
                        )
                        val parentCompleted = parentSnapshot?.isCompleted
                            ?: isCompletedDerived(task, parentCompletion, parentSettings)
                        if (!parentCompleted) {
                            true
                        } else {
                            // Check children
                            childrenByParent[task.id]?.any { child ->
                                val childSnapshot = snapshotByTaskId[child.childTaskId.trim().lowercase()]?.get(child.taskAddedDate)
                                val childCompletion = completionByTaskId[child.childTaskId.trim().lowercase()]
                                    ?.firstOrNull { it.date == child.taskAddedDate }
                                val childSettings = resolveTrackingSettings(
                                    task = task.copy(id = child.childTaskId, taskAddedDate = child.taskAddedDate),
                                    date = child.taskAddedDate,
                                    versions = trackingVersionMap[task.id].orEmpty()
                                )
                                val childCompleted = childSnapshot?.isCompleted
                                    ?: isCompletedDerived(task, childCompletion, childSettings)
                                !childCompleted
                            } ?: false
                        }
                    }
                    else -> false
                }
                if (!isActiveToday) return@mapNotNull null

                val completion = completionByTaskId[task.id.trim().lowercase()]
                    ?.firstOrNull { it.date == task.taskAddedDate }
                val snapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(task.taskAddedDate)
                val settings = resolveTrackingSettings(
                    task = task, date = task.taskAddedDate,
                    versions = trackingVersionMap[task.id].orEmpty()
                )
                val completed = snapshot?.isCompleted
                    ?: isCompletedDerived(task, completion, settings)

                ManagedRepeatTaskUi(
                    task = task,
                    section = ManageTaskSection.DAY_ACTIVE,
                    actionKey = task.id,
                    metaDate = metaDate,
                    completionOutOf10 = if (completed) 10f else 0f,
                    bestStreak = if (completed) 1 else 0,
                    completedCount = if (completed) 1 else 0,
                    totalDays = activeDates.size.coerceAtLeast(1),
                    showAction = false,
                    showMenu = true,
                    originalDate = task.taskAddedDate,
                    activeDates = activeDates.toSet()
                )
            }
            .sortedByDescending { it.metaDate }
            .toList()
    }

    private fun buildMissedDayList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>,
        extraDates: List<TaskExtraDateEntity>,
        children: List<UntilCompleteChildEntity>
    ): List<ManagedRepeatTaskUi> {
        val trackingVersionMap = trackingVersions
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
        val completionByTaskId = completions.groupBy { it.taskId.trim().lowercase() }
        val snapshotByTaskId = snapshots
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry -> entry.value.associateBy { it.date } }
        val extraDateMap = extraDates
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.map { it.date }.toSet() }

        return tasks
            .asSequence()
            // Only DAY tasks with showUntilCompleted=false can be "missed"
            .filter { it.taskType == TaskType.DAY && !it.showUntilCompleted }
            .filter { CommonMethods.isWithinTaskLifetime(it, todayString) }
            .mapNotNull { task ->
                val metaDate = runCatching { LocalDate.parse(task.taskAddedDate, dateFormatter) }.getOrNull()
                    ?: return@mapNotNull null

                val activeDates = linkedSetOf(task.taskAddedDate)
                activeDates.addAll(extraDateMap[task.id].orEmpty())

                // If any date is today, the task is active, not missed
                if (todayString in activeDates) return@mapNotNull null

                // All dates must be in the past
                if (activeDates.any { it >= todayString }) return@mapNotNull null

                // Check if the task was completed on its original date
                val snapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(task.taskAddedDate)
                val completion = completionByTaskId[task.id.trim().lowercase()]
                    ?.firstOrNull { it.date == task.taskAddedDate }
                val settings = resolveTrackingSettings(
                    task = task, date = task.taskAddedDate,
                    versions = trackingVersionMap[task.id].orEmpty()
                )
                val completedOnOriginal = snapshot?.isCompleted
                    ?: isCompletedDerived(task, completion, settings)

                // Check if ALL active dates are completed — if so, nothing was missed
                val allCompleted = activeDates.all { date ->
                    val dateSnapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(date)
                    val dateCompletion = completionByTaskId[task.id.trim().lowercase()]
                        ?.firstOrNull { it.date == date }
                    val dateSettings = resolveTrackingSettings(
                        task = task, date = date,
                        versions = trackingVersionMap[task.id].orEmpty()
                    )
                    dateSnapshot?.isCompleted
                        ?: isCompletedDerived(task, dateCompletion, dateSettings)
                }
                if (allCompleted) return@mapNotNull null

                val completedCount = activeDates.count { date ->
                    val dateSnapshot = snapshotByTaskId[task.id.trim().lowercase()]?.get(date)
                    val dateCompletion = completionByTaskId[task.id.trim().lowercase()]
                        ?.firstOrNull { it.date == date }
                    val dateSettings = resolveTrackingSettings(
                        task = task, date = date,
                        versions = trackingVersionMap[task.id].orEmpty()
                    )
                    dateSnapshot?.isCompleted
                        ?: isCompletedDerived(task, dateCompletion, dateSettings)
                }

                ManagedRepeatTaskUi(
                    task = task,
                    section = ManageTaskSection.DAY_MISSED,
                    actionKey = task.id,
                    metaDate = metaDate,
                    completionOutOf10 = if (completedOnOriginal) 10f else 0f,
                    bestStreak = if (completedCount > 0) 1 else 0,
                    completedCount = completedCount,
                    totalDays = activeDates.size.coerceAtLeast(1),
                    showAction = false,
                    showMenu = true,
                    originalDate = task.taskAddedDate,
                    activeDates = activeDates.toSet()
                )
            }
            .sortedByDescending { it.metaDate }
            .toList()
    }

    private fun <T, K, V> Iterable<T>.associateByNotNull(
        keySelector: (T) -> K?,
        valueSelector: (T) -> V
    ): Map<K, V> {
        val destination = linkedMapOf<K, V>()
        for (element in this) {
            val key = keySelector(element) ?: continue
            destination[key] = valueSelector(element)
        }
        return destination
    }

    fun resumeTask(item: ManagedRepeatTaskUi) {
        runSeriesAction(item.actionKey) {
            resumeOrReactivateTask(item.task)
        }
    }

    fun restartTask(item: ManagedRepeatTaskUi) {
        runSeriesAction(item.actionKey) {
            resumeOrReactivateTask(item.task)
        }
    }


    fun addDayTaskForDate(item: ManagedRepeatTaskUi, date: String) {
        if (date == item.task.taskAddedDate) return
        runSeriesAction(item.actionKey) {
            repository.addTaskForExtraDate(item.task.id, date)
        }
    }

    fun removeDayTaskFromDate(item: ManagedRepeatTaskUi, date: String) {
        if (date == item.task.taskAddedDate) return
        runSeriesAction(item.actionKey) {
            repository.removeTaskFromExtraDate(item.task.id, date)
        }
    }

    fun addUntilCompleteForDate(item: ManagedRepeatTaskUi, date: String) {
        if (date == item.task.taskAddedDate) return
        runSeriesAction(item.actionKey) {
            repository.addUntilCompleteChild(item.task.id, date)
        }
    }

    fun removeUntilCompleteFromDate(item: ManagedRepeatTaskUi, date: String) {
        if (date == item.task.taskAddedDate) return
        runSeriesAction(item.actionKey) {
            repository.getUntilCompleteChildForParentDate(item.task.id, date)?.let { child ->
                repository.removeUntilCompleteChild(child)
            }
        }
    }

    fun deleteTask(item: ManagedRepeatTaskUi) {
        runSeriesAction(item.actionKey) {
            when (item.section) {
                ManageTaskSection.REPEAT_ALL -> repository.deleteRepeatSeries(item.task)
                else -> repository.deleteTask(item.task)
            }
        }
    }

    fun pauseRepeatTask(item: ManagedRepeatTaskUi, pauseDate: String) {
        runSeriesAction(item.actionKey) {
            val paused = item.task.copy(
                taskRemovedDate = pauseDate,
                inactiveReason = TaskInactiveReason.PAUSED
            )
            repository.updateTask(paused)
        }
    }

    private fun runSeriesAction(seriesId: String, action: suspend () -> Unit) {
        val busy = _busySeriesIds.value.orEmpty()
        if (seriesId in busy) return

        _busySeriesIds.value = busy + seriesId
        viewModelScope.launch {
            try {
                action()
            } finally {
                _busySeriesIds.postValue(_busySeriesIds.value.orEmpty() - seriesId)
            }
        }
    }

    private suspend fun resumeOrReactivateTask(task: TaskEntity): TaskEntity {
        val removedDate = task.taskRemovedDate?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }
        val today = LocalDate.parse(todayString, dateFormatter)

        val yesterday = today.minusDays(1)
        return if ((task.inactiveReason == TaskInactiveReason.PAUSED || task.inactiveReason == TaskInactiveReason.ENDED) &&
            (removedDate == today || removedDate == yesterday)) {
            val resumedTask = task.copy(
                taskRemovedDate = null,
                inactiveReason = null
            )
            repository.updateTask(resumedTask)
            resumedTask
        } else {
            createReactivatedSegment(task)
        }
    }

    private suspend fun createReactivatedSegment(task: TaskEntity): TaskEntity {
        val resumedTask = task.copy(
            id = UUID.randomUUID().toString(),
            seriesId = task.seriesId.ifBlank { task.id },
            taskAddedDate = todayString,
            taskRemovedDate = null,
            inactiveReason = null
        )

        repository.insertTask(resumedTask)

        val listIds = repository.getListIdsForTask(task.id)
        listIds.forEach { listId ->
            repository.addTaskToList(listId, resumedTask.id)
        }

        repository.upsertTaskTrackingVersion(
            TaskTrackingVersionEntity(
                taskId = resumedTask.id,
                effectiveFromDate = resumedTask.taskAddedDate,
                weightValue = resumedTask.weight.weight,
                dailyTargetCount = resumedTask.dailyTargetCount,
                targetDurationSeconds = resumedTask.targetDurationSeconds,
                checklistItemsJson = resumedTask.checklistItems
            )
        )

        return resumedTask
    }
}
