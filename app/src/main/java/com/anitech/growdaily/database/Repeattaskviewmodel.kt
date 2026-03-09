package com.anitech.growdaily.database


import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.RepeatTaskUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import android.util.Log

class RepeatTaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val heatmapUiList: LiveData<List<RepeatTaskUi>> =
        MediatorLiveData<List<RepeatTaskUi>>().apply {

            var latestTasks: List<TaskEntity>? = null
            var latestCompletions: List<TaskCompletionEntity>? = null

            fun build() {
                viewModelScope.launch {
                    val result = withContext(Dispatchers.Default) {
                        val tasks = latestTasks ?: return@withContext emptyList()
                        val completions = latestCompletions ?: emptyList()

                        val completionMap = groupCompletions(completions)
                        val today = LocalDate.now()

                        tasks.map { task ->
                            val normalizedId = task.id.trim().lowercase()
                            val completedDates = completionMap[normalizedId] ?: emptySet()
                            val taskStart = try {
                                LocalDate.parse(task.taskAddedDate, DATE_FORMATTER)
                            } catch (e: Exception) {
                                today
                            }

                            val totalDays = ChronoUnit.DAYS.between(taskStart, today).toInt() + 1
                            val completedCount = completedDates.count {
                                !it.isBefore(taskStart) && !it.isAfter(today)
                            }

                            // Score out of 10 (e.g. 7.3)
                            val completionOutOf10 =
                                if (totalDays > 0) (completedCount.toFloat() / totalDays) * 10f
                                else 0f

                            val currentStreak =
                                CommonMethods.calculateCurrentStreak(taskStart, completedDates)

                            RepeatTaskUi(
                                task = task,
                                completedDates = completedDates,
                                currentStreak = currentStreak,
                                completionOutOf10 = completionOutOf10,
                                completedCount = completedCount,
                                totalDays = totalDays
                            )
                        }
                    }
                    value = result
                }
            }

            addSource(repository.getAllDailyTasks()) { tasks ->
                latestTasks = tasks
                build()
            }

            addSource(repository.getAllCompletionsTaskData()) { completions ->
                latestCompletions = completions
                build()
            }
        }

    private fun groupCompletions(
        list: List<TaskCompletionEntity>
    ): Map<String, Set<LocalDate>> {
        if (list.isEmpty()) return emptyMap()
        return list.mapNotNull { entity ->
            val normalizedId = entity.taskId.trim().lowercase()
            try {
                val parsed = LocalDate.parse(entity.date, DATE_FORMATTER)
                normalizedId to parsed
            } catch (e: DateTimeParseException) {
                Log.w("RepeatTaskVM", "Bad date='${entity.date}' taskId='$normalizedId'")
                null
            }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    }
}