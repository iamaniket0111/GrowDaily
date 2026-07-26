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
import kotlinx.coroutines.Job
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
    private var latestCompletionEntriesByTaskIdAll: Map<String, List<Pair<String, TaskCompletionEntity>>> = emptyMap()
    private var latestTrackingVersionsAll: Map<String, List<TaskTrackingVersionEntity>>? = null
    private var latestTaskDaySnapshotsAll: Map<String, Map<String, TaskDaySnapshotEntity>>? = null
    private var latestBarCompletionEntities: Map<String, Map<String, TaskCompletionEntity>> = emptyMap()
    private var latestBarTaskDaySnapshots: Map<String, Map<String, TaskDaySnapshotEntity>> = emptyMap()
    private var latestTaskExtraDatesAll: Map<String, Set<String>>? = null
    private var latestUntilCompleteChildrenAll: List<UntilCompleteChildEntity> = emptyList()
    private var latestSelectedListId: String? = null
    private var latestTaskIdsForSelectedList: List<String> = emptyList()
    private var expandedTasksCache: List<TaskEntity> = emptyList()
    private var childParentMapCache: Map<String, String> = emptyMap()
    private var parentTaskMapCache: Map<String, TaskEntity> = emptyMap()
    private var taskUiStateJob: Job? = null
    private var barTimelineJob: Job? = null

    private val barScoreCache = linkedMapOf<String, DailyScore>()
    private var barWindowStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(INITIAL_BAR_PAST_DAYS)
    private var barWindowEnd: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6).plusDays(INITIAL_BAR_FUTURE_DAYS)
    private var isLoadingBarPast = false
    private var isLoadingBarFuture = false
    private val barWindowRange = MutableLiveData(BarWindowRange(barWindowStart.toString(), barWindowEnd.toString()))

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
    private val allCompletions = repository.getAllCompletions()

    val completionMap: LiveData<Map<String, Map<String, Int>>> =
        allCompletions.map { list ->
            list.groupBy { it.date }
                .mapValues { entry ->
                    entry.value.associate { it.taskId to it.count }
                }
        }

    val completionEntityMap: LiveData<Map<String, Map<String, TaskCompletionEntity>>> =
        allCompletions.map { list ->
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

    private val barCompletionEntityMap: LiveData<Map<String, Map<String, TaskCompletionEntity>>> =
        barWindowRange.switchMap { range ->
            repository.getCompletionsBetweenFlow(range.startDate, range.endDate)
                .asLiveData()
                .map(::buildCompletionEntityMap)
        }

    private val barTaskDaySnapshotMap: LiveData<Map<String, Map<String, TaskDaySnapshotEntity>>> =
        barWindowRange.switchMap { range ->
            repository.getTaskDaySnapshotsBetweenFlow(range.startDate, range.endDate)
                .asLiveData()
                .map(::buildTaskDaySnapshotMap)
        }

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
                val expandedTasks = expandedTasksCache
                val childParentMap = childParentMapCache
                val parentTaskMap = parentTaskMapCache

                // do heavy work off main
                taskUiStateJob?.cancel()
                taskUiStateJob = viewModelScope.launch(Dispatchers.Default) {

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
                    val untilCompleteVisible = allVisibleTasks.filter { task ->
                        task.taskType == TaskType.UNTIL_COMPLETE &&
                            untilCompleteStates[task.id]?.isVisible == true
                    }
                    val scheduledNonUntilComplete = scheduledTasksForSelectedDate.filter {
                        it.taskType != TaskType.UNTIL_COMPLETE
                    }
                    val sessionRows = buildTaskSessionRows(
                        selectedDate = d,
                        scheduledTasks = scheduledNonUntilComplete,
                        untilCompleteTasks = untilCompleteVisible,
                        allVisibleTasks = allVisibleTasks,
                        extraDateMap = extraDateMap,
                        untilCompleteStates = untilCompleteStates,
                        completionEntityMap = completionEntityMap,
                        trackingVersionsMap = trackingVersionsMap
                    )
                    val orderedSessionRows = CommonMethods.applySmartTimeOrder(
                        sessionRows.map { it.task }.distinctBy { it.id }
                    ).flatMap { orderedTask ->
                        sessionRows
                            .filter { it.task.id == orderedTask.id }
                            .sortedBy { it.completionDate }
                    }

                    val dateMode = CommonMethods.getDateMode(d)
                    val currentMinutes = if (dateMode == DateMode.TODAY)
                        CommonMethods.currentMinutes()
                    else null

                    val activeMinutes =
                        if (dateMode == DateMode.TODAY && currentMinutes != null) {
                            orderedSessionRows
                                .map { it.task }
                                .distinctBy { it.id }
                                .filter { it.isScheduled && it.scheduledMinutes != null }
                                .filter {
                                    val scheduled = it.scheduledMinutes!!
                                    scheduled <= currentMinutes && (currentMinutes - scheduled) <= 60
                                }
                                .maxByOrNull { it.scheduledMinutes!! }
                                ?.scheduledMinutes
                        } else null

                    val isListFiltered = latestSelectedListId != null
                    val visibleTasks = orderedSessionRows.map { it.task }.distinctBy { it.id }
                    val visibleDailyTaskIds = visibleTasks.asSequence()
                        .filter { it.taskType == TaskType.DAILY }
                        .map { it.id }
                        .toSet()
                    val dailyTaskStreaks = buildDailyTaskStreaks(
                        tasks = visibleTasks,
                        completionEntriesByTaskId = latestCompletionEntriesByTaskIdAll,
                        visibleTaskIds = visibleDailyTaskIds,
                        trackingVersionsMap = trackingVersionsMap
                    )


                    val uiItems = orderedSessionRows.map { session ->
                        val task = session.task
                        val untilCompleteState = untilCompleteStates[task.id]
                        val completionDate = session.completionDate
                        val isUntilCompleteChild = childParentMap.containsKey(task.id)
                        val completion = if (isUntilCompleteChild) {
                            completionEntityMap[task.taskAddedDate]?.get(task.id)
                        } else {
                            completionEntityMap[completionDate]?.get(task.id)
                        }
                        val snapshot = if (isUntilCompleteChild) {
                            null
                        } else {
                            snapshotMap[completionDate]?.get(task.id)
                        }
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

                        val currentStreak = dailyTaskStreaks[task.id] ?: 0

                        TaskUiItem(
                            listItemKey = "${task.id}|$completionDate",
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
                        isEmpty = orderedSessionRows.isEmpty(),
                        selectedListId = latestSelectedListId
                    )

                    mediator.postValue(ui)
                }
            }

            addSource(allTasks) {
                latestTasks = it
                refreshExpandedTaskCaches()
                rebuildStateAsync()
            }

            addSource(selectedDate) {
                latestDate = it
                rebuildStateAsync()
            }

            addSource(allCompletions) {
                latestCompletionAll = buildCompletionCountMap(it)
                latestCompletionEntitiesAll = buildCompletionEntityMap(it)
                latestCompletionEntriesByTaskIdAll = buildCompletionEntriesByTaskId(it)
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
                refreshExpandedTaskCaches()
                rebuildStateAsync()
            }

            addSource(listTaskIds) {
                latestTaskIdsForSelectedList = it
                rebuildStateAsync()
            }

            addSource(this@TaskViewModel.selectedListId) {
                latestSelectedListId = it
                rebuildStateAsync()
            }
        }

    private val _barTimelineState = MediatorLiveData<BarTimelineState>().apply {

        fun rebuildBarStateAsync() {
            latestTasks ?: return
            val completionEntityMap = latestBarCompletionEntities
            val trackingVersionsMap = latestTrackingVersionsAll ?: return
            val snapshotMap = latestBarTaskDaySnapshots
            val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
            val expandedTasks = expandedTasksCache
            val selectedDate = latestDate ?: CommonMethods.getTodayDate()

            barTimelineJob?.cancel()
            barTimelineJob = viewModelScope.launch(Dispatchers.Default) {
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
            refreshExpandedTaskCaches()
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(selectedDate) {
            latestDate = it
            rebuildBarStateAsync()
        }

        addSource(barCompletionEntityMap) {
            latestBarCompletionEntities = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(trackingVersionMap) {
            latestTrackingVersionsAll = it
            barScoreCache.clear()
            rebuildBarStateAsync()
        }

        addSource(barTaskDaySnapshotMap) {
            latestBarTaskDaySnapshots = it
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
            refreshExpandedTaskCaches()
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
        refreshBarWindowRange()
    }

    fun loadMoreBarPast() {
        if (isLoadingBarPast) return
        val selectedDate = latestDate ?: CommonMethods.getTodayDate()

        isLoadingBarPast = true
        emitLoadingBarState(selectedDate)

        val newStart = barWindowStart.minusDays(BAR_CHUNK_DAYS)
        
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
        barWindowStart = newStart
        isLoadingBarPast = false
        refreshBarWindowRange(selectedDate)
    }

    fun loadMoreBarFuture() {
        if (isLoadingBarFuture) return
        val selectedDate = latestDate ?: CommonMethods.getTodayDate()

        isLoadingBarFuture = true
        emitLoadingBarState(selectedDate)

        val newEnd = barWindowEnd.plusDays(BAR_CHUNK_DAYS)

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
        barWindowEnd = newEnd
        isLoadingBarFuture = false
        refreshBarWindowRange(selectedDate)
    }

    private fun rebuildBarTimelineState() {
        latestTasks ?: return
        val completionEntityMap = latestBarCompletionEntities
        val trackingVersionsMap = latestTrackingVersionsAll ?: return
        val snapshotMap = latestBarTaskDaySnapshots
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        val expandedTasks = expandedTasksCache
        val selectedDate = latestDate ?: CommonMethods.getTodayDate()

        barTimelineJob?.cancel()
        barTimelineJob = viewModelScope.launch(Dispatchers.Default) {
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

    private fun refreshExpandedTaskCaches() {
        val tasks = latestTasks.orEmpty()
        parentTaskMapCache = tasks.associateBy { it.id }
        childParentMapCache = latestUntilCompleteChildrenAll.associate { it.childTaskId to it.parentTaskId }
        expandedTasksCache = expandUntilCompleteTasks(tasks, latestUntilCompleteChildrenAll)
    }

    private fun buildCompletionCountMap(
        completions: List<TaskCompletionEntity>
    ): Map<String, Map<String, Int>> {
        return completions.groupBy { it.date }
            .mapValues { entry ->
                entry.value.associate { it.taskId to it.count }
            }
    }

    private fun buildCompletionEntityMap(
        completions: List<TaskCompletionEntity>
    ): Map<String, Map<String, TaskCompletionEntity>> {
        return completions.groupBy { it.date }
            .mapValues { entry ->
                entry.value.associateBy { it.taskId }
            }
    }

    private fun buildCompletionEntriesByTaskId(
        completions: List<TaskCompletionEntity>
    ): Map<String, List<Pair<String, TaskCompletionEntity>>> {
        val entriesByTaskId = mutableMapOf<String, MutableList<Pair<String, TaskCompletionEntity>>>()
        completions.forEach { completion ->
            entriesByTaskId.getOrPut(completion.taskId) { mutableListOf() }
                .add(completion.date to completion)
        }
        return entriesByTaskId
    }

    private fun buildDailyTaskStreaks(
        tasks: List<TaskEntity>,
        completionEntriesByTaskId: Map<String, List<Pair<String, TaskCompletionEntity>>>,
        visibleTaskIds: Set<String>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>
    ): Map<String, Int> {
        val today = LocalDate.now()
        if (visibleTaskIds.isEmpty()) return emptyMap()
        return tasks.asSequence()
            .filter { it.taskType == TaskType.DAILY && it.id in visibleTaskIds }
            .associate { task ->
                val taskStart = LocalDate.parse(task.taskAddedDate)
                val scheduledDates = CommonMethods.scheduledDatesBetween(
                    task = task,
                    startInclusive = taskStart,
                    endInclusive = today
                )
                val completedDatesForTask = completionEntriesByTaskId[task.id]
                    .orEmpty()
                    .asSequence()
                    .filter { (dateStr, completion) ->
                        isCompletedDerived(
                            task = task,
                            completion = completion,
                            settings = resolveTrackingSettings(
                                task = task,
                                date = dateStr,
                                versions = trackingVersionsMap[task.id].orEmpty()
                            )
                        )
                    }
                    .mapNotNull { (dateStr, _) ->
                        runCatching { LocalDate.parse(dateStr) }.getOrNull()
                    }
                    .filter { it in scheduledDates }
                    .toSet()

                task.id to CommonMethods.calculateCurrentStreak(
                    taskStart,
                    completedDatesForTask,
                    scheduledDates
                )
            }
    }

    private fun buildTaskDaySnapshotMap(
        snapshots: List<TaskDaySnapshotEntity>
    ): Map<String, Map<String, TaskDaySnapshotEntity>> {
        return snapshots.groupBy { it.date }
            .mapValues { entry ->
                entry.value.associateBy { it.taskId }
            }
    }

    private fun refreshBarWindowRange(selectedDate: String? = null) {
        barWindowRange.value = BarWindowRange(
            startDate = barWindowStart.toString(),
            endDate = barWindowEnd.toString()
        )
        if (selectedDate != null) {
            emitLoadingBarState(selectedDate)
        }
    }

    override fun onCleared() {
        taskUiStateJob?.cancel()
        barTimelineJob?.cancel()
        super.onCleared()
    }

    private fun emitLoadingBarState(selectedDate: String) {
        _barTimelineState.value = (_barTimelineState.value ?: BarTimelineState(selectedDate = selectedDate)).copy(
            selectedDate = selectedDate,
            isLoadingPast = isLoadingBarPast,
            isLoadingFuture = isLoadingBarFuture
        )
    }

    private suspend fun emitCurrentBarState(selectedDate: String) {
        latestTasks ?: return
        val completionEntityMap = latestBarCompletionEntities
        val trackingVersionsMap = latestTrackingVersionsAll ?: return
        val snapshotMap = latestBarTaskDaySnapshots
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        val expandedTasks = expandedTasksCache
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
            val (resolvedTaskId, resolvedDate) = resolveCompletionTarget(taskId, date)
            repository.incrementCompletion(resolvedTaskId, resolvedDate)
        }

    fun decrementTaskCompletion(taskId: String, date: String) =
        viewModelScope.launch {
            val (resolvedTaskId, resolvedDate) = resolveCompletionTarget(taskId, date)
            repository.decrementCompletion(resolvedTaskId, resolvedDate)
        }

    fun changeTaskCompletionBy(taskId: String, date: String, delta: Int) =
        viewModelScope.launch {
            val (resolvedTaskId, resolvedDate) = resolveCompletionTarget(taskId, date)
            when {
                delta > 0 -> repeat(delta) { repository.incrementCompletion(resolvedTaskId, resolvedDate) }
                delta < 0 -> repeat(-delta) { repository.decrementCompletion(resolvedTaskId, resolvedDate) }
            }
        }

    fun resetTaskCompletion(taskId: String, date: String) =
        viewModelScope.launch {
            val (resolvedTaskId, resolvedDate) = resolveCompletionTarget(taskId, date)
            repository.resetCompletion(resolvedTaskId, resolvedDate)
        }

    fun addTimerDuration(taskId: String, date: String, seconds: Long) {
        viewModelScope.launch {
            val (resolvedTaskId, resolvedDate) = resolveCompletionTarget(taskId, date)
            runCatching {
                repository.addTimerDuration(resolvedTaskId, resolvedDate, seconds)
            }.onFailure { error ->
                Log.e(TAG, "Failed to add timer duration for $resolvedTaskId on $resolvedDate", error)
            }
        }
    }

    fun updateChecklist(taskId: String, date: String, checklistJson: String) {
        viewModelScope.launch {
            val (resolvedTaskId, resolvedDate) = resolveCompletionTarget(taskId, date)
            runCatching {
                repository.updateChecklistState(resolvedTaskId, resolvedDate, checklistJson)
            }.onFailure { error ->
                Log.e(TAG, "Failed to update checklist for $resolvedTaskId on $resolvedDate", error)
            }
        }
    }

    /**
     * Until-complete child sessions always read/write completion on [UntilCompleteChildEntity.taskAddedDate],
     * not the calendar day being viewed.
     */
    private fun resolveCompletionTarget(taskId: String, date: String): Pair<String, String> {
        untilCompleteChildByTaskId(taskId)?.let { child ->
            return child.childTaskId to child.taskAddedDate
        }
        val task = latestTasks?.find { it.id == taskId }
        val extraDateMap = latestTaskExtraDatesAll ?: emptyMap()
        if (task != null && isDayExtraDateSession(task, date, extraDateMap)) {
            return taskId to date
        }
        return taskId to date
    }

    /**
     * DAY + showUntilCompleted: original day keeps its completion key; each "Add for today"
     * extra date is a separate session keyed to that calendar day (not taskAddedDate).
     */
    private fun resolveDayTaskCompletionDate(
        task: TaskEntity,
        selectedDate: String,
        extraDateMap: Map<String, Set<String>>,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>
    ): String {
        val extraDates = extraDateMap[task.id].orEmpty()
        if (selectedDate in extraDates) {
            return selectedDate
        }
        val pendingExtraDate = extraDates
            .filter { it < selectedDate && it != task.taskAddedDate }
            .filter { extraDate ->
                val settings = resolveTrackingSettings(
                    task = task,
                    date = extraDate,
                    versions = trackingVersionsMap[task.id].orEmpty()
                )
                !isCompletedDerived(
                    task = task,
                    completion = completionEntityMap[extraDate]?.get(task.id),
                    settings = settings
                )
            }
            .maxOrNull()
        if (pendingExtraDate != null) {
            return pendingExtraDate
        }
        if (task.taskAddedDate < selectedDate) {
            return task.taskAddedDate
        }
        return selectedDate
    }

    private fun isDayExtraDateSession(
        task: TaskEntity,
        date: String,
        extraDateMap: Map<String, Set<String>>
    ): Boolean {
        if (task.taskType != TaskType.DAY) return false
        val extraDates = extraDateMap[task.id].orEmpty()
        return date in extraDates && date != task.taskAddedDate
    }

    private fun untilCompleteChildByTaskId(taskId: String): UntilCompleteChildEntity? =
        latestUntilCompleteChildrenAll.firstOrNull { it.childTaskId == taskId }

    private fun untilCompleteChildForParentOnDate(
        parentTaskId: String,
        date: String
    ): UntilCompleteChildEntity? =
        latestUntilCompleteChildrenAll.firstOrNull {
            it.parentTaskId == parentTaskId && it.taskAddedDate == date
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

    private data class TaskSessionRow(
        val task: TaskEntity,
        val completionDate: String
    )

    private data class BarWindowRange(
        val startDate: String,
        val endDate: String
    )

    private data class UntilCompleteDisplayState(
        val isVisible: Boolean,
        val displayCompletionDate: String
    )

    private fun buildTaskSessionRows(
        selectedDate: String,
        scheduledTasks: List<TaskEntity>,
        untilCompleteTasks: List<TaskEntity>,
        allVisibleTasks: List<TaskEntity>,
        extraDateMap: Map<String, Set<String>>,
        untilCompleteStates: Map<String, UntilCompleteDisplayState>,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>
    ): List<TaskSessionRow> {
        val rows = mutableListOf<TaskSessionRow>()
        val dayTasksWithRows = mutableSetOf<String>()

        fun addDayUntilCompletedSessions(task: TaskEntity) {
            collectDayShowUntilCompletedSessionDates(
                task = task,
                selectedDate = selectedDate,
                extraDateMap = extraDateMap,
                completionEntityMap = completionEntityMap,
                trackingVersionsMap = trackingVersionsMap
            ).forEach { sessionDate ->
                rows += TaskSessionRow(task, sessionDate)
            }
            dayTasksWithRows += task.id
        }

        scheduledTasks.forEach { task ->
            when {
                task.taskType == TaskType.DAY && task.showUntilCompleted ->
                    addDayUntilCompletedSessions(task)
                else -> rows += TaskSessionRow(task, selectedDate)
            }
        }

        untilCompleteTasks.forEach { task ->
            val completionDate = untilCompleteStates[task.id]?.displayCompletionDate ?: selectedDate
            rows += TaskSessionRow(task, completionDate)
        }

        allVisibleTasks
            .filter { it.taskType == TaskType.DAY && it.showUntilCompleted && it.id !in dayTasksWithRows }
            .forEach { task -> addDayUntilCompletedSessions(task) }

        return rows.distinctBy { "${it.task.id}|${it.completionDate}" }
    }

    /**
     * Each returned date is a distinct work session (original day or an "Add for today" extra date)
     * that should appear on [selectedDate] as its own row when still incomplete.
     */
    private fun collectDayShowUntilCompletedSessionDates(
        task: TaskEntity,
        selectedDate: String,
        extraDateMap: Map<String, Set<String>>,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>
    ): List<String> {
        val sessionDates = linkedSetOf<String>()
        val extraDates = extraDateMap[task.id].orEmpty()

        if (task.taskAddedDate == selectedDate || selectedDate in extraDates) {
            sessionDates += if (selectedDate in extraDates) selectedDate else task.taskAddedDate
        }

        if (task.taskAddedDate < selectedDate &&
            !isDaySessionCompleted(
                task = task,
                sessionDate = task.taskAddedDate,
                completionEntityMap = completionEntityMap,
                trackingVersionsMap = trackingVersionsMap
            )
        ) {
            sessionDates += task.taskAddedDate
        }

        extraDates
            .filter { it != task.taskAddedDate && it < selectedDate }
            .filter { extraDate ->
                !isDaySessionCompleted(
                    task = task,
                    sessionDate = extraDate,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionsMap
                )
            }
            .forEach { sessionDates += it }

        return sessionDates.toList()
    }

    private fun isDaySessionCompleted(
        task: TaskEntity,
        sessionDate: String,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>
    ): Boolean {
        val completion = completionEntityMap[sessionDate]?.get(task.id)
        val settings = resolveTrackingSettings(
            task = task,
            date = sessionDate,
            versions = trackingVersionsMap[task.id].orEmpty()
        )
        return isCompletedDerived(task, completion, settings)
    }

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

        // Synthetic child: one session anchored to taskAddedDate (Add-to-today date).
        if (task.id != versionOwnerId) {
            return resolveUntilCompleteChildState(
                task = task,
                selectedDate = selectedDate,
                completionEntityMap = completionEntityMap,
                trackingVersionsMap = trackingVersionsMap,
                versionOwnerId = versionOwnerId
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

    private fun resolveUntilCompleteChildState(
        task: TaskEntity,
        selectedDate: String,
        completionEntityMap: Map<String, Map<String, TaskCompletionEntity>>,
        trackingVersionsMap: Map<String, List<TaskTrackingVersionEntity>>,
        versionOwnerId: String
    ): UntilCompleteDisplayState {
        val sessionStart = task.taskAddedDate
        val completion = completionEntityMap[sessionStart]?.get(task.id)
        val settings = resolveTrackingSettings(
            task = task,
            date = sessionStart,
            versions = trackingVersionsMap[versionOwnerId].orEmpty()
        )
        if (isCompletedDerived(task, completion, settings)) {
            return UntilCompleteDisplayState(
                isVisible = sessionStart == selectedDate,
                displayCompletionDate = sessionStart
            )
        }
        return UntilCompleteDisplayState(
            isVisible = true,
            displayCompletionDate = sessionStart
        )
    }
}
