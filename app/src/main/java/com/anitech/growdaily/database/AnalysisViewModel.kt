package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AnalysisBarState
import com.anitech.growdaily.data_class.AnalysisHeatmapState
import com.anitech.growdaily.data_class.AnalysisOverviewState
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class AnalysisViewModel(
    private val repository: AppRepository
) : ViewModel() {

    // -----------------------------
    // TASK NOT FOUND
    // -----------------------------
    private val _taskNotFound = MutableLiveData<Boolean>()
    val taskNotFound: LiveData<Boolean> = _taskNotFound

    // -----------------------------
    // TASK
    // -----------------------------
    private val _selectedTask = MediatorLiveData<TaskEntity>()

    fun setTaskId(taskId: String) {
        val source = repository.getTaskById(taskId)
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
            var completions: List<TaskCompletionEntity> = emptyList()

            fun build() {
                val t = task ?: return
                viewModelScope.launch {
                    val state = withContext(Dispatchers.Default) {
                        val taskStart = LocalDate.parse(t.taskAddedDate)
                        val today = LocalDate.now()
                        val completedDates = completions.map { LocalDate.parse(it.date) }.toSet()
                        val totalDays = ChronoUnit.DAYS.between(taskStart, today).toInt() + 1
                        val completedCount = completedDates.count {
                            !it.isBefore(taskStart) && !it.isAfter(today)
                        }
                        val percent = if (totalDays > 0) (completedCount * 100) / totalDays else 0
                        val currentStreak = CommonMethods.calculateCurrentStreak(taskStart, completedDates)
                        val bestStreak = CommonMethods.calculateBestStreak(taskStart, completedDates)
                        val lastCompletedDate = (0 until totalDays)
                            .map { taskStart.plusDays(it.toLong()) }
                            .lastOrNull { completedDates.contains(it) }
                        val lastMissedDate = (0 until totalDays)
                            .map { taskStart.plusDays(it.toLong()) }
                            .lastOrNull { !completedDates.contains(it) }

                        AnalysisOverviewState(
                            task = t,
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
                    value = state
                }
            }

            var completionsSource: LiveData<List<TaskCompletionEntity>>? = null

            addSource(_selectedTask) { taskEntity ->
                task = taskEntity
                completionsSource?.let { removeSource(it) }
                val source = repository.getCompletionsForTask(taskEntity.id)
                completionsSource = source
                addSource(source) { list ->
                    completions = list
                    build()
                }
                build()
            }
        }

    // ======================================================
    // STATE 2: BAR GRAPH
    // Rebuilds ONLY when: period, anchor, or completions change
    // ======================================================
    val barState: LiveData<AnalysisBarState> =
        MediatorLiveData<AnalysisBarState>().apply {

            var task: TaskEntity? = null
            var completions: List<TaskCompletionEntity> = emptyList()
            var period: PeriodType = PeriodType.WEEK
            var weekAnchor = LocalDate.now()
            var monthAnchor = LocalDate.now()
            var yearAnchor = LocalDate.now()

            fun build() {
                val t = task ?: return
                viewModelScope.launch {
                    val state = withContext(Dispatchers.Default) {
                        val taskStart = LocalDate.parse(t.taskAddedDate)
                        val today = LocalDate.now()
                        val completedDates = completions.map { LocalDate.parse(it.date) }.toSet()

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
                                    .filter { !it.isBefore(taskStart) && !it.isAfter(today) }
                                val completed = days.count { completedDates.contains(it) }
                                if (days.isNotEmpty()) (completed.toFloat() / days.size) * 10f else 0f
                            }
                            else -> barDates.map { if (completedDates.contains(it)) 10f else 0f }
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
                    value = state
                }
            }

            var completionsSource: LiveData<List<TaskCompletionEntity>>? = null

            addSource(_selectedTask) { taskEntity ->
                task = taskEntity
                completionsSource?.let { removeSource(it) }
                val source = repository.getCompletionsForTask(taskEntity.id)
                completionsSource = source
                addSource(source) { list ->
                    completions = list
                    build()
                }
                build()
            }

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
            var completions: List<TaskCompletionEntity> = emptyList()
            var heatmapYear = LocalDate.now().year

            fun build() {
                val t = task ?: return
                viewModelScope.launch {
                    val state = withContext(Dispatchers.Default) {
                        val taskStart = LocalDate.parse(t.taskAddedDate)
                        val today = LocalDate.now()
                        val completedDates = completions.map { LocalDate.parse(it.date) }.toSet()

                        AnalysisHeatmapState(
                            heatmapYear = heatmapYear,
                            heatmapDates = completedDates,
                            isHeatmapNextEnabled = heatmapYear < today.year,
                            isHeatmapPrevEnabled = heatmapYear > taskStart.year
                        )
                    }
                    value = state
                }
            }

            var completionsSource: LiveData<List<TaskCompletionEntity>>? = null

            addSource(_selectedTask) { taskEntity ->
                task = taskEntity
                completionsSource?.let { removeSource(it) }
                val source = repository.getCompletionsForTask(taskEntity.id)
                completionsSource = source
                addSource(source) { list ->
                    completions = list
                    build()
                }
                build()
            }

            addSource(_heatmapYear) { heatmapYear = it; build() }
        }
}