package com.anitech.growdaily.database.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.enum_class.AddTaskUiEvent
import com.anitech.growdaily.enum_class.AddTaskValidationError
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskInactiveReason
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import com.anitech.growdaily.util.AddTaskDirtyStateTracker
import com.anitech.growdaily.util.AddTaskSaveValidator
import com.anitech.growdaily.util.AddTaskStateNormalizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class AddTaskViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private data class SaveTaskContext(
        val taskId: String,
        val seriesId: String,
        val taskAddedDate: String,
        val requestedEndDate: String?,
        val taskRemovedDate: String?,
        val inactiveReason: TaskInactiveReason?,
        val manualOrder: Int,
        val scheduledMinutes: Int?,
        val checklistJson: String?,
        val shouldSplitRepeatSegment: Boolean
    )

    /** Survives rotation; used for discard-changes detection. */
    val dirtyStateTracker = AddTaskDirtyStateTracker()

    /** True after the user picked a custom icon/color (survives rotation). */
    var hasUserSelectedTaskAppearance: Boolean = false

    private var isFormInitialized = false
    private var areListIdsInitialized = false

    private val _uiState = MutableStateFlow(AddTaskUiState())
    val uiState: StateFlow<AddTaskUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddTaskUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AddTaskUiEvent> = _events.asSharedFlow()

    private val _selectedListIds = MutableStateFlow<List<String>>(emptyList())
    val selectedListIds: StateFlow<List<String>> = _selectedListIds.asStateFlow()

    val allLists: LiveData<List<ListEntity>> = repository.getAllLists()

    private fun emitEvent(event: AddTaskUiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    // ── Basic field updaters ──────────────────────────────────────────────────

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun updateStartDate(date: String) {
        _uiState.update { current ->
            val sanitizedEndDate = current.endDate?.let { endDate ->
                val start = runCatching { LocalDate.parse(date) }.getOrNull()
                val end = runCatching { LocalDate.parse(endDate) }.getOrNull()
                if (start != null && end != null && end.isBefore(start)) null else endDate
            }
            current.copy(startDate = date, endDate = sanitizedEndDate)
        }
    }

    fun updateEndDate(date: String?) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun updateSchedule(time: String?, isScheduled: Boolean) {
        _uiState.update { it.copy(scheduleTime = time, isScheduled = isScheduled) }
    }

    fun updateEndTime(time: String?) {
        _uiState.update { it.copy(endTime = time) }
    }

    fun updateReminder(time: String?, isReminderEnabled: Boolean) {
        _uiState.update { it.copy(reminderTime = time, isReminderEnabled = isReminderEnabled) }
    }

    fun updateWeight(weight: TaskWeight) {
        _uiState.update { it.copy(weight = weight) }
    }

    fun updateIconAndColor(icon: String, color: String) {
        _uiState.update { it.copy(icon = icon, color = color) }
    }

    fun updateShowUntilCompleted(enabled: Boolean) {
        _uiState.update { it.copy(showUntilCompleted = enabled) }
    }



    fun updateSelectedLists(ids: List<String>) {
        _selectedListIds.value = ids
    }

    fun updateRepeatConfig(type: RepeatType, days: List<Int>) {
        _uiState.update {
            it.copy(repeatType = type, repeatDays = days.distinct().sorted())
        }
    }

    // ── Tracking type updaters ────────────────────────────────────────────────

    fun updateTrackingType(type: TrackingType) {
        _uiState.update { it.copy(trackingType = type) }
    }

    fun updateDailyTargetCount(count: Int) {
        _uiState.update { it.copy(dailyTargetCount = count.coerceAtLeast(1)) }
    }

    fun updateTargetDurationSeconds(seconds: Long) {
        _uiState.update { it.copy(targetDurationSeconds = seconds.coerceAtLeast(60L)) }
    }

    fun addChecklistItem(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value.checklistItems
        if (current.contains(trimmed)) {
            emitEvent(AddTaskUiEvent.ShowValidationError(AddTaskValidationError.CHECKLIST_DUPLICATE))
            return
        }
        _uiState.update { it.copy(checklistItems = current + trimmed) }
    }

    fun updateChecklistItem(index: Int, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value.checklistItems.toMutableList()
        if (index in current.indices) {
            current[index] = trimmed
            _uiState.update { it.copy(checklistItems = current) }
        }
    }

    fun moveChecklistItem(from: Int, to: Int) {
        val current = _uiState.value.checklistItems.toMutableList()
        if (from in current.indices && to in current.indices) {
            val item = current.removeAt(from)
            current.add(to, item)
            _uiState.update { it.copy(checklistItems = current) }
        }
    }

    fun removeChecklistItem(index: Int) {
        val current = _uiState.value.checklistItems.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.update { it.copy(checklistItems = current) }
        }
    }

    // ── Load for edit ─────────────────────────────────────────────────────────

    fun loadTaskForEdit(task: TaskEntity) {
        val parsedChecklist: List<String> = if (
            task.trackingType == TrackingType.CHECKLIST &&
            !task.checklistItems.isNullOrBlank()
        ) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(task.checklistItems, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        _uiState.value = AddTaskUiState(
            title = task.title,
            note = task.note ?: "",
            startDate = task.taskAddedDate,
            endDate = if (task.taskType == TaskType.DAILY && task.inactiveReason == TaskInactiveReason.ENDED) {
                task.taskRemovedDate
            } else {
                null
            },
            scheduleTime = task.scheduledTime,
            endTime = task.endTime,
            reminderTime = task.reminderTime,
            isScheduled = task.isScheduled,
            isReminderEnabled = task.reminderEnabled,
            weight = task.weight,
            icon = task.iconResId,
            color = task.colorCode,
            showUntilCompleted = task.showUntilCompleted,
            trackingType = task.trackingType,
            dailyTargetCount = task.dailyTargetCount.coerceAtLeast(1),
            targetDurationSeconds = task.targetDurationSeconds.coerceAtLeast(60L),
            checklistItems = parsedChecklist,
            repeatType = task.repeatType ?: RepeatType.DAILY,
            repeatDays = CommonMethods.parseRepeatDays(task.repeatDays),
            isLoading = false,
            manualOrder = task.manualOrder
        )
    }

    fun ensureEditTaskLoaded(task: TaskEntity) {
        if (isFormInitialized) return
        loadTaskForEdit(task)
        isFormInitialized = true
    }

    fun ensureAddFormInitialized() {
        if (isFormInitialized) return
        isFormInitialized = true
    }

    /** Call once after the form has applied accent defaults and other initial UI sync. */
    fun captureAddModeDirtyBaseline() {
        if (dirtyStateTracker.hasStateBaseline()) return
        dirtyStateTracker.markAddModeReady(_uiState.value)
    }

    fun ensureListIdsLoaded(taskId: String, onComplete: (List<String>) -> Unit) {
        if (areListIdsInitialized) {
            onComplete(_selectedListIds.value)
            return
        }
        viewModelScope.launch {
            val ids = repository.getListIdsForTask(taskId)
            _selectedListIds.value = ids
            dirtyStateTracker.onListIdsLoaded(ids)
            areListIdsInitialized = true
            onComplete(ids)
        }
    }

    fun loadTaskListIds(taskId: String, onComplete: (List<String>) -> Unit) {
        ensureListIdsLoaded(taskId, onComplete)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun buildDraftTaskEntity(existingId: String?, taskType: TaskType): TaskEntity {
        val normalized = AddTaskStateNormalizer.forPersistence(_uiState.value)
        val today = CommonMethods.getTodayDate()
        val taskId = existingId ?: java.util.UUID.randomUUID().toString()
        val checklistJson = if (normalized.trackingType == TrackingType.CHECKLIST && normalized.checklistItems.isNotEmpty()) {
            com.google.gson.Gson().toJson(normalized.checklistItems)
        } else null

        val startMins = normalized.scheduleTime?.let { CommonMethods.timeToMinutes(it) }
        val durationMins = if (normalized.targetDurationSeconds > 0L) (normalized.targetDurationSeconds / 60L).toInt() else 15
        val calcEndTime = normalized.endTime ?: startMins?.let { CommonMethods.minutesToTime((it + durationMins) % 1440) }

        return TaskEntity(
            id = taskId,
            seriesId = taskId,
            title = normalized.title.ifBlank { "New Habit" },
            note = normalized.note.ifBlank { null },
            weight = normalized.weight,
            scheduledTime = normalized.scheduleTime,
            endTime = calcEndTime,
            reminderTime = normalized.reminderTime,
            reminderEnabled = normalized.isReminderEnabled,
            isScheduled = normalized.isScheduled,
            taskAddedDate = today,
            taskRemovedDate = null,
            inactiveReason = null,
            iconResId = normalized.icon,
            colorCode = normalized.color,
            taskType = taskType,
            showUntilCompleted = taskType == TaskType.DAY && normalized.showUntilCompleted,
            repeatType = if (taskType == TaskType.DAILY) normalized.repeatType else null,
            repeatDays = if (taskType == TaskType.DAILY) CommonMethods.serializeRepeatDays(normalized.repeatDays) else null,
            dailyTargetCount = if (normalized.trackingType == TrackingType.COUNT) normalized.dailyTargetCount else 0,
            manualOrder = 0,
            scheduledMinutes = startMins,
            trackingType = normalized.trackingType,
            checklistItems = checklistJson,
            targetDurationSeconds = if (normalized.trackingType == TrackingType.TIMER) normalized.targetDurationSeconds else 0L
        )
    }

    fun saveTask(
        isEdit: Boolean,
        existingId: String?,
        taskType: TaskType,
        originalTask: TaskEntity?
    ) {
        val normalized = AddTaskStateNormalizer.forPersistence(_uiState.value)

        AddTaskSaveValidator.validate(normalized, taskType)?.let { error ->
            emitEvent(AddTaskUiEvent.ShowValidationError(error))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val today = CommonMethods.getTodayDate()
                val saveContext = buildSaveTaskContext(
                    isEdit = isEdit,
                    existingId = existingId,
                    taskType = taskType,
                    originalTask = originalTask,
                    currentState = normalized,
                    today = today
                )
                if (saveContext.requestedEndDate != null && saveContext.requestedEndDate < saveContext.taskAddedDate) {
                    _uiState.update { it.copy(isLoading = false) }
                    emitEvent(AddTaskUiEvent.ShowValidationError(AddTaskValidationError.END_DATE_BEFORE_START))
                    return@launch
                }

                val task = TaskEntity(
                    id = saveContext.taskId,
                    seriesId = saveContext.seriesId,
                    title = normalized.title,
                    note = normalized.note.ifBlank { null },
                    weight = normalized.weight,
                    scheduledTime = normalized.scheduleTime,
                    endTime = normalized.endTime,
                    reminderTime = normalized.reminderTime,
                    reminderEnabled = normalized.isReminderEnabled,
                    isScheduled = normalized.isScheduled,
                    taskAddedDate = saveContext.taskAddedDate,
                    taskRemovedDate = saveContext.taskRemovedDate,
                    inactiveReason = saveContext.inactiveReason,
                    iconResId = normalized.icon,
                    colorCode = normalized.color,
                    taskType = taskType,
                    showUntilCompleted = taskType == TaskType.DAY && normalized.showUntilCompleted,
                    repeatType = if (taskType == TaskType.DAILY) normalized.repeatType else null,
                    repeatDays = if (taskType == TaskType.DAILY) {
                        CommonMethods.serializeRepeatDays(normalized.repeatDays)
                    } else {
                        null
                    },
                    dailyTargetCount = if (normalized.trackingType == TrackingType.COUNT) {
                        normalized.dailyTargetCount
                    } else {
                        0
                    },
                    manualOrder = saveContext.manualOrder,
                    scheduledMinutes = saveContext.scheduledMinutes,
                    trackingType = normalized.trackingType,
                    checklistItems = saveContext.checklistJson,
                    targetDurationSeconds = if (normalized.trackingType == TrackingType.TIMER) {
                        normalized.targetDurationSeconds
                    } else {
                        0L
                    }
                )

                persistTask(task, isEdit, originalTask, saveContext.shouldSplitRepeatSegment)

                maybeSaveTrackingVersion(
                    task = task,
                    isEdit = isEdit,
                    originalTask = originalTask,
                    checklistJson = saveContext.checklistJson
                )

                syncTaskLists(task.id, _selectedListIds.value, saveContext.shouldSplitRepeatSegment)

                _uiState.update { it.copy(isLoading = false) }
                emitEvent(AddTaskUiEvent.Saved(isEdit))
                emitEvent(AddTaskUiEvent.NavigateBack)

            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                emitEvent(
                    AddTaskUiEvent.ShowMessage(
                        message = "",
                        retrySave = true
                    )
                )
            }
        }
    }

    fun restartProgressDirectly(
        existingId: String,
        taskType: TaskType,
        originalTask: TaskEntity
    ) {
        val normalized = AddTaskStateNormalizer.forPersistence(_uiState.value)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val today = CommonMethods.getTodayDate()

                val task = TaskEntity(
                    id = existingId,
                    seriesId = originalTask.seriesId.ifBlank { existingId },
                    title = normalized.title,
                    note = normalized.note.ifBlank { null },
                    weight = normalized.weight,
                    scheduledTime = normalized.scheduleTime,
                    endTime = normalized.endTime,
                    reminderTime = normalized.reminderTime,
                    reminderEnabled = normalized.isReminderEnabled,
                    isScheduled = normalized.isScheduled,
                    taskAddedDate = today,
                    taskRemovedDate = originalTask.taskRemovedDate,
                    inactiveReason = originalTask.inactiveReason,
                    iconResId = normalized.icon,
                    colorCode = normalized.color,
                    taskType = taskType,
                    showUntilCompleted = false,
                    repeatType = normalized.repeatType,
                    repeatDays = CommonMethods.serializeRepeatDays(normalized.repeatDays),
                    dailyTargetCount = if (normalized.trackingType == TrackingType.COUNT) {
                        normalized.dailyTargetCount
                    } else {
                        0
                    },
                    manualOrder = originalTask.manualOrder,
                    scheduledMinutes = CommonMethods.timeToMinutes(normalized.scheduleTime),
                    trackingType = normalized.trackingType,
                    checklistItems = if (normalized.trackingType == TrackingType.CHECKLIST) {
                        Gson().toJson(normalized.checklistItems)
                    } else {
                        null
                    },
                    targetDurationSeconds = if (normalized.trackingType == TrackingType.TIMER) {
                        normalized.targetDurationSeconds
                    } else {
                        0L
                    }
                )

                repository.updateTask(task)
                repository.clearTaskHistoryBeforeDate(task.id, today)

                maybeSaveTrackingVersion(
                    task = task,
                    isEdit = true,
                    originalTask = originalTask,
                    checklistJson = task.checklistItems,
                    restartProgress = true
                )

                updateTaskLists(task.id, _selectedListIds.value)

                _uiState.update { it.copy(isLoading = false, startDate = today) }
                emitEvent(
                    AddTaskUiEvent.ShowMessage(
                        message = "Progress restarted from today",
                        retrySave = false
                    )
                )
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                emitEvent(
                    AddTaskUiEvent.ShowMessage(
                        message = "Failed to restart progress",
                        retrySave = false
                    )
                )
            }
        }
    }

    private suspend fun buildSaveTaskContext(
        isEdit: Boolean,
        existingId: String?,
        taskType: TaskType,
        originalTask: TaskEntity?,
        currentState: AddTaskUiState,
        today: String
    ): SaveTaskContext {
        val shouldSplitRepeatSegment = shouldSplitRepeatSegmentFromToday(
            isEdit = isEdit,
            taskType = taskType,
            originalTask = originalTask,
            currentState = currentState,
            today = today
        )
        val taskId = if (shouldSplitRepeatSegment) {
            UUID.randomUUID().toString()
        } else {
            existingId ?: UUID.randomUUID().toString()
        }
        val taskAddedDate = if (shouldSplitRepeatSegment) today else currentState.startDate
        val requestedEndDate = if (taskType == TaskType.DAILY) currentState.endDate else null

        return SaveTaskContext(
            taskId = taskId,
            seriesId = originalTask?.seriesId?.ifBlank { null } ?: taskId,
            taskAddedDate = taskAddedDate,
            requestedEndDate = requestedEndDate,
            taskRemovedDate = when {
                taskType == TaskType.DAILY -> {
                    if (originalTask?.inactiveReason == TaskInactiveReason.PAUSED && !shouldSplitRepeatSegment) {
                        originalTask.taskRemovedDate
                    } else {
                        requestedEndDate
                    }
                }
                isEdit -> originalTask?.taskRemovedDate
                else -> null
            },
            inactiveReason = when {
                taskType == TaskType.DAILY -> {
                    if (originalTask?.inactiveReason == TaskInactiveReason.PAUSED && !shouldSplitRepeatSegment) {
                        TaskInactiveReason.PAUSED
                    } else if (requestedEndDate != null) {
                        TaskInactiveReason.ENDED
                    } else {
                        null
                    }
                }
                isEdit -> originalTask?.inactiveReason
                else -> null
            },
            manualOrder = if (isEdit) {
                currentState.manualOrder
            } else {
                (repository.getMaxManualOrder() ?: 0) + 1
            },
            scheduledMinutes = CommonMethods.timeToMinutes(currentState.scheduleTime),
            checklistJson = if (currentState.trackingType == TrackingType.CHECKLIST) {
                Gson().toJson(currentState.checklistItems)
            } else {
                null
            },
            shouldSplitRepeatSegment = shouldSplitRepeatSegment
        )
    }

    private suspend fun persistTask(
        task: TaskEntity,
        isEdit: Boolean,
        originalTask: TaskEntity?,
        shouldSplitRepeatSegment: Boolean
    ) {
        if (shouldSplitRepeatSegment && originalTask != null) {
            repository.updateTask(
                originalTask.copy(
                    taskRemovedDate = CommonMethods.getYesterdayDate(),
                    inactiveReason = null
                )
            )
            repository.insertTask(task)
        } else if (isEdit) {
            repository.updateTask(task)
        } else {
            repository.insertTask(task)
        }
    }

    private suspend fun syncTaskLists(
        taskId: String,
        selectedListIds: List<String>,
        shouldSplitRepeatSegment: Boolean
    ) {
        if (shouldSplitRepeatSegment) {
            selectedListIds.forEach { listId ->
                repository.addTaskToList(listId, taskId)
            }
        } else {
            updateTaskLists(taskId, selectedListIds)
        }
    }

    private suspend fun updateTaskLists(taskId: String, listIds: List<String>) {
        repository.removeTaskFromAllLists(taskId)
        listIds.forEach { listId -> repository.addTaskToList(listId, taskId) }
    }

    fun insertList(list: ListEntity) = viewModelScope.launch { repository.insertList(list) }

    fun deleteTask(task: TaskEntity, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteTask(task)
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }

    fun updateTask(task: TaskEntity, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateTask(task)
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }

    fun resumeDailyTask(task: TaskEntity, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val today = CommonMethods.getTodayDate()
                val yesterday = LocalDate.parse(today).minusDays(1).toString()
                if ((task.inactiveReason == TaskInactiveReason.PAUSED || task.inactiveReason == TaskInactiveReason.ENDED) &&
                    (task.taskRemovedDate == today || task.taskRemovedDate == yesterday)) {
                    val resumedTask = task.copy(
                        taskRemovedDate = null,
                        inactiveReason = null
                    )
                    repository.updateTask(resumedTask)
                } else {
                    val resumedTask = task.copy(
                        id = UUID.randomUUID().toString(),
                        seriesId = task.seriesId.ifBlank { task.id },
                        taskAddedDate = today,
                        taskRemovedDate = null,
                        inactiveReason = null
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
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }

    private suspend fun maybeSaveTrackingVersion(
        task: TaskEntity,
        isEdit: Boolean,
        originalTask: TaskEntity?,
        checklistJson: String?,
        restartProgress: Boolean = false
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

        if (!changed && !restartProgress) return

        val effectiveDate = if (
            isEdit &&
            originalTask != null &&
            originalTask.taskAddedDate < today &&
            task.taskType == TaskType.DAILY
        ) {
            today
        } else {
            task.taskAddedDate
        }

        if (
            !restartProgress &&
            isEdit &&
            originalTask != null &&
            originalTask.taskAddedDate < effectiveDate &&
            (weightChanged || trackingChanged)
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
            CommonMethods.parseRepeatDays(originalTask.repeatDays) != currentState.repeatDays.distinct().sorted()
    }
}
