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
import com.anitech.growdaily.data_class.BarTimelineState
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.data_class.TaskUiItem
import com.anitech.growdaily.data_class.TaskUiState
import com.anitech.growdaily.data_class.UntilCompleteChildEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.util.completionPercent
import com.anitech.growdaily.database.util.isCompletedDerived
import com.anitech.growdaily.database.util.resolveTrackingSettings
import com.anitech.growdaily.enum_class.DateMode
import com.anitech.growdaily.enum_class.TaskType
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
        // Professional Rolling Window Strategy
        private const val INITIAL_BAR_PAST_DAYS = 91L // ~3 months
        private const val INITIAL_BAR_FUTURE_DAYS = 91L // ~3 months
        private const val BAR_CHUNK_DAYS = 56L // 8 weeks per fetch
        private const val MAX_BAR_WINDOW_DAYS = 365L // Keep ~1 year max in memory to prevent OOM
        private const val TAG = "TaskViewModel"
    }

    private var latestTasks: List<TaskEntity>? = null
    private var latestDate: String? = null
    private var latestCompletionAll: Map<String, Map<String, Int>>? = null
    private var latestCompletionEntitiesAll: Map<String, Map<String, TaskCompletionEntity>>? = null
    private var latestTrackingVersionsAll: Map<String, List<TaskTrackingVersionEntity>>? = null
    private var latestTaskDaySnapshotsAll: Map<String, Map<String, TaskDaySnapshotEntity>>? = null
    private var latestTaskExtraDatesAll: Map<String, Set<String>>? = null
    private var latestUntilCompleteChildrenAll: List<UntilCompleteChildEntity> = emptyList()
    private var latestSelectedListId: String? = null
    private var latestTaskIdsForSelectedList: List<String> = emptyList()

    private val barScoreCache = linkedMapOf<String, DailyScore>()
    private var barWindowStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(INITIAL_BAR_PAST_DAYS)
    private var barWindowEnd: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6).plusDays(INITIAL_BAR_FUTURE_DAYS)
    private var isLoadingBarPast = false
    private var isLoadingBarFuture = false

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

    val taskExtraDateMap: LiveData<Map<String, Set<String>>> =
        repository.getAllTaskExtraDatesFlow().asLiveData().map { list ->
            list.groupBy { it.taskId }
                .mapValues { entry -> entry.value.map { it.date }.toSet() }
        }

    val untilCompleteChildren = repository.getAllUntilCompleteChildrenFlow().asLiveData()

    // -----------------------------
    // UI STATE (optimized)
    // -----------------------------
    val taskUiState: LiveData<TaskUiState> =
        MediatorLiveData<TaskUiState>().apply {
            // keep a reference to this MediatorLiveData so we can post from background
            val mediator = this

            fun rebuildStateAsync() {
                val t = latestTasks ?: return
                val d = latestDate ?: return
                val completionMap = latestCompletionAll ?: return
                val completionEntityMap = latestCompletionEntitiesAll ?: return
                val trackingVersionsMap = latestTrackingVersionsAll ?: return
                val snapshotMap = latestTaskDaySnapshotsAll ?: emptyMap()
                    val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
                    val expandedTasks = expandUntilCompleteTasks(t, latestUntilCompleteChildrenAll)
                    val childParentMap = latestUntilCompleteChildrenAll.associate { it.childTaskId to it.parentTaskId }
                    val parentTaskMap = t.associateBy { it.id }

                // do heavy work off main
                viewModelScope.launch(Dispatchers.Default) {

                    val allVisibleTasks =
                        if (latestSelectedListId == null) {
                            expandedTasks
                        } else {
                            expandedTasks.filter { it.id in latestTaskIdsForSelectedList || childParentMap[it.id] in latestTaskIdsForSelectedList }
                        }

                    val untilCompleteStates = allVisibleTasks
                        .filter { it.taskType == TaskType.UNTIL_COMPLETE }
                        .associate { task ->
                            task.id to resolveUntilCompleteState(
                                task = task,
                                selectedDate = d,
                                completionEntityMap = completionEntityMap,
                                trackingVersionsMap = trackingVersionsMap,
                                versionOwnerId = childParentMap[task.id] ?: task.id
                            )
                        }
                    val scheduledTasksForSelectedDate = CommonMethods.filterTasks(
                        allVisibleTasks.filter { it.taskType != TaskType.UNTIL_COMPLETE },
                        d,
                        extraDateMap
                    ) + allVisibleTasks.filter { task ->
                        task.taskType == TaskType.UNTIL_COMPLETE &&
                            untilCompleteStates[task.id]?.isVisible == true
                    }
                    val carryForwardDayTasks = allVisibleTasks.filter { task ->
                        task.taskType == TaskType.DAY &&
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
                        task.taskType == TaskType.DAILY &&
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

                    val isListFiltered = latestSelectedListId != null


                    val uiItems = orderedTasks.map { task ->
                        val untilCompleteState = untilCompleteStates[task.id]
                        val completionDate = if (
                            task.taskType == TaskType.DAY &&
                            task.showUntilCompleted &&
                            task.taskAddedDate < d
                        ) {
                            task.taskAddedDate
                        } else if (
                            task.taskType == TaskType.DAILY &&
                            task.showMissedOnGapDays &&
                            !CommonMethods.isTaskActiveOnDate(task, d)
                        ) {
                            CommonMethods.previousScheduledDate(task, d) ?: d
                        } else if (task.taskType == TaskType.UNTIL_COMPLETE) {
                            untilCompleteState?.displayCompletionDate ?: d
                        } else d
                        val completion = completionEntityMap[completionDate]?.get(task.id)
                        val snapshot = snapshotMap[completionDate]?.get(task.id)
                        val trackingOwnerId = childParentMap[task.id] ?: task.id
                        val settings = resolveTrackingSettings(
                            task = task,
                            date = completionDate,
                            versions = trackingVersionsMap[trackingOwnerId].orEmpty()
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
                            task.scheduledMinutes < currentMinutes -> TimeState.PAST
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
                            pendingFromDate = when {
                                task.taskType == com.anitech.growdaily.enum_class.TaskType.UNTIL_COMPLETE &&
                                    task.taskAddedDate != d &&
                                    untilCompleteState?.isVisible == true -> task.taskAddedDate
                                completionDate != d -> completionDate
                                else -> null
                            },
                            sourceTask = childParentMap[task.id]?.let { parentTaskMap[it] }
                        )
                    }


                    // basic scores
                    val dayScore =
                        calculateScoreForDateVersioned(
                            t,
                            // score against effective task occurrences, not only base tasks
                            // so UNTIL_COMPLETE children count on their own dates
                            d,
                            completionEntityMap,
                            trackingVersionsMap,
                            snapshotMap,
                            extraDateMap,
                            expandedTasksOverride = expandedTasks
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
                        snapshotMap,
                        extraDateMap,
                        expandedTasksOverride = expandedTasks
                    )

                    val monthStart = selectedDate.withDayOfMonth(1)
                    val monthEnd = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                    val monthScore = calculateAggregateScoreVersioned(
                        t,
                        monthStart,
                        monthEnd,
                        completionEntityMap,
                        trackingVersionsMap,
                        snapshotMap,
                        extraDateMap,
                        expandedTasksOverride = expandedTasks
                    )

                    // publish on mediator
                    val ui = TaskUiState(
                        date = d,
                        tasks = uiItems,
                        completionForDate = completionMap[d] ?: emptyMap(),
                        dayScore = dayScore,
                        weekScore = weekScore,
                        monthScore = monthScore,
                        barScores = emptyList(),
                        dateMode = CommonMethods.getDateMode(d),
                        isEmpty = orderedTasks.isEmpty(),
                        selectedListId = latestSelectedListId
                    )

                    mediator.postValue(ui)
                }
            }

            addSource(allTasks) {
                latestTasks = it
                rebuildStateAsync()
            }

            addSource(selectedDate) {
                latestDate = it
                rebuildStateAsync()
            }

            addSource(completionMap) {
                latestCompletionAll = it
                rebuildStateAsync()
            }

            addSource(completionEntityMap) {
                latestCompletionEntitiesAll = it
                rebuildStateAsync()
            }

            addSource(trackingVersionMap) {
                latestTrackingVersionsAll = it
                rebuildStateAsync()
            }

            addSource(taskDaySnapshotMap) {
                latestTaskDaySnapshotsAll = it
                rebuildStateAsync()
            }

            addSource(taskExtraDateMap) {
                latestTaskExtraDatesAll = it
                rebuildStateAsync()
            }

            addSource(untilCompleteChildren) {
                latestUntilCompleteChildrenAll = it
                rebuildStateAsync()
            }

            addSource(this@TaskViewModel.selectedListId) {
                latestSelectedListId = it
                rebuildStateAsync()
            }


            addSource(listTaskIds) {
                latestTaskIdsForSelectedList = it
                rebuildStateAsync()
            }

        }

    private val _barTimelineState = MediatorLiveData<BarTimelineState>().apply {

        fun rebuildBarStateAsync() {
            val tasks = latestTasks ?: return
            val completionEntityMap = latestCompletionEntitiesAll ?: return
            val trackingVersionsMap = latestTrackingVersionsAll ?: return
            val snapshotMap = latestTaskDaySnapshotsAll ?: emptyMap()
            val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
            val expandedTasks = expandUntilCompleteTasks(tasks, latestUntilCompleteChildrenAll)
            val selectedDate = latestDate ?: CommonMethods.getTodayDate()

            viewModelScope.launch(Dispatchers.Default) {
                val scores = buildBarWindowScores(
                    tasks = expandedTasks,
                    start = barWindowStart,
                    end = barWindowEnd,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionsMap,
                    snapshotMap = snapshotMap,
                    extraDateMap = extraDateMap
                )

                postValue(
                    BarTimelineState(
                        scores = scores,
                        selectedDate = selectedDate,
                        isLoadingPast = isLoadingBarPast,
                        isLoadingFuture = isLoadingBarFuture
                    )
                )
            }
        }

        addSource(allTasks) {
            latestTasks = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(selectedDate) {
            latestDate = it
            rebuildBarStateAsync()
        }

        addSource(completionEntityMap) {
            latestCompletionEntitiesAll = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(trackingVersionMap) {
            latestTrackingVersionsAll = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(taskDaySnapshotMap) {
            latestTaskDaySnapshotsAll = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(taskExtraDateMap) {
            latestTaskExtraDatesAll = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }
        addSource(untilCompleteChildren) {
            latestUntilCompleteChildrenAll = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }
    }
    val barTimelineState: LiveData<BarTimelineState> = _barTimelineState

    fun resetBarWindowToToday() {
        jumpToDate(LocalDate.now())
    }

    fun jumpToDate(target: LocalDate) {
        val weekStart = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        barWindowStart = weekStart.minusDays(INITIAL_BAR_PAST_DAYS)
        barWindowEnd = weekStart.plusDays(6).plusDays(INITIAL_BAR_FUTURE_DAYS)
        isLoadingBarPast = false
        isLoadingBarFuture = false
        barScoreCache.clear() // Clear cache to keep memory usage flat
        rebuildBarTimelineState()
    }

    fun loadMoreBarPast() {
        if (isLoadingBarPast) return
        val tasks = latestTasks ?: return
        val completionEntityMap = latestCompletionEntitiesAll ?: return
        val trackingVersionsMap = latestTrackingVersionsAll ?: return
        val snapshotMap = latestTaskDaySnapshotsAll ?: emptyMap()
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        val expandedTasks = expandUntilCompleteTasks(tasks, latestUntilCompleteChildrenAll)
        val selectedDate = latestDate ?: CommonMethods.getTodayDate()

        isLoadingBarPast = true
        emitLoadingBarState(selectedDate)

        val newStart = barWindowStart.minusDays(BAR_CHUNK_DAYS)
        val oldStart = barWindowStart
        
        // Rolling Window: If we exceed MAX_BAR_WINDOW_DAYS, trim the future
        if (java.time.temporal.ChronoUnit.DAYS.between(newStart, barWindowEnd) > MAX_BAR_WINDOW_DAYS) {
            val trimDays = BAR_CHUNK_DAYS
            val newEnd = barWindowEnd.minusDays(trimDays)
            // Remove trimmed days from cache
            var cursor = newEnd.plusDays(1)
            while (!cursor.isAfter(barWindowEnd)) {
                barScoreCache.remove(cursor.toString())
                cursor = cursor.plusDays(1)
            }
            barWindowEnd = newEnd
        }

        viewModelScope.launch(Dispatchers.Default) {
            var cursor = newStart
            while (cursor.isBefore(oldStart)) {
                getOrBuildDailyScore(
                    tasks = expandedTasks,
                    date = cursor,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionsMap,
                    snapshotMap = snapshotMap,
                    extraDateMap = extraDateMap
                )
                cursor = cursor.plusDays(1)
            }
            barWindowStart = newStart
            isLoadingBarPast = false
            emitCurrentBarState(selectedDate)
        }
    }

    fun loadMoreBarFuture() {
        if (isLoadingBarFuture) return
        val tasks = latestTasks ?: return
        val completionEntityMap = latestCompletionEntitiesAll ?: return
        val trackingVersionsMap = latestTrackingVersionsAll ?: return
        val snapshotMap = latestTaskDaySnapshotsAll ?: emptyMap()
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        val expandedTasks = expandUntilCompleteTasks(tasks, latestUntilCompleteChildrenAll)
        val selectedDate = latestDate ?: CommonMethods.getTodayDate()

        isLoadingBarFuture = true
        emitLoadingBarState(selectedDate)

        val newEnd = barWindowEnd.plusDays(BAR_CHUNK_DAYS)
        val oldEnd = barWindowEnd

        // Rolling Window: If we exceed MAX_BAR_WINDOW_DAYS, trim the past
        if (java.time.temporal.ChronoUnit.DAYS.between(barWindowStart, newEnd) > MAX_BAR_WINDOW_DAYS) {
            val trimDays = BAR_CHUNK_DAYS
            val newStart = barWindowStart.plusDays(trimDays)
            // Remove trimmed days from cache
            var cursor = barWindowStart
            while (cursor.isBefore(newStart)) {
                barScoreCache.remove(cursor.toString())
                cursor = cursor.plusDays(1)
            }
            barWindowStart = newStart
        }

        viewModelScope.launch(Dispatchers.Default) {
            var cursor = oldEnd.plusDays(1)
            while (!cursor.isAfter(newEnd)) {
                getOrBuildDailyScore(
                    tasks = expandedTasks,
                    date = cursor,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionsMap,
                    snapshotMap = snapshotMap,
                    extraDateMap = extraDateMap
                )
                cursor = cursor.plusDays(1)
            }
            barWindowEnd = newEnd
            isLoadingBarFuture = false
            emitCurrentBarState(selectedDate)
        }
    }

    private fun rebuildBarTimelineState() {
        val tasks = latestTasks ?: return
        val completionEntityMap = latestCompletionEntitiesAll ?: return
        val trackingVersionsMap = latestTrackingVersionsAll ?: return
        val snapshotMap = latestTaskDaySnapshotsAll ?: emptyMap()
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        val expandedTasks = expandUntilCompleteTasks(tasks, latestUntilCompleteChildrenAll)
        val selectedDate = latestDate ?: CommonMethods.getTodayDate()

        viewModelScope.launch(Dispatchers.Default) {
            emitComputedBarState(
                tasks = expandedTasks,
                completionEntityMap = completionEntityMap,
                trackingVersionsMap = trackingVersionsMap,
                snapshotMap = snapshotMap,
                extraDateMap = extraDateMap,
                selectedDate = selectedDate
            )
        }
    }

    private fun emitLoadingBarState(selectedDate: String) {
        _barTimelineState.value = (_barTimelineState.value ?: BarTimelineState(selectedDate = selectedDate)).copy(
            selectedDate = selectedDate,
            isLoadingPast = isLoadingBarPast,
            isLoadingFuture = isLoadingBarFuture
        )
    }

    private suspend fun emitCurrentBarState(selectedDate: String) {
        val tasks = latestTasks ?: return
        val completionEntityMap = latestCompletionEntitiesAll ?: return
        val trackingVersionsMap = latestTrackingVersionsAll ?: return
        val snapshotMap = latestTaskDaySnapshotsAll ?: emptyMap()
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        val expandedTasks = expandUntilCompleteTasks(tasks, latestUntilCompleteChildrenAll)
        emitComputedBarState(
            tasks = expandedTasks,
            completionEntityMap = completionEntityMap,
            trackingVersionsMap = trackingVersionsMap,
            snapshotMap = snapshotMap,
            extraDateMap = extraDateMap,
            selectedDate = selectedDate
        )
    }

    private suspend fun emitComputedBarState(
        tasks: List<TaskEntity>,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>,
        extraDateMap: Map<String, Set<String>>,
        selectedDate: String
    ) {
        val scores = buildBarWindowScores(
            tasks = tasks,
            start = barWindowStart,
            end = barWindowEnd,
            completionEntityMap = completionEntityMap,
            trackingVersionsMap = trackingVersionsMap,
            snapshotMap = snapshotMap,
            extraDateMap = extraDateMap
        )
        _barTimelineState.postValue(
            BarTimelineState(
                scores = scores,
                selectedDate = selectedDate,
                isLoadingPast = isLoadingBarPast,
                isLoadingFuture = isLoadingBarFuture
            )
        )
    }

    private fun buildBarWindowScores(
        tasks: List<TaskEntity>,
        start: LocalDate,
        end: LocalDate,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>,
        extraDateMap: Map<String, Set<String>>
    ): List<DailyScore> {
        val scores = ArrayList<DailyScore>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            scores.add(
                getOrBuildDailyScore(
                    tasks = tasks,
                    date = cursor,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionsMap,
                    snapshotMap = snapshotMap,
                    extraDateMap = extraDateMap
                )
            )
            cursor = cursor.plusDays(1)
        }
        return scores
    }

    private fun getOrBuildDailyScore(
        tasks: List<TaskEntity>,
        date: LocalDate,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>,
        extraDateMap: Map<String, Set<String>>
    ): DailyScore {
        val key = date.toString()
        return barScoreCache.getOrPut(key) {
            val score = calculateScoreForDateVersioned(
                tasks = tasks,
                date = key,
                completionEntityMap = completionEntityMap,
                trackingVersionsMap = trackingVersionsMap,
                snapshotMap = snapshotMap,
                extraDateMap = extraDateMap
            )
            val taskCount = CommonMethods.filterTasksForDate(tasks, key, extraDateMap).size
            DailyScore(
                date = key,
                dayText = date.dayOfMonth.toString(),
                monthDayText = "${date.monthValue}/${date.dayOfMonth}",
                score = score,
                taskCount = taskCount
            )
        }
    }

    private fun calculateScoreForDateVersioned(
        tasks: List<TaskEntity>,
        date: String,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>,
        extraDateMap: Map<String, Set<String>>,
        expandedTasksOverride: List<TaskEntity>? = null
    ): Float {
        val tasksForDate = CommonMethods.filterTasksForDate(expandedTasksOverride ?: tasks, date, extraDateMap)
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
        snapshotMap: Map<String, Map<String, TaskDaySnapshotEntity>>,
        extraDateMap: Map<String, Set<String>>,
        expandedTasksOverride: List<TaskEntity>? = null
    ): Float {
        val dailyScores = mutableListOf<Float>()
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            val dateString = currentDate.toString()
            val effectiveTasks = expandedTasksOverride ?: tasks
            val tasksForDate = CommonMethods.filterTasksForDate(effectiveTasks, dateString, extraDateMap)
            if (tasksForDate.isNotEmpty()) {
                val score = calculateScoreForDateVersioned(
                    tasks = effectiveTasks,
                    date = dateString,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionsMap,
                    snapshotMap = snapshotMap,
                    extraDateMap = extraDateMap
                )
                dailyScores.add(score)
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

    private fun expandUntilCompleteTasks(
        baseTasks: List<TaskEntity>,
        children: List<UntilCompleteChildEntity>
    ): List<TaskEntity> {
        if (children.isEmpty()) return baseTasks
        val parentsById = baseTasks.associateBy { it.id }
        val syntheticChildren = children.mapNotNull { child ->
            val parent = parentsById[child.parentTaskId] ?: return@mapNotNull null
            if (parent.taskType != com.anitech.growdaily.enum_class.TaskType.UNTIL_COMPLETE) return@mapNotNull null
            parent.copy(
                id = child.childTaskId,
                taskAddedDate = child.taskAddedDate,
                taskRemovedDate = null,
                inactiveReason = null
            )
        }
        return baseTasks + syntheticChildren
    }

    private data class UntilCompleteDisplayState(
        val isVisible: Boolean,
        val displayCompletionDate: String
    )

    private fun resolveUntilCompleteState(
        task: TaskEntity,
        selectedDate: String,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        versionOwnerId: String
    ): UntilCompleteDisplayState {
        if (task.taskAddedDate > selectedDate) {
            return UntilCompleteDisplayState(
                isVisible = false,
                displayCompletionDate = selectedDate
            )
        }
        if (task.taskRemovedDate != null && task.taskRemovedDate < selectedDate) {
            return UntilCompleteDisplayState(
                isVisible = false,
                displayCompletionDate = selectedDate
            )
        }

        // If this is a synthetic child, it's a one-day session.
        // It should only look at its own date, not the parent's history.
        if (task.id != versionOwnerId) {
            return UntilCompleteDisplayState(
                isVisible = true,
                displayCompletionDate = selectedDate
            )
        }

        var cursor = LocalDate.parse(task.taskAddedDate)
        val endDate = LocalDate.parse(selectedDate)
        while (!cursor.isAfter(endDate)) {
            val dateString = cursor.toString()
            val completion = completionEntityMap[dateString]?.get(task.id)
            val settings = resolveTrackingSettings(
                task = task,
                date = dateString,
                versions = trackingVersionsMap[versionOwnerId].orEmpty()
            )
            if (isCompletedDerived(task, completion, settings)) {
                return UntilCompleteDisplayState(
                    isVisible = dateString == selectedDate,
                    displayCompletionDate = dateString
                )
            }
            cursor = cursor.plusDays(1)
        }

        return UntilCompleteDisplayState(
            isVisible = true,
            displayCompletionDate = selectedDate
        )
    }
}
