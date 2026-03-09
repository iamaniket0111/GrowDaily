package com.anitech.growdaily.database

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
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskUiItem
import com.anitech.growdaily.data_class.TaskUiState
import com.anitech.growdaily.enum_class.DateMode
import com.anitech.growdaily.enum_class.TimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class TaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    companion object {
        // Adjust this if you want smaller/larger window
        private const val RANGE_DAYS = 91
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

    // -----------------------------
    // UI STATE (optimized)
    // -----------------------------
    val taskUiState: LiveData<TaskUiState> =
        MediatorLiveData<TaskUiState>().apply {

            var tasks: List<TaskEntity>? = null
            var date: String? = null
            var completionAll: Map<String, Map<String, Int>>? = null
            var selectedListId: String? = null
            var taskIdsForSelectedList: List<String> = emptyList()


            // keep a reference to this MediatorLiveData so we can post from background
            val mediator = this

            fun rebuildStateAsync() {
                val t = tasks ?: return
                val d = date ?: return
                val completionMap = completionAll ?: return

                // do heavy work off main
                viewModelScope.launch(Dispatchers.Default) {

                    // filtered tasks only once
                    val filteredTasks = CommonMethods.filterTasks(t, d)

                    val listFilteredTasks =
                        if (selectedListId == null) {
                            filteredTasks
                        } else {
                            filteredTasks.filter { it.id in taskIdsForSelectedList }
                        }

                    val orderedTasks = CommonMethods.applySmartTimeOrder(listFilteredTasks)

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
                        val count = completionMap[d]?.get(task.id) ?: 0
                        val timeState = when {
                            !task.isScheduled || task.scheduledMinutes == null -> TimeState.NONE
                            dateMode != DateMode.TODAY || currentMinutes == null -> TimeState.NONE
                            task.scheduledMinutes!! < currentMinutes -> TimeState.PAST
                            task.scheduledMinutes == currentMinutes -> TimeState.CURRENT
                            else -> TimeState.FUTURE
                        }

                        val currentStreak = if (task.taskType == com.anitech.growdaily.enum_class.TaskType.DAILY) {
                            val taskStart = LocalDate.parse(task.taskAddedDate)
                            val completedDatesForTask = completionMap.entries
                                .filter { (_, taskMap) -> (taskMap[task.id] ?: 0) > 0 }
                                .mapNotNull { (dateStr, _) ->
                                    runCatching { LocalDate.parse(dateStr) }.getOrNull()
                                }
                                .toSet()
                            CommonMethods.calculateCurrentStreak(taskStart, completedDatesForTask)
                        } else 0

                        TaskUiItem(
                            task = task,
                            isActive = task.scheduledMinutes != null &&
                                    task.scheduledMinutes == activeMinutes && !isListFiltered,
                            timeState = timeState,
                            dateMode = dateMode,
                            currentStreak = currentStreak,
                            completionCount = count,
                            isListFiltered = isListFiltered
                        )
                    }


                    // basic scores
                    val dayScore =
                        CommonMethods.calculateScoreForDate(filteredTasks, d, completionMap)
                    val selectedDate = LocalDate.parse(d)

                    val weekStart =
                        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weekEnd = weekStart.plusDays(6)

                    val weekScore = CommonMethods.calculateAggregateScore(
                        filteredTasks,
                        weekStart,
                        weekEnd,
                        completionMap
                    )

                    val monthStart = selectedDate.withDayOfMonth(1)
                    val monthEnd = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                    val monthScore = CommonMethods.calculateAggregateScore(
                        filteredTasks,
                        monthStart,
                        monthEnd,
                        completionMap
                    )

                    // bar window
                    val half = RANGE_DAYS / 2L
                    val start = selectedDate.minusDays(half)
                    val end = selectedDate.plusDays(half)

                    // build bar list
                    val barScores = ArrayList<DailyScore>(RANGE_DAYS)
                    var current = start
                    while (!current.isAfter(end)) {

                        // IMPORTANT: call calculateScoreForDate with filteredTasks (much cheaper)
                        val score = CommonMethods.calculateScoreForDate(
                            filteredTasks,
                            current.toString(),
                            completionMap
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
                        isEmpty = listFilteredTasks.isEmpty(),
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

            addSource(this@TaskViewModel.selectedListId) {
                selectedListId = it
                rebuildStateAsync()
            }


            addSource(listTaskIds) {
                taskIdsForSelectedList = it
                rebuildStateAsync()
            }

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


    fun resetTaskCompletion(taskId: String, date: String) =
        viewModelScope.launch {
            repository.resetCompletion(taskId, date)
        }
}
