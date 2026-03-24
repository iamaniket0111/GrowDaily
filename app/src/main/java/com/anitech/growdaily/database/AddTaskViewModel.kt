package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(AddTaskUiState())
    val uiState: LiveData<AddTaskUiState> = _uiState

    // For list selection
    private val _selectedListIds = MutableLiveData<List<String>>(emptyList())
    val selectedListIds: LiveData<List<String>> = _selectedListIds

    // All lists from repository
    val allLists: LiveData<List<ListEntity>> = repository.getAllLists()

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value?.copy(title = title)
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value?.copy(note = note)
    }

    fun updateStartDate(date: String) {
        _uiState.value = _uiState.value?.copy(startDate = date)
    }

    fun updateSchedule(time: String?, isScheduled: Boolean) {
        _uiState.value = _uiState.value?.copy(
            scheduleTime = time,
            isScheduled = isScheduled
        )
    }

    fun updateReminder(time: String?, isReminderEnabled: Boolean) {
        _uiState.value = _uiState.value?.copy(
            reminderTime = time,
            isReminderEnabled = isReminderEnabled
        )
    }

    fun updateWeight(weight: TaskWeight) {
        _uiState.value = _uiState.value?.copy(weight = weight)
    }

    fun updateIconAndColor(icon: String, color: String) {
        _uiState.value = _uiState.value?.copy(icon = icon, color = color)
    }

    fun updateSelectedLists(ids: List<String>) {
        _selectedListIds.value = ids
    }

    fun loadTaskForEdit(task: TaskEntity) {
        _uiState.value = AddTaskUiState(
            title = task.title,
            note = task.note ?: "",
            startDate = task.taskAddedDate,
            scheduleTime = task.scheduledTime,
            reminderTime = task.reminderTime,
            isScheduled = task.isScheduled,
            isReminderEnabled = task.reminderEnabled,
            weight = task.weight,
            icon = task.iconResId,
            color = task.colorCode,
            isLoading = false,
            errorMessage = null,
            isSaved = false
        )
    }

    fun loadTaskListIds(taskId: String, onComplete: (List<String>) -> Unit) {
        viewModelScope.launch {
            val ids = repository.getListIdsForTask(taskId)
            _selectedListIds.value = ids
            onComplete(ids)
        }
    }

    fun saveTask(
        isEdit: Boolean,
        existingId: String?,
        taskType: TaskType,
        onComplete: (Boolean, String?) -> Unit
    ) {

        val currentState = _uiState.value ?: return

        if (currentState.title.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Title is required")
            onComplete(false, "Title is required")
            return
        }

        viewModelScope.launch {

            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)

            try {

                val scheduledMinutes = CommonMethods.timeToMinutes(currentState.scheduleTime)

                val manualOrder = if (isEdit) {
                    currentState.manualOrder   // ✅ No DB call
                } else {
                    val maxOrder = repository.getMaxManualOrder() ?: 0
                    maxOrder + 1
                }

                val task = TaskEntity(
                    id = existingId ?: UUID.randomUUID().toString(),
                    title = currentState.title,
                    note = currentState.note.ifBlank { null },
                    weight = currentState.weight,
                    scheduledTime = currentState.scheduleTime,
                    reminderTime = currentState.reminderTime,
                    reminderEnabled = currentState.isReminderEnabled,
                    isScheduled = currentState.isScheduled,
                    taskAddedDate = currentState.startDate,
                    taskRemovedDate = null,
                    iconResId = currentState.icon,
                    colorCode = currentState.color,
                    taskType = taskType,
                    repeatType = null,
                    repeatDays = null,
                    dailyTargetCount = 1,
                    manualOrder = manualOrder,
                    scheduledMinutes = scheduledMinutes
                )

                if (isEdit) {
                    repository.updateTask(task)
                } else {
                    repository.insertTask(task)
                }

                updateTaskLists(task.id, _selectedListIds.value ?: emptyList())

                _uiState.value = currentState.copy(
                    isLoading = false,
                    isSaved = true
                )

                onComplete(true, null)

            } catch (e: Exception) {

                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = e.message
                )

                onComplete(false, e.message)
            }
        }
    }




    private suspend fun updateTaskLists(taskId: String, listIds: List<String>) {
        // Remove all existing list memberships for this task in one shot.
        // This avoids relying on LiveData.value (which can be null inside a coroutine)
        // and prevents UNIQUE constraint crashes on re-insert.
        repository.removeTaskFromAllLists(taskId)

        // Then add to the newly selected lists
        listIds.forEach { listId ->
            repository.addTaskToList(listId, taskId)
        }
    }

    fun deleteCompletionsBefore(taskId: String, newStartDate: String) {
        viewModelScope.launch {
            repository.deleteCompletionsBefore(taskId, newStartDate)
        }
    }

    fun resetSaveState() {
        _uiState.value = _uiState.value?.copy(isSaved = false, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value?.copy(errorMessage = null)
    }
    //delete task

    fun insertList(list: ListEntity) = viewModelScope.launch {
        repository.insertList(list)
    }

    fun deleteTask(task: TaskEntity) = viewModelScope.launch {
        repository.deleteTask(task)
    }

    fun updateTask(task: TaskEntity) = viewModelScope.launch {
        repository.updateTask(task)
    }
}