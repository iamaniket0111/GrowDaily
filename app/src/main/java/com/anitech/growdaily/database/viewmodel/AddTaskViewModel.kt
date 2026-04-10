package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.util.resolveTrackingSettings
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.UUID

class AddTaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(AddTaskUiState())
    val uiState: LiveData<AddTaskUiState> = _uiState

    private val _selectedListIds = MutableLiveData<List<String>>(emptyList())
    val selectedListIds: LiveData<List<String>> = _selectedListIds

    val allLists: LiveData<List<ListEntity>> = repository.getAllLists()

    // ── Basic field updaters ──────────────────────────────────────────────────

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
        _uiState.value = _uiState.value?.copy(scheduleTime = time, isScheduled = isScheduled)
    }

    fun updateReminder(time: String?, isReminderEnabled: Boolean) {
        _uiState.value = _uiState.value?.copy(reminderTime = time, isReminderEnabled = isReminderEnabled)
    }

    fun updateWeight(weight: TaskWeight) {
        _uiState.value = _uiState.value?.copy(weight = weight)
    }

    fun updateIconAndColor(icon: String, color: String) {
        _uiState.value = _uiState.value?.copy(icon = icon, color = color)
    }

    fun updateShowUntilCompleted(enabled: Boolean) {
        _uiState.value = _uiState.value?.copy(showUntilCompleted = enabled)
    }

    fun updateShowMissedOnGapDays(enabled: Boolean) {
        _uiState.value = _uiState.value?.copy(showMissedOnGapDays = enabled)
    }

    fun updateSelectedLists(ids: List<String>) {
        _selectedListIds.value = ids
    }

    fun updateRepeatConfig(type: RepeatType, days: List<Int>) {
        _uiState.value = _uiState.value?.copy(
            repeatType = type,
            repeatDays = days.distinct().sorted()
        )
    }

    // ── Tracking type updaters ────────────────────────────────────────────────

    fun updateTrackingType(type: TrackingType) {
        _uiState.value = _uiState.value?.copy(trackingType = type)
    }

    fun updateDailyTargetCount(count: Int) {
        _uiState.value = _uiState.value?.copy(dailyTargetCount = count.coerceAtLeast(1))
    }

    fun updateTargetDurationSeconds(seconds: Long) {
        _uiState.value = _uiState.value?.copy(targetDurationSeconds = seconds.coerceAtLeast(60L))
    }

    fun addChecklistItem(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value?.checklistItems ?: emptyList()
        _uiState.value = _uiState.value?.copy(checklistItems = current + trimmed)
    }

    fun removeChecklistItem(index: Int) {
        val current = _uiState.value?.checklistItems?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value?.copy(checklistItems = current)
        }
    }

    // ── Load for edit ─────────────────────────────────────────────────────────

    fun loadTaskForEdit(task: TaskEntity) {
        // Parse stored checklistItems JSON back to List<String>
        val parsedChecklist: List<String> = if (
            task.trackingType == TrackingType.CHECKLIST &&
            !task.checklistItems.isNullOrBlank()
        ) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(task.checklistItems, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

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
            showUntilCompleted = task.showUntilCompleted,
            showMissedOnGapDays = task.showMissedOnGapDays,
            // ── tracking ──
            trackingType = task.trackingType,
            dailyTargetCount = task.dailyTargetCount.coerceAtLeast(1),
            targetDurationSeconds = task.targetDurationSeconds.coerceAtLeast(60L),
            checklistItems = parsedChecklist,
            repeatType = task.repeatType ?: RepeatType.DAILY,
            repeatDays = CommonMethods.parseRepeatDays(task.repeatDays),
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

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveTask(
        isEdit: Boolean,
        existingId: String?,
        taskType: TaskType,
        originalTask: TaskEntity?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val currentState = _uiState.value ?: return

        if (currentState.title.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Title is required")
            onComplete(false, "Title is required")
            return
        }

        // CHECKLIST validation — must have at least one item
        if (currentState.trackingType == TrackingType.CHECKLIST &&
            currentState.checklistItems.isEmpty()
        ) {
            _uiState.value = currentState.copy(errorMessage = "Add at least one checklist item")
            onComplete(false, "Add at least one checklist item")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)

            try {
                val scheduledMinutes = CommonMethods.timeToMinutes(currentState.scheduleTime)
                val today = CommonMethods.getTodayDate()
                val shouldSplitRepeatSegment = shouldSplitRepeatSegmentFromToday(
                    isEdit = isEdit,
                    taskType = taskType,
                    originalTask = originalTask,
                    currentState = currentState,
                    today = today
                )

                val manualOrder = if (isEdit) currentState.manualOrder
                else (repository.getMaxManualOrder() ?: 0) + 1

                // Serialize checklist labels to JSON for storage
                val checklistJson = if (currentState.trackingType == TrackingType.CHECKLIST) {
                    Gson().toJson(currentState.checklistItems)
                } else null

                val taskId = if (shouldSplitRepeatSegment) UUID.randomUUID().toString()
                else existingId ?: UUID.randomUUID().toString()
                val seriesId = originalTask?.seriesId?.ifBlank { null } ?: taskId
                val taskAddedDate = if (shouldSplitRepeatSegment) today else currentState.startDate
                val taskRemovedDate = when {
                    shouldSplitRepeatSegment -> null
                    isEdit -> originalTask?.taskRemovedDate
                    else -> null
                }

                val task = TaskEntity(
                    id = taskId,
                    seriesId = seriesId,
                    title = currentState.title,
                    note = currentState.note.ifBlank { null },
                    weight = currentState.weight,
                    scheduledTime = currentState.scheduleTime,
                    reminderTime = currentState.reminderTime,
                    reminderEnabled = currentState.isReminderEnabled,
                    isScheduled = currentState.isScheduled,
                    taskAddedDate = taskAddedDate,
                    taskRemovedDate = taskRemovedDate,
                    iconResId = currentState.icon,
                    colorCode = currentState.color,
                    taskType = taskType,
                    showUntilCompleted = taskType == TaskType.DAY && currentState.showUntilCompleted,
                    showMissedOnGapDays = taskType == TaskType.DAILY && currentState.showMissedOnGapDays,
                    repeatType = if (taskType == TaskType.DAILY) currentState.repeatType else null,
                    repeatDays = if (taskType == TaskType.DAILY) {
                        CommonMethods.serializeRepeatDays(currentState.repeatDays)
                    } else null,
                    dailyTargetCount = if (currentState.trackingType == TrackingType.COUNT)
                        currentState.dailyTargetCount else 0,
                    manualOrder = manualOrder,
                    scheduledMinutes = scheduledMinutes,
                    trackingType = currentState.trackingType,
                    checklistItems = checklistJson,
                    targetDurationSeconds = if (currentState.trackingType == TrackingType.TIMER)
                        currentState.targetDurationSeconds else 0L
                )

                if (shouldSplitRepeatSegment && originalTask != null) {
                    repository.updateTask(
                        originalTask.copy(taskRemovedDate = CommonMethods.getYesterdayDate())
                    )
                    repository.insertTask(task)
                } else if (isEdit) {
                    repository.updateTask(task)
                } else {
                    repository.insertTask(task)
                }

                maybeSaveTrackingVersion(
                    task = task,
                    isEdit = isEdit,
                    originalTask = originalTask,
                    checklistJson = checklistJson
                )

                if (shouldSplitRepeatSegment) {
                    (_selectedListIds.value ?: emptyList()).forEach { listId ->
                        repository.addTaskToList(listId, task.id)
                    }
                } else {
                    updateTaskLists(task.id, _selectedListIds.value ?: emptyList())
                }

                _uiState.value = currentState.copy(isLoading = false, isSaved = true)
                onComplete(true, null)

            } catch (e: Exception) {
                _uiState.value = currentState.copy(isLoading = false, errorMessage = e.message)
                onComplete(false, e.message)
            }
        }
    }

    private suspend fun updateTaskLists(taskId: String, listIds: List<String>) {
        repository.removeTaskFromAllLists(taskId)
        listIds.forEach { listId -> repository.addTaskToList(listId, taskId) }
    }

    fun deleteCompletionsBefore(taskId: String, newStartDate: String) {
        viewModelScope.launch { repository.deleteCompletionsBefore(taskId, newStartDate) }
    }

    fun resetSaveState() {
        _uiState.value = _uiState.value?.copy(isSaved = false, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value?.copy(errorMessage = null)
    }

    fun insertList(list: ListEntity) = viewModelScope.launch { repository.insertList(list) }
    fun deleteTask(task: TaskEntity) = viewModelScope.launch { repository.deleteTask(task) }
    fun updateTask(task: TaskEntity) = viewModelScope.launch { repository.updateTask(task) }

    fun resumeDailyTask(task: TaskEntity) = viewModelScope.launch {
        val today = CommonMethods.getTodayDate()
        val resumedTask = task.copy(
            id = UUID.randomUUID().toString(),
            seriesId = task.seriesId.ifBlank { task.id },
            taskAddedDate = today,
            taskRemovedDate = null
        )

        repository.insertTask(resumedTask)

        val listIds = repository.getListIdsForTask(task.id)
        listIds.forEach { listId ->
            repository.addTaskToList(listId, resumedTask.id)
        }

        maybeSaveTrackingVersion(
            task = resumedTask,
            isEdit = false,
            originalTask = null,
            checklistJson = resumedTask.checklistItems
        )
    }

    private suspend fun maybeSaveTrackingVersion(
        task: TaskEntity,
        isEdit: Boolean,
        originalTask: TaskEntity?,
        checklistJson: String?
    ) {
        val today = CommonMethods.getTodayDate()
        val weightChanged = !isEdit || originalTask == null || originalTask.weight != task.weight
        val trackingChanged = when (task.trackingType) {
            TrackingType.COUNT ->
                !isEdit || originalTask == null || originalTask.dailyTargetCount != task.dailyTargetCount

            TrackingType.TIMER ->
                !isEdit || originalTask == null ||
                    originalTask.targetDurationSeconds != task.targetDurationSeconds

            TrackingType.CHECKLIST ->
                !isEdit || originalTask == null ||
                    parseChecklistItems(originalTask.checklistItems) != parseChecklistItems(checklistJson)

            TrackingType.BINARY -> false
        }
        val changed = weightChanged || trackingChanged

        if (!changed) return

        val effectiveDate = if (
            isEdit &&
            task.taskType == TaskType.DAILY &&
            originalTask != null &&
            originalTask.taskAddedDate < today
        ) {
            today
        } else {
            task.taskAddedDate
        }

        if (
            isEdit &&
            originalTask != null &&
            originalTask.taskAddedDate < effectiveDate &&
            weightChanged
        ) {
            repository.upsertTaskTrackingVersion(
                TaskTrackingVersionEntity(
                    taskId = task.id,
                    effectiveFromDate = originalTask.taskAddedDate,
                    weightValue = originalTask.weight.weight,
                    dailyTargetCount = originalTask.dailyTargetCount,
                    targetDurationSeconds = originalTask.targetDurationSeconds,
                    checklistItemsJson = originalTask.checklistItems
                )
            )
        }

        repository.upsertTaskTrackingVersion(
            TaskTrackingVersionEntity(
                taskId = task.id,
                effectiveFromDate = effectiveDate,
                weightValue = task.weight.weight,
                dailyTargetCount = task.dailyTargetCount,
                targetDurationSeconds = task.targetDurationSeconds,
                checklistItemsJson = checklistJson
            )
        )
    }

    private fun parseChecklistItems(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun shouldSplitRepeatSegmentFromToday(
        isEdit: Boolean,
        taskType: TaskType,
        originalTask: TaskEntity?,
        currentState: AddTaskUiState,
        today: String
    ): Boolean {
        if (!isEdit || taskType != TaskType.DAILY || originalTask == null) return false
        if (originalTask.taskAddedDate >= today) return false
        if (!CommonMethods.isWithinTaskLifetime(originalTask, today)) return false

        return isRepeatScheduleChanged(originalTask, currentState)
    }

    private fun isRepeatScheduleChanged(
        originalTask: TaskEntity,
        currentState: AddTaskUiState
    ): Boolean {
        return originalTask.repeatType != currentState.repeatType ||
            CommonMethods.parseRepeatDays(originalTask.repeatDays) != currentState.repeatDays.distinct().sorted() ||
            originalTask.showMissedOnGapDays != currentState.showMissedOnGapDays
    }
}
