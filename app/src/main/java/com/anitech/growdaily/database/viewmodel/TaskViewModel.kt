package com.anitech.growdaily.database.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.data_class.TaskUiItem
import com.anitech.growdaily.data_class.TaskUiState
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.util.completionPercent
import com.anitech.growdaily.database.util.isCompletedDerived
import com.anitech.growdaily.database.util.resolveTrackingSettings
import com.anitech.growdaily.enum_class.DateMode
import com.anitech.growdaily.enum_class.TimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

class TaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    companion object {
        // Adjust this if you want smaller/larger window
        private const val RANGE_DAYS = 91
        private const val TAG = "TaskViewModel"
    }

    // -----------------------------
    // TASK LIST
    // -----------------------------
    val allTasks: LiveData<List<TaskEntity>> =
        repository.getAllTasksFlow().asLiveData()

    // -----------------------------
    // SELECTED LIST
    // -----------------------------
    private val _selectedListId = MutableLiveData<String?>(null)
    val selectedListId: LiveData<String?> = _selectedListId

    fun setSelectedList(listId: String?) {
        if (_selectedListId.value == listId) return
        _selectedListId.value = listId
    }

    val allLists = repository.getAllLists()

    private val listTaskIds = selectedListId.switchMap { listId ->
        if (listId == null) {
            MutableLiveData(emptyList())
        } else {
            repository.getTaskIdsForListFlow(listId).asLiveData()
        }
    }


    // -----------------------------
    // SELECTED DATE
    // -----------------------------
    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    fun ensureDate(date: String) {
        if (_selectedDate.value == null) {
            _selectedDate.value = date
        }
    }

    fun setDate(date: String) {
        if (_selectedDate.value == date) return
        _selectedDate.value = date
    }

    // -----------------------------
    // COMPLETION MAP
    // -----------------------------
    val completionMap: LiveData<Map<String, Map<String, Int>>> =
        repository.getAllCompletions().map { list ->
            list.groupBy { it.date }
                .mapValues { entry ->
                    entry.value.associate { it.taskId to it.count }
                }
        }

    val completionEntityMap: LiveData<Map<String, Map<String, TaskCompletionEntity>>> =
        repository.getAllCompletions().map { list ->
            list.groupBy { it.date }
                .mapValues { entry ->
                    entry.value.associateBy { it.taskId }
                }
        }

    val trackingVersionMap: LiveData<Map<String, List<TaskTrackingVersionEntity>>> =
        repository.getAllTaskTrackingVersionsFlow().asLiveData().map { list ->
            list.groupBy { it.taskId }
                .mapValues { entry ->
                    entry.value.sortedBy { it.effectiveFromDate }
                }
        }

    val taskDaySnapshotMap: LiveData<Map<String, Map<String, TaskDaySnapshotEntity>>> =
        repository.getAllTaskDaySnapshotsFlow().asLiveData().map { list ->
            list.groupBy { it.date }
                .mapValues { entry ->
                    entry.value.associateBy { it.taskId }
                }
        }

    // -----------------------------
    // UI STATE (optimized)
    // -----------------------------
    val taskUiState: LiveData<TaskUiState> =
        MediatorLiveData<TaskUiState>().apply {

            var tasks: List<TaskEntity>? = null
            var date: String? = null
            var completionAll: Map<String, Map<String, Int>>? = null
            var completionEntitiesAll: Map<String, Map<String, TaskCompletionEntity>>? = null
            var trackingVersionsAll: Map<String, List<TaskTrackingVersionEntity>>? = null
            var taskDaySnapshotsAll: Map<String, Map<String, TaskDaySnapshotEntity>>? = null
            var selectedListId: String? = null
            var taskIdsForSelectedList: List<String> = emptyList()


            // keep a reference to this MediatorLiveData so we can post from background
            val mediator = this

            fun rebuildStateAsync() {
                val t = tasks ?: return
                val d = date ?: return
                val completionMap = completionAll ?: return
                val completionEntityMap = completionEntitiesAll ?: return
                val trackingVersionsMap = trackingVersionsAll ?: return
                val snapshotMap = taskDaySnapshotsAll ?: emptyMap()

                // do heavy work off main
                viewModelScope.launch(Dispatchers.Default) {

                    val allVisibleTasks =
                        if (selectedListId == null) {
                            t
                        } else {
                            t.filter { it.id in taskIdsForSelectedList }
                        }

                    val scheduledTasksForSelectedDate = CommonMethods.filterTasks(allVisibleTasks, d)
                    val carryForwardDayTasks = allVisibleTasks.filter { task ->
                        task.taskType == com.anitech.growdaily.enum_class.TaskType.DAY &&
                            task.showUntilCompleted &&
                            task.taskAddedDate < d &&
                            !isCompletedDerived(
                                task = task,
                                completion = completionEntityMap[task.taskAddedDate]?.get(task.id),
                                settings = resolveTrackingSettings(
                                    task = task,
                                    date = task.taskAddedDate,
                                    versions = trackingVersionsMap[task.id].orEmpty()
                                )
                            )
                    }
                    val carryForwardRepeatTasks = allVisibleTasks.filter { task ->
                        task.taskType == com.anitech.growdaily.enum_class.TaskType.DAILY &&
                            task.showMissedOnGapDays &&
                            (task.repeatType != null && task.repeatType != com.anitech.growdaily.enum_class.RepeatType.DAILY) &&
                            CommonMethods.isWithinTaskLifetime(task, d) &&
                            !CommonMethods.isTaskActiveOnDate(task, d) &&
                            run {
                                val previousScheduledDate = CommonMethods.previousScheduledDate(task, d)
                                    ?: return@run false
                                !isCompletedDerived(
                                    task = task,
                                    completion = completionEntityMap[previousScheduledDate]?.get(task.id),
                                    settings = resolveTrackingSettings(
                                        task = task,
                                        date = previousScheduledDate,
                                        versions = trackingVersionsMap[task.id].orEmpty()
                                    )
                                )
                            }
                    }

                    val orderedTasks = CommonMethods.applySmartTimeOrder(
                        (scheduledTasksForSelectedDate + carryForwardDayTasks + carryForwardRepeatTasks)
                            .distinctBy { it.id }
                    )

                    val dateMode = CommonMethods.getDateMode(d)
                    val currentMinutes = if (dateMode == DateMode.TODAY)
                        CommonMethods.currentMinutes()
                    else null

                    val activeMinutes =
                        if (dateMode == DateMode.TODAY && currentMinutes != null) {
                            orderedTasks
                                .filter { it.isScheduled && it.scheduledMinutes != null }
                                .filter { it.scheduledMinutes!! <= currentMinutes }
                                .maxByOrNull { it.scheduledMinutes!! }
                                ?.scheduledMinutes
                        } else null

                    val isListFiltered = selectedListId != null


                    val uiItems = orderedTasks.map { task ->
                        val completionDate = if (
                            task.taskType == com.anitech.growdaily.enum_class.TaskType.DAY &&
                            task.showUntilCompleted &&
                            task.taskAddedDate < d
                        ) {
                            task.taskAddedDate
                        } else if (
                            task.taskType == com.anitech.growdaily.enum_class.TaskType.DAILY &&
                            task.showMissedOnGapDays &&
                            !CommonMethods.isTaskActiveOnDate(task, d)
                        ) {
                            CommonMethods.previousScheduledDate(task, d) ?: d
                        } else d
                        val completion = completionEntityMap[completionDate]?.get(task.id)
                        val snapshot = snapshotMap[completionDate]?.get(task.id)
                        val settings = resolveTrackingSettings(
                            task = task,
                            date = completionDate,
                            versions = trackingVersionsMap[task.id].orEmpty()
                        )
                        val completionCount = snapshot?.completionCount ?: (completion?.count ?: 0)
                        val completionPercent = snapshot?.progressPercent
                            ?: completionPercent(task, completion, settings)
                        val isCompleted = snapshot?.isCompleted
                            ?: isCompletedDerived(task, completion, settings)
                        val timeState = when {
                            !task.isScheduled || task.scheduledMinutes == null -> TimeState.NONE
                            completionDate != d -> TimeState.NONE
                            dateMode != DateMode.TODAY || currentMinutes == null -> TimeState.NONE
                            task.scheduledMinutes!! < currentMinutes -> TimeState.PAST
                            task.scheduledMinutes == currentMinutes -> TimeState.CURRENT
                            else -> TimeState.FUTURE
                        }

                        val currentStreak = if (task.taskType == com.anitech.growdaily.enum_class.TaskType.DAILY) {
                            val taskStart = LocalDate.parse(task.taskAddedDate)
                            val scheduledDates = CommonMethods.scheduledDatesBetween(
                                task = task,
                                startInclusive = taskStart,
                                endInclusive = LocalDate.now()
                            )
                            val completedDatesForTask = completionEntityMap.entries
                                .filter { (dateStr, taskMap) ->
                                    isCompletedDerived(
                                        task,
                                        taskMap[task.id],
                                        resolveTrackingSettings(
                                            task = task,
                                            date = dateStr,
                                            versions = trackingVersionsMap[task.id].orEmpty()
                                        )
                                    )
                                }
                                .mapNotNull { (dateStr, _) ->
                                    runCatching { LocalDate.parse(dateStr) }.getOrNull()
                                }
                                .filter { scheduledDates.contains(it) }
                                .toSet()
                            CommonMethods.calculateCurrentStreak(
                                taskStart,
                                completedDatesForTask,
                                scheduledDates
                            )
                        } else 0

                        TaskUiItem(
                            task = task,
                            isActive = task.scheduledMinutes != null &&
                                    task.scheduledMinutes == activeMinutes && !isListFiltered,
                            timeState = timeState,
                            dateMode = dateMode,
                            currentStreak = currentStreak,
                            completionCount = completionCount,
                            completionPercent = completionPercent,
                            trackingSettings = settings,
                            isCompleted = isCompleted,
                            isListFiltered = isListFiltered,
                            completionDate = completionDate,
                            pendingFromText = if (completionDate != d)
                                "Pending from ${CommonMethods.formatDate(completionDate)}"
                            else null
                        )
                    }


                    // basic scores
                    val dayScore =
                        calculateScoreForDateVersioned(
                            t,
                            d,
                            completionEntityMap,
                            trackingVersionsMap,
                            snapshotMap
                        )
                    val selectedDate = LocalDate.parse(d)

                    val weekStart =
                        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weekEnd = weekStart.plusDays(6)

                    val weekScore = calculateAggregateScoreVersioned(
                        t,
                        weekStart,
                        weekEnd,
                        completionEntityMap,
                        trackingVersionsMap,
                        snapshotMap
                    )

                    val monthStart = selectedDate.withDayOfMonth(1)
                    val monthEnd = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                    val monthScore = calculateAggregateScoreVersioned(
                        t,
                        monthStart,
                        monthEnd,
                        completionEntityMap,
                        trackingVersionsMap,
                        snapshotMap
                    )

                    // bar window
                    val half = RANGE_DAYS / 2L
                    val barCenterDate = LocalDate.now()
                    val start = barCenterDate.minusDays(half)
                    val end = barCenterDate.plusDays(half)

                    // build bar list
                    val barScores = ArrayList<DailyScore>(RANGE_DAYS)
                    var current = start
                    while (!current.isAfter(end)) {

                        val score = calculateScoreForDateVersioned(
                            t,
                            current.toString(),
                            completionEntityMap,
                            trackingVersionsMap,
                            snapshotMap
                        )

                        barScores.add(
                            DailyScore(
                                date = current.toString(),
                                dayText = current.dayOfMonth.toString(),
                                monthDayText = "${current.monthValue}/${current.dayOfMonth}",
                                score = score,
                                taskCount = 0 // you can compute taskCount similarly if needed
                            )
                        )
                        current = current.plusDays(1)
                    }

                    // publish on mediator
                    val ui = TaskUiState(
                        date = d,
                        tasks = uiItems,
                        completionForDate = completionMap[d] ?: emptyMap(),
                        dayScore = dayScore,
                        weekScore = weekScore,
                        monthScore = monthScore,
                        barScores = barScores,
                        dateMode = CommonMethods.getDateMode(d),
                        isEmpty = orderedTasks.isEmpty(),
                        selectedListId = selectedListId
                    )

                    mediator.postValue(ui)
                }
            }

            addSource(allTasks) {
                tasks = it
                rebuildStateAsync()
            }

            addSource(selectedDate) {
                date = it
                rebuildStateAsync()
            }

            addSource(completionMap) {
                completionAll = it
                rebuildStateAsync()
            }

            addSource(completionEntityMap) {
                completionEntitiesAll = it
                rebuildStateAsync()
            }

            addSource(trackingVersionMap) {
                trackingVersionsAll = it
                rebuildStateAsync()
            }

            addSource(taskDaySnapshotMap) {
                taskDaySnapshotsAll = it
                rebuildStateAsync()
            }

            addSource(this@TaskViewModel.selectedListId) {
                selectedListId = it
                rebuildStateAsync()
            }


            addSource(listTaskIds) {
                taskIdsForSelectedList = it
                rebuildStateAsync()
            }

        }

    private fun calculateScoreForDateVersioned(
        tasks: List<TaskEntity>,
        date: String,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>
    ): Float {
        val tasksForDate = CommonMethods.filterTasksForDate(tasks, date)
        if (tasksForDate.isEmpty()) return 0f

        var totalWeight = 0f
        var achievedWeight = 0f

        for (task in tasksForDate) {
            val snapshot = snapshotMap[date]?.get(task.id)
            val settings = resolveTrackingSettings(task, date, trackingVersionsMap[task.id].orEmpty())
            val taskWeight = settings.weightValue.toFloat()
            totalWeight += taskWeight

            val progressPercent = snapshot?.progressPercent
                ?: completionPercent(task, completionEntityMap[date]?.get(task.id), settings)

            val progressRatio = (progressPercent.coerceIn(0, 100) / 100f)
            achievedWeight += taskWeight * progressRatio
        }

        if (totalWeight == 0f) return 0f
        val rawScore = (achievedWeight / totalWeight) * 10f
        return ((rawScore * 10).roundToInt()) / 10f
    }

    private fun calculateAggregateScoreVersioned(
        tasks: List<TaskEntity>,
        startDate: LocalDate,
        endDate: LocalDate,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>
    ): Float {
        val dailyScores = mutableListOf<Float>()
        var currentDate = startDate
        val today = LocalDate.now()

        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isAfter(today)) {
                val dateString = currentDate.toString()
                val tasksForDate = CommonMethods.filterTasksForDate(tasks, dateString)
                if (tasksForDate.isNotEmpty()) {
                    val score = calculateScoreForDateVersioned(
                        tasks = tasks,
                        date = dateString,
                        completionEntityMap = completionEntityMap,
                        trackingVersionsMap = trackingVersionsMap,
                        snapshotMap = snapshotMap
                    )
                    dailyScores.add(score)
                }
            }
            currentDate = currentDate.plusDays(1)
        }

        if (dailyScores.isEmpty()) return 0f
        return ((dailyScores.sum() / dailyScores.size) * 10).roundToInt() / 10f
    }

    // -----------------------------
    // COMPLETION ACTIONS
    // -----------------------------
    fun incrementTaskCompletion(taskId: String, date: String) =
        viewModelScope.launch {
            repository.incrementCompletion(taskId, date)
        }

    fun decrementTaskCompletion(taskId: String, date: String) =
        viewModelScope.launch {
            repository.decrementCompletion(taskId, date)
        }

    fun changeTaskCompletionBy(taskId: String, date: String, delta: Int) =
        viewModelScope.launch {
            when {
                delta > 0 -> repeat(delta) { repository.incrementCompletion(taskId, date) }
                delta < 0 -> repeat(-delta) { repository.decrementCompletion(taskId, date) }
            }
        }


    fun resetTaskCompletion(taskId: String, date: String) =
        viewModelScope.launch {
            repository.resetCompletion(taskId, date)
        }

    fun addTimerDuration(taskId: String, date: String, seconds: Long) {
        viewModelScope.launch {
            runCatching {
                repository.addTimerDuration(taskId, date, seconds)
            }.onFailure { error ->
                Log.e(TAG, "Failed to add timer duration for $taskId on $date", error)
            }
        }
    }

    fun updateChecklist(taskId: String, date: String, checklistJson: String) {
        viewModelScope.launch {
            runCatching {
                repository.updateChecklistState(taskId, date, checklistJson)
            }.onFailure { error ->
                Log.e(TAG, "Failed to update checklist for $taskId on $date", error)
            }
        }
    }
}
