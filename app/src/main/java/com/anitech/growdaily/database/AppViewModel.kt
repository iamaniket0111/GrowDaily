package com.anitech.growdaily.database

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.DayLogEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListWithTasks
import com.anitech.growdaily.data_class.MoodHistoryItem
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskHeatmapUi
import com.anitech.growdaily.data_class.TaskUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters


class AppViewModel(private val repository: AppRepository) : ViewModel() {

    fun insertTask(task: TaskEntity) = viewModelScope.launch {
        repository.insertTask(task)
    }

    fun updateTask(task: TaskEntity) = viewModelScope.launch {
        repository.updateTask(task)
    }

    fun deleteTask(task: TaskEntity) = viewModelScope.launch {
        repository.deleteTask(task)
    }

    fun clearAllTasks() = viewModelScope.launch {//to delete all data, have to work on it further
        repository.clearAllTasks()
    }

    val allTasks: LiveData<List<TaskEntity>> =
        repository
            .getAllTasksFlow()
            .asLiveData()

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    private val _selectedListId = MutableLiveData<String?>(null)
    val selectedListId: LiveData<String?> = _selectedListId

    fun setSelectedList(listId: String?) {
        // double click ignore
        if (_selectedListId.value == listId) return
        _selectedListId.value = listId
    }

    // 👇 Mediator same, but ab allTasks source auto-trigger hoga (DB load pe)

    val completionMap: LiveData<Map<String, Map<String, Int>>> =
        repository.getAllCompletions().map { list ->
            list.groupBy { it.date }
                .mapValues { entry ->
                    entry.value.associate { it.taskId to it.count }
                }
        }

//    val taskUiState = MediatorLiveData<TaskUiState>().apply {
//
//        var tasks: List<TaskEntity>? = null
//        var date: String? = null
//        var completionMapAll: Map<String, Map<String, Int>>? = null
//
//        fun update() {
//            val t = tasks ?: return
//            val d = date ?: return
//            val completionAll = completionMapAll ?: return
//
//            viewModelScope.launch(Dispatchers.Default) {
//
//               // val latestLog = repository.getLatestLogForDate(d)
//
////                val orderedTasks = if (latestLog != null) {
////                    val orderedIds = latestLog.taskIds
////                    t.sortedBy { orderedIds.indexOf(it.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
////                } else {
////                    t
////                }
//
//                val filtered = CommonMethods.filterTasks(t, d)
//
//                val completionForDate = completionAll[d] ?: emptyMap()
//
//                postValue(TaskUiState(filtered, completionForDate))
//            }
//        }
//
//        addSource(allTasks) {
//            tasks = it
//            update()
//        }
//
//        addSource(selectedDate) {
//            date = it
//            update()
//        }
//
//        addSource(completionMap) {
//            completionMapAll = it
//            update()
//        }
//    }



    //new task ui state
    val taskUiState = MediatorLiveData<TaskUiState>().apply {

        var tasks: List<TaskEntity>? = null
        var date: String? = null
        var completionMapAll: Map<String, Map<String, Int>>? = null

        fun update() {
            val t = tasks ?: return
            val d = date ?: return
            val completionAll = completionMapAll ?: return

            viewModelScope.launch(Dispatchers.Default) {

                val filteredTasks =
                    CommonMethods.filterTasks(t, d)

                val completionForDate =
                    completionAll[d] ?: emptyMap()

                val dayScore =
                    CommonMethods.calculateScoreForDate(
                        t, d, completionAll
                    )

                val selected = LocalDate.parse(d)

                val weekStart =
                    selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(6)

                val weekScore =
                    CommonMethods.calculateAggregateScore(
                        t, weekStart, weekEnd, completionAll
                    )

                val monthStart = selected.withDayOfMonth(1)
                val monthEnd = selected.withDayOfMonth(selected.lengthOfMonth())

                val monthScore =
                    CommonMethods.calculateAggregateScore(
                        t, monthStart, monthEnd, completionAll
                    )

                val weekBars =
                    CommonMethods.calculateDailyScoresThisWeek(
                        t, d, completionAll
                    )

                postValue(
                    TaskUiState(
                        date = d,
                        tasks = filteredTasks,
                        completionForDate = completionForDate,
                        dayScore = dayScore,
                        weekScore = weekScore,
                        monthScore = monthScore,
                        weekBarScores = weekBars,
                        isFutureDate = CommonMethods.isFutureDate(d),
                        isEmpty = filteredTasks.isEmpty()
                    )
                )
            }
        }

        addSource(allTasks) {
            tasks = it
            update()
        }

        addSource(selectedDate) {
            date = it
            update()
        }

        addSource(completionMap) {
            completionMapAll = it
            update()
        }
    }




    fun setDate(date: String) {
        _selectedDate.value = date
    }

    val filteredTasksFunc = MediatorLiveData<List<TaskEntity>>()


    private fun updateFilteredTasks() {
        viewModelScope.launch {
            val tasks = allTasks.value ?: return@launch
            val date = selectedDate.value ?: return@launch

            val result =CommonMethods.filterTasks(tasks, date)
            filteredTasksFunc.postValue(result)
        }
    }

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





    val completionCountsForSelectedDate: LiveData<Map<String, Int>> =
        MediatorLiveData<Map<String, Int>>().apply {
            var latestMap: Map<String, Map<String, Int>>? = null
            var latestDate: String? = null

            fun update() {
                val m = latestMap ?: return
                val d = latestDate ?: return
                value = m[d] ?: emptyMap()
            }

            addSource(completionMap) { latestMap = it; update() }
            addSource(selectedDate) { latestDate = it; update() }
        }


//    val completedIdsForSelectedDate: LiveData<Set<String>> =
//        MediatorLiveData<Set<String>>().apply {
//
//            var currentMap: Map<String, Set<String>>? = null
//            var currentDate: String? = null
//
//            fun update() {
//                val map = currentMap ?: return
//                val date = currentDate ?: return
//                value = map[date] ?: emptySet()
//            }
//
//            addSource(completionMap) { map ->
//                currentMap = map
//                update()
//            }
//
//            addSource(selectedDate) { date ->
//                currentDate = date
//                update()
//            }
//        }


    // optional helper (UI ke liye)
    suspend fun isTaskCompleted(taskId: String, date: String): Boolean {
        return repository.isTaskCompleted(taskId, date)
    }

    suspend fun getTasksForDate(date: String): List<TaskEntity> {
        val all = allTasks.value ?: emptyList()
        val latestLog = repository.getLatestLogForDate(date)
        return if (latestLog != null) {
            val orderedIds = latestLog.taskIds
            all.sortedBy { orderedIds.indexOf(it.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        } else {
            all
        }
    }

    val barTasksByDate: LiveData<Map<String, List<TaskEntity>>> =
        MediatorLiveData<Map<String, List<TaskEntity>>>().apply {

            addSource(allTasks) { tasks ->
                viewModelScope.launch {

                    val map = withContext(Dispatchers.Default) {

                        val result = mutableMapOf<String, List<TaskEntity>>()
                        val today = LocalDate.now()

                        for (i in -14..14) {
                            val date = today.plusDays(i.toLong()).toString()
                            result[date] =CommonMethods.filterTasks(tasks, date)
                        }

                        result
                    }

                    postValue(map)
                }

            }

        }

    val barUiState: LiveData<
            Pair<Map<String, List<TaskEntity>>, Map<String, Map<String, Int>>>
            > =
        MediatorLiveData<
                Pair<Map<String, List<TaskEntity>>, Map<String, Map<String, Int>>>
                >().apply {

            var taskMap: Map<String, List<TaskEntity>>? = null
            var completion: Map<String, Map<String, Int>>? = null

            fun update() {
                val t = taskMap ?: return
                val c = completion ?: return
                value = t to c
            }

            addSource(barTasksByDate) {
                taskMap = it
                update()
            }

            addSource(completionMap) {
                completion = it
                update()
            }
        }


    val dayScoreTrigger:
            LiveData<Triple<List<TaskEntity>, Map<String, Map<String, Int>>, String>> =
        MediatorLiveData<Triple<List<TaskEntity>, Map<String, Map<String, Int>>, String>>().apply {

            var tasks: List<TaskEntity>? = null
            var completion: Map<String, Map<String, Int>>? = null
            var date: String? = null

            fun update() {
                val t = tasks ?: return
                val c = completion ?: return
                val d = date ?: return
                value = Triple(t, c, d)
            }

            addSource(allTasks) {
                tasks = it
                update()
            }

            addSource(completionMap) {
                completion = it
                update()
            }

            addSource(selectedDate) {
                date = it
                update()
            }
        }


    val scoreTrigger:
            LiveData<Triple<List<TaskEntity>, Map<String, Map<String, Int>>, String>> =
        MediatorLiveData<Triple<List<TaskEntity>, Map<String, Map<String, Int>>, String>>().apply {

            var tasks: List<TaskEntity>? = null
            var completion: Map<String, Map<String, Int>>? = null
            var date: String? = null

            fun update() {
                val t = tasks ?: return
                val c = completion ?: return
                val d = date ?: return
                value = Triple(t, c, d)
            }

            addSource(allTasks) {
                tasks = it
                update()
            }

            addSource(completionMap) {
                completion = it
                update()
            }

            addSource(selectedDate) {
                date = it
                update()
            }
        }


    fun logTaskReorder(effectiveFromDate: String, taskIds: List<String>) {
        viewModelScope.launch {
            val today = CommonMethods.getTodayDate()
            repository.logReorder(today, effectiveFromDate, taskIds)
        }
    }

    fun updateTasks(tasks: List<TaskEntity>) = viewModelScope.launch {
        repository.updateTasks(tasks)
    }

    fun deleteTasks(tasks: List<TaskEntity>) = viewModelScope.launch {
        repository.deleteTasks(tasks)
    }


    fun getAllDailyTasks(): LiveData<List<TaskEntity>> {
        return repository.getAllDailyTasks()
    }

    fun getAllCompletionsTaskData(): LiveData<List<TaskCompletionEntity>> {
        return repository.getAllCompletionsTaskData()
    }

    fun getCompletionsForTask(taskId: String): LiveData<List<TaskCompletionEntity>> {
        return repository.getCompletionsForTask(taskId)
    }


    fun deleteCompletionsBefore(taskId: String, newStartDate: String) {
        viewModelScope.launch {
            repository.deleteCompletionsBefore(taskId, newStartDate)
        }
    }


    val heatmapUiList = MediatorLiveData<List<TaskHeatmapUi>>().apply {
        var latestTasks: List<TaskEntity>? = null
        var latestCompletions: List<TaskCompletionEntity>? = null

        fun combineAndPublish() {

            viewModelScope.launch {

                val result = withContext(Dispatchers.Default) {

                    val tasks = latestTasks ?: return@withContext emptyList()
                    val completions = latestCompletions ?: emptyList()

                    val completionMap = groupCompletions(completions)

                    tasks.map { task ->
                        val normalizedTaskId = task.id.trim().lowercase()
                        TaskHeatmapUi(
                            task = task,
                            completedDates = completionMap[normalizedTaskId] ?: emptySet()
                        )
                    }
                }

                value = result
            }
        }


        addSource(repository.getAllDailyTasks()) { tasks ->
            latestTasks = tasks
            combineAndPublish()
        }

        addSource(repository.getAllCompletionsTaskData()) { completions ->
            latestCompletions = completions
            combineAndPublish()
        }
    }


    // somewhere common (object DateUtils or companion)
    val DATE_FORMAT = "yyyy-MM-dd"
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)

    private fun groupCompletions(
        list: List<TaskCompletionEntity>
    ): Map<String, Set<LocalDate>> {
        // return empty map quickly if no completions
        if (list.isEmpty()) return emptyMap()

        return list.mapNotNull { entity ->
            val normalizedId = entity.taskId.trim().lowercase()
            try {
                val parsed = LocalDate.parse(entity.date, DATE_FORMATTER)
                normalizedId to parsed
            } catch (e: DateTimeParseException) {
                Log.w(
                    "groupCompletions",
                    "Skipping bad date='${entity.date}' for taskId='$normalizedId'"
                )
                null
            }
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    }


    //diary work ----------
    val allEntries: LiveData<List<DiaryEntry>> = repository.allEntries

    fun insert(entry: DiaryEntry) = viewModelScope.launch {
        repository.insert(entry)
    }

    fun update(entry: DiaryEntry) = viewModelScope.launch {
        repository.update(entry)
    }

    fun delete(entry: DiaryEntry) = viewModelScope.launch {
        repository.delete(entry)
    }

    fun getEntryById(id: String, onResult: (DiaryEntry?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getEntryById(id))
        }
    }

    fun clearAllDiaryData() = viewModelScope.launch {
        repository.clearAll()
    }
    //day note ----------

    // Insert note (UI se call karna ho to coroutine scope use hoga)
    fun insertDayNote(note: DayNoteEntity) {
        viewModelScope.launch {
            repository.insertDayNote(note)
        }
    }

    fun updateDayNote(note: DayNoteEntity) {
        viewModelScope.launch {
            repository.updateDayNote(note)
        }
    }

    fun deleteDayNote(note: DayNoteEntity) {
        viewModelScope.launch {
            repository.deleteDayNote(note)
        }
    }

    // Ek note fetch karna (specific taskId + date)
    //will be use in add task
    private val _noteForDate = MutableLiveData<DayNoteEntity?>()
    val noteForDate: LiveData<DayNoteEntity?> = _noteForDate

    fun getNoteForDate(taskId: String, date: String) {
        viewModelScope.launch {
            val note = repository.getNoteForDate(taskId, date)
            _noteForDate.postValue(note)
        }
    }

    fun getNoteForDateOnce(taskId: String, date: String, callback: (DayNoteEntity?) -> Unit) {
        viewModelScope.launch {
            val note = repository.getNoteForDate(taskId, date)
            callback(note)
        }
    }

    // Ek date ke saare notes fetch karna
    //will be use in add diary
    private val _notesOnDate = MutableLiveData<List<DayNoteEntity>>()
    val notesOnDate: LiveData<List<DayNoteEntity>> = _notesOnDate

    fun getNotesOnDate(date: String) {
        viewModelScope.launch {
            val notes = repository.getNotesOnDate(date)
            _notesOnDate.postValue(notes)
        }
    }

    //mood history
    val allMoods: LiveData<List<MoodHistoryItem>> = repository.allMoods
    fun insertMood(mood: MoodHistoryItem) = viewModelScope.launch {
        repository.insert(mood)
    }

    fun updateMood(mood: MoodHistoryItem) = viewModelScope.launch {
        repository.update(mood)
    }

    fun deleteMood(mood: MoodHistoryItem) = viewModelScope.launch {
        repository.delete(mood)
    }

    fun clearAllMood() = viewModelScope.launch {
        repository.clearAllMood()
    }

    fun getTodaysMoodLive(todayDate: String): LiveData<MoodHistoryItem?> {
        return repository.getMoodByDateLive(todayDate)
    }


    //list
    fun insertList(list: ListEntity) {
        viewModelScope.launch {
            repository.insertList(list)
        }
    }

    val allLists: LiveData<List<ListEntity>> =
        repository.getAllLists()

    fun updateList(list: ListEntity) {
        viewModelScope.launch {
            repository.updateList(list)
        }
    }

    fun getListIdsForTask(
        taskId: String,
        onResult: (List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val ids = repository.getListIdsForTask(taskId)
            onResult(ids)
        }
    }


    fun addTaskToList(listId: String, taskId: String) {
        viewModelScope.launch {
            repository.addTaskToList(listId, taskId)
        }
    }

    fun removeTaskFromList(listId: String, taskId: String) {
        viewModelScope.launch {
            repository.removeTaskFromList(listId, taskId)
        }
    }

    fun getListWithTasks(listId: String): LiveData<ListWithTasks> {
        return repository.getListWithTasks(listId)
    }

    fun getTaskIdsForList(
        listId: String,
        onResult: (List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val ids = repository.getTaskIdsForList(listId)
            onResult(ids)
        }
    }


    fun saveTasksForList(listId: String, taskIds: List<String>) {
        viewModelScope.launch {
            repository.syncTasksForList(listId, taskIds)
        }
    }


    //condition date


    //combined data
    val dayLogs: LiveData<List<DayLogEntity>> =
        repository.getCombinedData().asLiveData()


    sealed class SaveResult {
        object Idle : SaveResult()
        object Loading : SaveResult()
        data class Success(val isNewTask: Boolean) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    private val _saveResult = MutableLiveData<SaveResult>(SaveResult.Idle)  // 👈 Init with Idle
    val saveResult: LiveData<SaveResult> = _saveResult

    // 👈 New: Public reset fun for fragment to call before save
    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
    }

    fun saveOrUpdateTask(
        task: TaskEntity,
        date: String,
        isEdit: Boolean,
        originalScheduledTime: String?
    ) = viewModelScope.launch {

        _saveResult.value = SaveResult.Loading

        try {

            withContext(Dispatchers.Default) {

                if (!isEdit) {
                    insertAndReorderTask(task, date, isEdit = false)
                } else {

                    val timeChanged = originalScheduledTime != task.scheduledTime
                    repository.updateTask(task)   // direct repository call

                    if (timeChanged && task.scheduledTime != null) {
                        insertAndReorderTask(task, date, isEdit = true)
                    }
                }
            }

            _saveResult.value = SaveResult.Success(isNewTask = !isEdit)

        } catch (e: Exception) {

            Log.e("AddTaskDebug", "SaveOrUpdate failed: ${e.message}", e)
            _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")

        }
    }


    private suspend fun insertAndReorderTask(
        task: TaskEntity,
        date: String,
        isEdit: Boolean
    ) {

        val currentTasks = getTasksForDate(date)

        val nullPositions = mutableMapOf<Int, TaskEntity>()
        val timed = mutableListOf<TaskEntity>()

        currentTasks.forEachIndexed { index, item ->
            if (item.scheduledTime == null) {
                nullPositions[index] = item
            } else {
                timed.add(item)
            }
        }

        val updatedTasks: List<TaskEntity> =
            if (!task.scheduledTime.isNullOrBlank()) {

                val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

                try {

                    if (isEdit) {
                        timed.removeAll { it.id == task.id }
                        nullPositions.entries.removeIf { it.value.id == task.id }
                    }

                    timed.add(task)

                    timed.sortBy {
                        LocalTime.parse(it.scheduledTime!!, timeFormatter)
                    }

                    val newItems = mutableListOf<TaskEntity>()
                    var timedIndex = 0
                    val totalSlots =
                        if (isEdit) currentTasks.size
                        else currentTasks.size + 1

                    for (i in 0 until totalSlots) {
                        if (nullPositions.containsKey(i)) {
                            newItems.add(nullPositions[i]!!)
                        } else if (timedIndex < timed.size) {
                            newItems.add(timed[timedIndex++])
                        }
                    }

                    while (timedIndex < timed.size) {
                        newItems.add(timed[timedIndex++])
                    }

                    newItems

                } catch (e: DateTimeParseException) {
                    fallbackRebuild(currentTasks, task, isEdit)
                }

            } else {
                fallbackRebuild(currentTasks, task, isEdit)
            }

        if (!isEdit) {
            repository.insertTask(task)   // direct repository call
        }

        val orderedIds = updatedTasks.map { it.id }
        repository.logReorder(CommonMethods.getTodayDate(), date, orderedIds)
    }


    // 👈 Helper: Fallback rebuild for null/invalid time or simple append/replace
    private fun fallbackRebuild(
        currentTasks: List<TaskEntity>,
        task: TaskEntity,
        isEdit: Boolean
    ): List<TaskEntity> {
        return if (isEdit) {
            // Edit: Replace in place
            currentTasks.map { if (it.id == task.id) task else it }
        } else {
            // New: Append at end
            currentTasks.toMutableList().apply { add(task) }
        }
    }
}

