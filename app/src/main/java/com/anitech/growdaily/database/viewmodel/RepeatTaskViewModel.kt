package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.RepeatTaskUi
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.util.completionPercent
import com.anitech.growdaily.database.util.isCompletedDerived
import com.anitech.growdaily.database.util.resolveTrackingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException


class RepeatTaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Combines the two Room streams into one, debounces to collapse the
     * near-simultaneous first emissions, then maps to UI models on the
     * Default (CPU) dispatcher — never touching the main thread.
     *
     * Requirements:
     *  - repository.getAllTasksFlow()         → Flow<List<TaskEntity>>
     *  - repository.getAllCompletionsFlow()   → Flow<List<TaskCompletionEntity>>
     *
     * If your repository only exposes LiveData versions today, wrap them:
     *   myLiveData.asFlow()
     */
    val heatmapUiList: LiveData<List<RepeatTaskUi>> =
        combine(
            repository.getRepeatTasksFlow(),
            repository.getAllCompletionsFlow(),
            repository.getAllTaskTrackingVersionsFlow(),
            repository.getAllTaskDaySnapshotsFlow()
        ) { tasks, completions, trackingVersions, snapshots ->
            buildUiList(tasks, completions, trackingVersions, snapshots)
        }
            .debounce(50L)          // collapses the two near-simultaneous first Room emissions
            .flowOn(Dispatchers.Default)   // mapping runs off the main thread
            .asLiveData(viewModelScope.coroutineContext)

    // ── pure mapping — runs on Default dispatcher ─────────────────────────────

    private fun buildUiList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        snapshots: List<TaskDaySnapshotEntity>
    ): List<RepeatTaskUi> {
        if (tasks.isEmpty()) return emptyList()

        val completionMap = groupCompletions(completions)
        val trackingVersionMap = trackingVersions
            .groupBy { it.taskId }
            .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
        val snapshotMap = snapshots
            .groupBy { it.taskId.trim().lowercase() }
            .mapValues { entry -> entry.value.associateBy { it.date } }
        val today = LocalDate.now()

        return tasks
            .groupBy { it.seriesId.ifBlank { it.id } }
            .values
            .map { segments ->
                val orderedSegments = segments.sortedBy { it.taskAddedDate }
                val displayTask = orderedSegments.last()

                val taskIdByDate = linkedMapOf<LocalDate, String>()
                val completionByDate = linkedMapOf<LocalDate, TaskCompletionEntity>()
                val progressByDate = linkedMapOf<LocalDate, Int>()
                val completedDays = linkedSetOf<LocalDate>()
                val mergedVersions = linkedMapOf<String, TaskTrackingVersionEntity>()

                orderedSegments.forEach { task ->
                    val normalizedId = task.id.trim().lowercase()
                    val segmentCompletionByDate = completionMap[normalizedId] ?: emptyMap()
                    val snapshotsByDate = snapshotMap[normalizedId].orEmpty()
                    val versions = trackingVersionMap[task.id].orEmpty()
                    versions.forEach { version ->
                        mergedVersions["${version.taskId}:${version.effectiveFromDate}"] = version
                    }

                    val taskStart = try {
                        LocalDate.parse(task.taskAddedDate, DATE_FORMATTER)
                    } catch (e: Exception) {
                        today
                    }

                    val scheduledDates = CommonMethods.scheduledDatesBetween(task, taskStart, today)
                    scheduledDates.forEach { date ->
                        taskIdByDate[date] = task.id
                        val progress = snapshotsByDate[date.toString()]?.progressPercent ?: run {
                            val completion = segmentCompletionByDate[date]
                            if (isCompletedDerived(
                                    task,
                                    completion,
                                    resolveTrackingSettings(task, date.toString(), versions)
                                )
                            ) 100 else 0
                        }
                        progressByDate[date] = progress
                        segmentCompletionByDate[date]?.let { completionByDate[date] = it }
                        if ((snapshotsByDate[date.toString()]?.isCompleted ?: false) || progress >= 100) {
                            completedDays.add(date)
                        }
                    }
                }

                val taskStart = taskIdByDate.keys.minOrNull() ?: today
                val currentSegment = displayTask
                val currentSegmentStart = try {
                    LocalDate.parse(currentSegment.taskAddedDate, DATE_FORMATTER)
                } catch (e: Exception) {
                    taskStart
                }
                val currentSegmentScheduledDates =
                    CommonMethods.scheduledDatesBetween(currentSegment, currentSegmentStart, today)
                val totalDays = progressByDate.size
                val completedCount = completedDays.size
                val completionOutOf10 =
                    if (totalDays > 0) {
                        (progressByDate.values.sum().toFloat() / totalDays) / 10f
                    } else 0f
                val currentStreak = CommonMethods.calculateCurrentStreak(
                    currentSegmentStart,
                    completedDays,
                    currentSegmentScheduledDates
                )

                RepeatTaskUi(
                    task = displayTask,
                    seriesStartDate = taskStart,
                    completionByDate = completionByDate,
                    progressByDate = progressByDate,
                    taskIdByDate = taskIdByDate,
                    completedDays = completedDays,
                    trackingVersions = mergedVersions.values.toList(),
                    currentStreak = currentStreak,
                    completionOutOf10 = completionOutOf10,
                    completedCount = completedCount,
                    totalDays = totalDays
                )
            }
    }

    private fun groupCompletions(
        list: List<TaskCompletionEntity>
    ): Map<String, Map<LocalDate, TaskCompletionEntity>> {
        if (list.isEmpty()) return emptyMap()
        return list.mapNotNull { entity ->
            val normalizedId = entity.taskId.trim().lowercase()
            try {
                normalizedId to (LocalDate.parse(entity.date, DATE_FORMATTER) to entity)
            } catch (e: DateTimeParseException) {
                null
            }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, pairs) ->
                pairs.associate { (date, entity) -> date to entity }
            }
    }

    fun onHistoryCellClick(taskId: String, date: String) {
        viewModelScope.launch {
            val uiList = heatmapUiList.value ?: return@launch
            val parsedDate = runCatching { LocalDate.parse(date, DATE_FORMATTER) }.getOrNull()
                ?: return@launch
            val item = uiList.find {
                it.task.id == taskId || it.taskIdByDate[parsedDate] == taskId
            } ?: return@launch
            val resolvedTaskId = item.taskIdByDate[parsedDate] ?: taskId
            val settings = resolveTrackingSettings(item.task, date, item.trackingVersions)
            val target = settings.dailyTargetCount.coerceAtLeast(1)
            val count = item.completionByDate[parsedDate]?.count ?: 0

            if (target == 1) {
                if (count >= 1) repository.resetCompletion(resolvedTaskId, date)
                else repository.markCompleted(resolvedTaskId, date)
            } else {
                if (count < target) repository.incrementCompletion(resolvedTaskId, date)
                // multi-target reset is handled from fragment (bottom sheet)
            }
        }
    }

    fun changeTaskCompletionBy(taskId: String, date: String, delta: Int) {
        viewModelScope.launch {
            when {
                delta > 0 -> repeat(delta) { repository.incrementCompletion(taskId, date) }
                delta < 0 -> repeat(-delta) { repository.decrementCompletion(taskId, date) }
            }
        }
    }


    fun addTimerDuration(taskId: String, date: String, seconds: Long) {
        viewModelScope.launch {
            runCatching {
                repository.addTimerDuration(taskId, date, seconds)
            }.onFailure { error ->
                Log.e("RepeatTaskViewModel", "Failed to add timer duration for $taskId on $date", error)
            }
        }
    }

    /** Persists updated checklist JSON for [taskId] on [date]. */
    fun updateChecklist(taskId: String, date: String, checklistJson: String) {
        viewModelScope.launch {
            runCatching {
                repository.updateChecklistState(taskId, date, checklistJson)
            }.onFailure { error ->
                Log.e("RepeatTaskViewModel", "Failed to update checklist for $taskId on $date", error)
            }
        }
    }
}
