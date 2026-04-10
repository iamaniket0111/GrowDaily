package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AnalysisBarState
import com.anitech.growdaily.data_class.AnalysisHeatmapState
import com.anitech.growdaily.data_class.AnalysisOverviewState
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.util.completionPercent
import com.anitech.growdaily.database.util.isCompletedDerived
import com.anitech.growdaily.database.util.resolveTrackingSettings
import com.anitech.growdaily.enum_class.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class AnalysisViewModel(
    private val repository: AppRepository
) : ViewModel() {
    private val allTasks = repository.getAllTasksFlow().asLiveData()
    private val allCompletions = repository.getAllCompletionsFlow().asLiveData()
    private val allSnapshots = repository.getAllTaskDaySnapshotsFlow().asLiveData()

    // -----------------------------
    // TASK NOT FOUND
    // -----------------------------
    private val _taskNotFound = MutableLiveData<Boolean>()
    val taskNotFound: LiveData<Boolean> = _taskNotFound

    // -----------------------------
    // TASK
    // -----------------------------
    private val _selectedTask = MediatorLiveData<TaskEntity>()
    private var currentTaskId: String? = null
    private var selectedTaskSource: LiveData<TaskEntity>? = null

    fun setTaskId(taskId: String) {
        if (currentTaskId == taskId && selectedTaskSource != null) return

        selectedTaskSource?.let { source ->
            _selectedTask.removeSource(source)
        }

        currentTaskId = taskId
        val source = repository.getTaskById(taskId)
        selectedTaskSource = source
        _selectedTask.addSource(source) { task ->
            if (task == null) {
                _taskNotFound.value = true
                return@addSource
            }
            _selectedTask.value = task

            // Reset anchors when task changes
            val today = LocalDate.now()
            _weekAnchor.value = today
            _monthAnchor.value = today
            _yearAnchor.value = today
            _heatmapYear.value = today.year
        }
    }

    // -----------------------------
    // PERIOD
    // -----------------------------
    private val _analysisPeriod = MutableLiveData<PeriodType>(PeriodType.WEEK)
    val analysisPeriod: LiveData<PeriodType> = _analysisPeriod

    fun setAnalysisPeriod(period: PeriodType) {
        if (_analysisPeriod.value == period) return
        _analysisPeriod.value = period
    }

    // -----------------------------
    // INDEPENDENT ANCHORS
    // -----------------------------
    private val _weekAnchor = MutableLiveData(LocalDate.now())
    private val _monthAnchor = MutableLiveData(LocalDate.now())
    private val _yearAnchor = MutableLiveData(LocalDate.now())

    fun moveAnalysisAnchor(delta: Int) {
        when (_analysisPeriod.value) {
            PeriodType.WEEK -> _weekAnchor.value = (_weekAnchor.value ?: LocalDate.now()).plusWeeks(delta.toLong())
            PeriodType.MONTH -> _monthAnchor.value = (_monthAnchor.value ?: LocalDate.now()).plusMonths(delta.toLong())
            PeriodType.YEAR -> _yearAnchor.value = (_yearAnchor.value ?: LocalDate.now()).plusYears(delta.toLong())
            null -> {}
        }
    }

    // -----------------------------
    // HEATMAP YEAR
    // -----------------------------
    private val _heatmapYear = MutableLiveData(LocalDate.now().year)

    fun moveHeatmapYear(delta: Int) {
        _heatmapYear.value = (_heatmapYear.value ?: LocalDate.now().year) + delta
    }

    // ======================================================
    // STATE 1: OVERVIEW
    // Rebuilds ONLY when: task or completions change
    // ======================================================
    val overviewState: LiveData<AnalysisOverviewState> =
        MediatorLiveData<AnalysisOverviewState>().apply {

            var task: TaskEntity? = null
            var tasks: List<TaskEntity> = emptyList()
            var completions: List<TaskCompletionEntity> = emptyList()
            var snapshots: List<TaskDaySnapshotEntity> = emptyList()

            fun build() {
                val t = task ?: return
                viewModelScope.launch {
                    val state = withContext(Dispatchers.Default) {
                        val seriesId = t.seriesId.ifBlank { t.id }
                        val seriesTasks = tasks
                            .filter { it.seriesId.ifBlank { it.id } == seriesId }
                            .sortedBy { it.taskAddedDate }
                        if (seriesTasks.isEmpty()) return@withContext null
                        val displayTask = seriesTasks.last()
                        val taskIds = seriesTasks.map { it.id }.toSet()
                        val taskStart = LocalDate.parse(seriesTasks.minOf { it.taskAddedDate })
                        val today = LocalDate.now()
                        val scheduledDates = seriesTasks
                            .flatMapTo(sortedSetOf()) { segment ->
                                CommonMethods.scheduledDatesBetween(
                                    segment,
                                    LocalDate.parse(segment.taskAddedDate),
                                    today
                                )
                            }
                        val seriesSnapshots = snapshots.filter { it.taskId in taskIds }
                        val snapshotByDate = seriesSnapshots
                            .associateBy({ LocalDate.parse(it.date) }, { it })
                        val completionDates = completions
                            .asSequence()
                            .filter { it.taskId in taskIds }
                            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                            .toSet()
                        val progressByDate = scheduledDates.associateWith { date ->
                            snapshotByDate[date]?.progressPercent
                                ?: if (completionDates.contains(date)) 100 else 0
                        }
                        val completedDates = scheduledDates.filter { date ->
                            snapshotByDate[date]?.isCompleted ?: completionDates.contains(date)
                        }.toSet()
                        val currentSegment = displayTask
                        val currentSegmentStart = LocalDate.parse(currentSegment.taskAddedDate)
                        val currentSegmentScheduledDates = CommonMethods.scheduledDatesBetween(
                            currentSegment,
                            currentSegmentStart,
                            today
                        )
                        val totalDays = scheduledDates.size
                        val completedCount = completedDates.count { scheduledDates.contains(it) }
                        val percent = if (totalDays > 0) {
                            progressByDate.values.sum() / totalDays
                        } else 0
                        val currentStreak = CommonMethods.calculateCurrentStreak(
                            currentSegmentStart,
                            completedDates,
                            currentSegmentScheduledDates
                        )
                        val bestStreak = CommonMethods.calculateBestStreak(taskStart, completedDates, scheduledDates)
                        val orderedScheduledDates = scheduledDates.sorted()
                        val lastCompletedDate = orderedScheduledDates
                            .lastOrNull { completedDates.contains(it) }
                        val lastMissedDate = orderedScheduledDates
                            .lastOrNull { !completedDates.contains(it) }

                        AnalysisOverviewState(
                            task = displayTask,
                            seriesStartDate = taskStart,
                            scheduledDates = scheduledDates,
                            progressByDate = progressByDate,
                            completedDates = completedDates,
                            currentStreak = currentStreak,
                            bestStreak = bestStreak,
                            completionPercent = percent,
                            completedCount = completedCount,
                            totalDays = totalDays,
                            lastCompletedDate = lastCompletedDate,
                            lastMissedDate = lastMissedDate
                        )
                    }
                    state?.let { value = it }
                }
            }

            addSource(_selectedTask) { taskEntity ->
                task = taskEntity
                build()
            }

            addSource(allTasks) { tasks = it; build() }
            addSource(allCompletions) { completions = it; build() }
            addSource(allSnapshots) { snapshots = it; build() }
        }

    // ======================================================
    // STATE 2: BAR GRAPH
    // Rebuilds ONLY when: period, anchor, or completions change
    // ======================================================
    val barState: LiveData<AnalysisBarState> =
        MediatorLiveData<AnalysisBarState>().apply {

            var task: TaskEntity? = null
            var tasks: List<TaskEntity> = emptyList()
            var completions: List<TaskCompletionEntity> = emptyList()
            var snapshots: List<TaskDaySnapshotEntity> = emptyList()
            var period: PeriodType = PeriodType.WEEK
            var weekAnchor = LocalDate.now()
            var monthAnchor = LocalDate.now()
            var yearAnchor = LocalDate.now()

            fun build() {
                val t = task ?: return
                viewModelScope.launch {
                    val state = withContext(Dispatchers.Default) {
                        val seriesId = t.seriesId.ifBlank { t.id }
                        val seriesTasks = tasks
                            .filter { it.seriesId.ifBlank { it.id } == seriesId }
                            .sortedBy { it.taskAddedDate }
                        if (seriesTasks.isEmpty()) return@withContext null
                        val displayTask = seriesTasks.last()
                        val taskIds = seriesTasks.map { it.id }.toSet()
                        val taskStart = LocalDate.parse(seriesTasks.minOf { it.taskAddedDate })
                        val today = LocalDate.now()
                        val completionDates = completions
                            .filter { it.taskId in taskIds }
                            .mapNotNull {
                            runCatching { LocalDate.parse(it.date) }.getOrNull()
                        }.toSet()
                        val snapshotByDate = snapshots
                            .asSequence()
                            .filter { it.taskId in taskIds }
                            .associateBy({ LocalDate.parse(it.date) }, { it })

                        val anchor = when (period) {
                            PeriodType.WEEK -> weekAnchor
                            PeriodType.MONTH -> monthAnchor
                            PeriodType.YEAR -> yearAnchor
                        }

                        val barDates = when (period) {
                            PeriodType.WEEK -> {
                                val start = anchor.with(DayOfWeek.MONDAY)
                                (0..6).map { start.plusDays(it.toLong()) }
                            }
                            PeriodType.MONTH -> {
                                val ym = YearMonth.from(anchor)
                                (1..ym.lengthOfMonth()).map { ym.atDay(it) }
                            }
                            PeriodType.YEAR -> {
                                (1..12).map { YearMonth.of(anchor.year, it).atDay(1) }
                            }
                        }

                        val barScores = when (period) {
                            PeriodType.YEAR -> barDates.map { firstDay ->
                                val ym = YearMonth.from(firstDay)
                                val days = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
                                    .filter {
                                        !it.isAfter(today) && seriesTasks.any { segment ->
                                            CommonMethods.isTaskActiveOnDate(segment, it.toString())
                                        }
                                    }
                                val totalProgress = days.sumOf { day ->
                                    (snapshotByDate[day]?.progressPercent
                                        ?: if (completionDates.contains(day)) 100 else 0)
                                }
                                if (days.isNotEmpty()) (totalProgress.toFloat() / days.size) / 10f else 0f
                            }
                            else -> barDates.map { date ->
                                if (date.isAfter(today) || seriesTasks.none { segment ->
                                        CommonMethods.isTaskActiveOnDate(segment, date.toString())
                                    }) 0f
                                else (snapshotByDate[date]?.progressPercent
                                    ?: if (completionDates.contains(date)) 100 else 0).toFloat() / 10f
                            }
                        }

                        val periodTitle = when (period) {
                            PeriodType.WEEK -> {
                                val start = anchor.with(DayOfWeek.MONDAY)
                                val end = start.plusDays(6)
                                "${start.dayOfMonth} ${start.month.name.take(3)} - ${end.dayOfMonth} ${end.month.name.take(3)}"
                            }
                            PeriodType.MONTH ->
                                "${anchor.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${anchor.year}"
                            PeriodType.YEAR -> anchor.year.toString()
                        }

                        val isNextEnabled = when (period) {
                            PeriodType.WEEK -> anchor.plusWeeks(1).isBefore(today.plusDays(1))
                            PeriodType.MONTH -> YearMonth.from(anchor) < YearMonth.from(today)
                            PeriodType.YEAR -> anchor.year < today.year
                        }

                        val isPrevEnabled = when (period) {
                            PeriodType.WEEK -> anchor.minusWeeks(1).isAfter(taskStart.minusDays(1))
                            PeriodType.MONTH -> YearMonth.from(anchor) > YearMonth.from(taskStart)
                            PeriodType.YEAR -> anchor.year > taskStart.year
                        }

                        AnalysisBarState(
                            period = period,
                            anchorDate = anchor,
                            barDates = barDates,
                            barScores = barScores,
                            periodTitle = periodTitle,
                            isNextEnabled = isNextEnabled,
                            isPrevEnabled = isPrevEnabled
                        )
                    }
                    state?.let { value = it }
                }
            }

            addSource(_selectedTask) { taskEntity ->
                task = taskEntity
                build()
            }

            addSource(allTasks) { tasks = it; build() }
            addSource(allCompletions) { completions = it; build() }
            addSource(allSnapshots) { snapshots = it; build() }
            addSource(_analysisPeriod) { period = it; build() }
            addSource(_weekAnchor) { weekAnchor = it; build() }
            addSource(_monthAnchor) { monthAnchor = it; build() }
            addSource(_yearAnchor) { yearAnchor = it; build() }
        }

    // ======================================================
    // STATE 3: HEATMAP
    // Rebuilds ONLY when: heatmapYear or completions change
    // ======================================================
    val heatmapState: LiveData<AnalysisHeatmapState> =
        MediatorLiveData<AnalysisHeatmapState>().apply {

            var task: TaskEntity? = null
            var tasks: List<TaskEntity> = emptyList()
            var completions: List<TaskCompletionEntity> = emptyList()
            var snapshots: List<TaskDaySnapshotEntity> = emptyList()
            var heatmapYear = LocalDate.now().year

            fun build() {
                val t = task ?: return
                viewModelScope.launch {
                    val state = withContext(Dispatchers.Default) {
                        val seriesId = t.seriesId.ifBlank { t.id }
                        val seriesTasks = tasks
                            .filter { it.seriesId.ifBlank { it.id } == seriesId }
                            .sortedBy { it.taskAddedDate }
                        if (seriesTasks.isEmpty()) return@withContext null
                        val taskIds = seriesTasks.map { it.id }.toSet()
                        val taskStart = LocalDate.parse(seriesTasks.minOf { it.taskAddedDate })
                        val today = LocalDate.now()
                        val scheduledDates = seriesTasks
                            .flatMapTo(sortedSetOf()) { segment ->
                                CommonMethods.scheduledDatesBetween(
                                    segment,
                                    LocalDate.parse(segment.taskAddedDate),
                                    today
                                )
                            }
                        val snapshotByDate = snapshots
                            .asSequence()
                            .filter { it.taskId in taskIds }
                            .associateBy({ LocalDate.parse(it.date) }, { it })
                        val completionDates = completions
                            .asSequence()
                            .filter { it.taskId in taskIds }
                            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                            .toSet()
                        val progressByDate = scheduledDates.associateWith { date ->
                            snapshotByDate[date]?.progressPercent
                                ?: if (completionDates.contains(date)) 100 else 0
                        }

                        AnalysisHeatmapState(
                            heatmapYear = heatmapYear,
                            seriesStartDate = taskStart,
                            scheduledDates = scheduledDates,
                            progressByDate = progressByDate,
                            isHeatmapNextEnabled = heatmapYear < today.year,
                            isHeatmapPrevEnabled = heatmapYear > taskStart.year
                        )
                    }
                    state?.let { value = it }
                }
            }

            addSource(_selectedTask) { taskEntity ->
                task = taskEntity
                build()
            }

            addSource(allTasks) { tasks = it; build() }
            addSource(allCompletions) { completions = it; build() }
            addSource(allSnapshots) { snapshots = it; build() }
            addSource(_heatmapYear) { heatmapYear = it; build() }
        }
}
