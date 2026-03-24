package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.RepeatTaskUi
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import android.util.Log
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit


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
            repository.getAllCompletionsFlow()
        ) { tasks, completions ->
            buildUiList(tasks, completions)
        }
            .debounce(50L)          // collapses the two near-simultaneous first Room emissions
            .flowOn(Dispatchers.Default)   // mapping runs off the main thread
            .asLiveData(viewModelScope.coroutineContext)

    // ── pure mapping — runs on Default dispatcher ─────────────────────────────

    private fun buildUiList(
        tasks: List<TaskEntity>,
        completions: List<TaskCompletionEntity>
    ): List<RepeatTaskUi> {
        if (tasks.isEmpty()) return emptyList()

        val completionMap = groupCompletions(completions)
        val today = LocalDate.now()

        return tasks.map { task ->
            val normalizedId  = task.id.trim().lowercase()
            val completedDates = completionMap[normalizedId] ?: emptyMap()

            val taskStart = try {
                LocalDate.parse(task.taskAddedDate, DATE_FORMATTER)
            } catch (e: Exception) {
                today
            }

            val totalDays      = ChronoUnit.DAYS.between(taskStart, today).toInt() + 1
            val completedCount = completedDates.count { (date, count) ->
                count >= 1 && !date.isBefore(taskStart) && !date.isAfter(today)
            }
            val completionOutOf10 =
                if (totalDays > 0) (completedCount.toFloat() / totalDays) * 10f else 0f
            val currentStreak = CommonMethods.calculateCurrentStreak(taskStart, completedDates.keys)

            RepeatTaskUi(
                task              = task,
                completedDates    = completedDates,
                currentStreak     = currentStreak,
                completionOutOf10 = completionOutOf10,
                completedCount    = completedCount,
                totalDays         = totalDays
            )
        }
    }

    private fun groupCompletions(
        list: List<TaskCompletionEntity>
    ): Map<String, Map<LocalDate, Int>> {       // was Map<String, Set<LocalDate>>
        if (list.isEmpty()) return emptyMap()
        return list.mapNotNull { entity ->
            val normalizedId = entity.taskId.trim().lowercase()
            try {
                Triple(normalizedId, LocalDate.parse(entity.date, DATE_FORMATTER), entity.count)
            } catch (e: DateTimeParseException) {
                null
            }
        }
            .groupBy({ it.first })
            .mapValues { (_, triples) ->
                triples.associate { it.second to it.third }  // LocalDate -> count
            }
    }

    fun onHistoryCellClick(taskId: String, date: String) {
        viewModelScope.launch {
            val uiList = heatmapUiList.value ?: return@launch
            val item = uiList.find { it.task.id == taskId } ?: return@launch
            val target = item.task.dailyTargetCount.coerceAtLeast(1)

            val parsedDate = LocalDate.parse(date, DATE_FORMATTER)
            val count = item.completedDates[parsedDate] ?: 0

            if (target == 1) {
                if (count >= 1) repository.resetCompletion(taskId, date)
                else repository.markCompleted(taskId, date)
            } else {
                if (count < target) repository.incrementCompletion(taskId, date)
                // multi-target reset is handled from fragment (bottom sheet)
            }
        }
    }
}