package com.anitech.growdaily.database

import android.util.Log
import androidx.lifecycle.*
import com.anitech.growdaily.CommonMethods.Companion.filterTasks
import com.anitech.growdaily.data_class.ConditionEntity
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.data_class.DateItemEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.MoodHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.switchMap
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.CommonMethods.Companion.filterTasksByCondition
import com.anitech.growdaily.data_class.DayLogEntity


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



    val allTasks: LiveData<List<DailyTask>> = repository.getAllTasks()  // 👇 Ye LiveData<...> return karega

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

    fun clearAllMood()=viewModelScope.launch {
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

}
