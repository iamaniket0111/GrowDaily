package com.anitech.growdaily.database

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.CommonMethods.Companion.filterTasks
import com.anitech.growdaily.CommonMethods.Companion.filterTasksByCondition
import com.anitech.growdaily.data_class.ConditionEntity
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.data_class.DateItemEntity
import com.anitech.growdaily.data_class.DayLogEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.MoodHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException


class AppViewModel(private val repository: AppRepository) : ViewModel() {

    fun insertTask(task: DailyTask) = viewModelScope.launch {
        repository.insertTask(task)
    }

    fun updateTask(task: DailyTask) = viewModelScope.launch {
        repository.updateTask(task)
    }

    fun deleteTask(task: DailyTask) = viewModelScope.launch {
        repository.deleteTask(task)
    }

    fun clearAllTasks() = viewModelScope.launch {//to delete all data, have to work on it further
        repository.clearAllTasks()
    }

    fun getAllTasks() = viewModelScope.launch {
        repository.getAllTasks()
    }


//    val filteredTasks: LiveData<List<DailyTask>> = selectedDate.switchMap { date ->
//        if (date == null) {
//            MutableLiveData(emptyList())
//        } else {
//            allTasks.map { tasks -> filterTasks(tasks, date) }
//        }
//    }


    val allTasks: LiveData<List<DailyTask>> =
        repository.getAllTasks()  // 👇 Ye LiveData<...> return karega

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    // 👇 Mediator same, but ab allTasks source auto-trigger hoga (DB load pe)
    val filteredTasksByCondition = MediatorLiveData<List<DailyTask>>().apply {
        var currentTasks: List<DailyTask>? = null
        var currentDate: String? = null

        fun update() {
            Log.d("ViewModelDebug", "Update called: tasks=${currentTasks?.size}, date=$currentDate")
            val tasks = currentTasks ?: return
            val date = currentDate ?: return
            viewModelScope.launch {
                try {
                    Log.d("ViewModelDebug", "Fetching log for date: $date")
                    val latestLog = repository.getLatestLogForDate(date)
                    Log.d("ViewModelDebug", "Latest log: ${latestLog?.taskIds?.size ?: 0} ids")

                    val orderedTasks = if (latestLog != null) {
                        val orderedIds = latestLog.taskIds
                        val sorted = tasks.sortedBy { task ->
                            val index = orderedIds.indexOf(task.id)
                            if (index >= 0) index else Int.MAX_VALUE
                        }
                        Log.d("ViewModelDebug", "Ordered tasks size: ${sorted.size}")
                        sorted
                    } else {
                        Log.d("ViewModelDebug", "No log, using default sort")
                        tasks.sortedBy { it.id }
                    }

                    val dateItem = repository.getDateItem(date)
                    Log.d("ViewModelDebug", "DateItem: ${dateItem != null}")

                    val filtered = if (dateItem != null) {
                        val result = filterTasksByCondition(orderedTasks, date, dateItem)
                        Log.d("ViewModelDebug", "Filtered by condition: ${result.size} tasks")
                        result
                    } else {
                        val result = filterTasks(orderedTasks, date)
                        Log.d("ViewModelDebug", "Filtered basic: ${result.size} tasks")
                        result
                    }

                    postValue(filtered)
                    Log.d("ViewModelDebug", "Posted filtered value: ${filtered.size}")
                } catch (e: Exception) {
                    Log.e("ViewModelDebug", "Error in update: ${e.message}", e)
                }
            }
        }

        // Sources same – ab allTasks repo se LiveData hai, to initial emit pe "AllTasks source" log aayega
        addSource(allTasks) { tasks: List<DailyTask>? ->
            Log.d("ViewModelDebug", "AllTasks source: ${tasks?.size} tasks")  // 👇 Ye ab fire hoga
            currentTasks = tasks
            update()
        }

        addSource(selectedDate) { date: String? ->
            Log.d("ViewModelDebug", "SelectedDate source: $date")
            currentDate = date
            update()
        }
    }

    suspend fun getTasksForDate(date: String): List<DailyTask> {
        val all = allTasks.value ?: emptyList()
        val latestLog = repository.getLatestLogForDate(date)
        return if (latestLog != null) {
            val orderedIds = latestLog.taskIds
            all.sortedBy { orderedIds.indexOf(it.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        } else {
            all
        }
    }

    // Aur setDate me bhi log
    fun setDate(date: String) {
        Log.d("ViewModelDebug", "setDate called with: $date")
        _selectedDate.value = date
    }
    //date and task item

    fun logTaskReorder(effectiveFromDate: String, taskIds: List<String>) {
        viewModelScope.launch {
            val today = CommonMethods.getTodayDate()
            repository.logReorder(today, effectiveFromDate, taskIds)
        }
    }

    //deletion work
    fun updateTasks(tasks: List<DailyTask>) = viewModelScope.launch {
        repository.updateTasks(tasks)
    }

    fun deleteTasks(tasks: List<DailyTask>) = viewModelScope.launch {
        repository.deleteTasks(tasks)
    }

    fun getTasksByCondition(conditionId: Int): LiveData<List<DailyTask>> {//to get task of specific condition
        return repository.getDailyTasksByCondition(conditionId)
    }

    suspend fun getTasksByConditionDirect(conditionId: Int): List<DailyTask> { //not live
        return repository.getDailyTasksByConditionDirect(conditionId)
    }

    fun getAllDailyTasks(): LiveData<List<DailyTask>> {
        return repository.getAllDailyTasks()
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


    //condition
    fun getAllConditions(): LiveData<List<ConditionEntity>> {
        return repository.getAll()
    }

    val allConditionsLiv: LiveData<List<ConditionEntity>> = repository.getAllConditions()

    fun insertAll(conditions: List<ConditionEntity>) {
        viewModelScope.launch {
            repository.insertAll(conditions)
        }
    }

    fun updateCondition(condition: ConditionEntity) {
        viewModelScope.launch {
            repository.updateCondition(condition)
        }
    }

    fun updateConditions(conditions: List<ConditionEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateConditions(conditions)
        }
    }

    //condition date
    fun insertDateItem(dateItem: DateItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertDateItem(dateItem)
        }
    }

    suspend fun getDateItem(date: String): DateItemEntity? {
        return repository.getDateItem(date)
    }

    fun deleteDateItem(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDateItem(date)
        }
    }

    fun upsertDateItem(dateItem: DateItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertDateItem(dateItem)
        }
    }

    fun removeConditionFromDate(date: String, conditionType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeConditionFromDate(date, conditionType)
        }
    }

    val dateEntity: LiveData<DateItemEntity?> = selectedDate.switchMap { date ->
        if (date == null) {
            MutableLiveData(null)
        } else {
            repository.getDateItemObs(date) // <- LiveData<DateItemEntity?>
        }
    }

    //combined data
    val dayLogs: LiveData<List<DayLogEntity>> =
        repository.getCombinedData().asLiveData()

    //reorder
    // In AppViewModel.kt – add these imports if not present


// Existing class AppViewModel me yeh function add kar:

// Updated ViewModel Code (AppViewModel.kt) - Add these

    // 👈 New: StateFlow/LiveData for result (use MutableLiveData for simplicity, import androidx.lifecycle.MutableLiveData)

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
        task: DailyTask,
        date: String,
        isEdit: Boolean,
        originalScheduledTime: String?
    ) = viewModelScope.launch {
        _saveResult.value = SaveResult.Idle  // Clear before start
        _saveResult.value = SaveResult.Loading

        try {
            if (!isEdit) {
                insertAndReorderTask(task, date, isEdit = false)
                _saveResult.value = SaveResult.Success(isNewTask = true)
            } else {
                val timeChanged = originalScheduledTime != task.scheduledTime
                updateTask(task)

                if (timeChanged && task.scheduledTime != null) {
                    Log.d("AddTaskDebug", "Time changed during edit, triggering reorder")
                    insertAndReorderTask(task, date, isEdit = true)
                } else {
                    Log.d("AddTaskDebug", "Edit without time change, no reorder needed")
                }
                _saveResult.value = SaveResult.Success(isNewTask = false)
            }
        } catch (e: Exception) {
            Log.e("AddTaskDebug", "SaveOrUpdate failed: ${e.message}", e)
            _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
        } finally {
            _saveResult.value = SaveResult.Idle  // Reset always
        }
    }

    private suspend fun insertAndReorderTask(task: DailyTask, date: String, isEdit: Boolean) {
        val currentTasks = getTasksForDate(date)
        Log.d("AddTaskDebug", "Reorder triggered. Edit mode: $isEdit, Current tasks: ${currentTasks.size}")

        val nullPositions = mutableMapOf<Int, DailyTask>()
        val timed = mutableListOf<DailyTask>()

        currentTasks.forEachIndexed { index, item ->
            if (item.scheduledTime == null) {
                nullPositions[index] = item
            } else {
                timed.add(item)
            }
        }

        val updatedTasks: List<DailyTask> = if (!task.scheduledTime.isNullOrBlank()) {
            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
            try {
                if (isEdit) {
                    timed.removeAll { it.id == task.id }
                    nullPositions.entries.removeIf { it.value.id == task.id }
                }
                timed.add(task)

                timed.sortBy { LocalTime.parse(it.scheduledTime!!, timeFormatter) }
                Log.d("AddTaskDebug", "Sorted timed times: ${timed.map { it.scheduledTime }}")

                val newItems = mutableListOf<DailyTask>()
                var timedIndex = 0
                val totalSlots = if (isEdit) currentTasks.size else currentTasks.size + 1
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
                Log.e("AddTaskDebug", "Invalid time: ${task.scheduledTime}, fallback")
                fallbackRebuild(currentTasks, task, isEdit)
            }
        } else {
            Log.d("AddTaskDebug", "Null time task")
            fallbackRebuild(currentTasks, task, isEdit)
        }

        if (!isEdit) {
            insertTask(task)
        }

        val orderedIds = updatedTasks.map { it.id }
        logTaskReorder(date, orderedIds)

        val action = if (isEdit) "updated" else "added"
        val pos = updatedTasks.indexOf(task)
        Log.d("AddTaskDebug", "Task $action, final pos: $pos, total: ${updatedTasks.size}")
    }


    // 👈 Helper: Fallback rebuild for null/invalid time or simple append/replace
    private fun fallbackRebuild(
        currentTasks: List<DailyTask>,
        task: DailyTask,
        isEdit: Boolean
    ): List<DailyTask> {
        return if (isEdit) {
            // Edit: Replace in place
            currentTasks.map { if (it.id == task.id) task else it }
        } else {
            // New: Append at end
            currentTasks.toMutableList().apply { add(task) }
        }
    }
}
