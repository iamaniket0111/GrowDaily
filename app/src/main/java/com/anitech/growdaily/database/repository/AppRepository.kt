package com.anitech.growdaily.database.repository

import androidx.lifecycle.LiveData
import com.anitech.growdaily.data_class.ChecklistProgressItemEntity
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskExtraDateEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.data_class.UntilCompleteChildEntity
import com.anitech.growdaily.database.dao.ChecklistProgressDao
import com.anitech.growdaily.database.dao.ListDao
import com.anitech.growdaily.database.dao.TaskCompletionDao
import com.anitech.growdaily.database.dao.TaskDao
import com.anitech.growdaily.database.dao.TaskDaySnapshotDao
import com.anitech.growdaily.database.dao.TaskExtraDateDao
import com.anitech.growdaily.database.dao.TaskTrackingVersionDao
import com.anitech.growdaily.database.dao.UntilCompleteChildDao
import com.anitech.growdaily.database.util.buildTaskDaySnapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate


class AppRepository(
    private val taskDao: TaskDao,
    private val listDao: ListDao,
    internal  val completionDao: TaskCompletionDao,
    private val checklistProgressDao: ChecklistProgressDao,
    private val taskTrackingVersionDao: TaskTrackingVersionDao,
    private val taskDaySnapshotDao: TaskDaySnapshotDao,
    private val taskExtraDateDao: TaskExtraDateDao,
    private val untilCompleteChildDao: UntilCompleteChildDao
) {
    private var snapshotSyncJob: Job? = null
    private val snapshotTaskSignatures = mutableMapOf<String, Int>()
    //day score
    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTask(task: TaskEntity) {
        val snapshotTaskIds = linkedSetOf(task.id)
        taskExtraDateDao.deleteAllForTask(task.id)
        val untilChildren = untilCompleteChildDao.getAllNowForParent(task.id)
        untilCompleteChildDao.deleteAllForParent(task.id)
        untilChildren.forEach { child ->
            snapshotTaskIds += child.childTaskId
            completionDao.deleteAllForTask(child.childTaskId)
            checklistProgressDao.deleteAllForTask(child.childTaskId)
        }
        completionDao.deleteAllForTask(task.id)
        checklistProgressDao.deleteAllForTask(task.id)
        removeTaskFromAllLists(task.id)
        snapshotTaskIds.forEach(snapshotTaskSignatures::remove)
        taskDaySnapshotDao.deleteForTasks(snapshotTaskIds.toList())
        taskDao.deleteTask(task)
    }

    suspend fun deleteRepeatSeries(task: TaskEntity) {
        val seriesId = task.seriesId.ifBlank { task.id }
        val segments = taskDao.getAllTasksNow()
            .filter { it.seriesId.ifBlank { it.id } == seriesId }
        segments.forEach { deleteTask(it) }
        snapshotTaskSignatures.remove(seriesId)
    }

    private fun invalidateTaskSnapshotCache(taskId: String) {
        snapshotTaskSignatures.remove(taskId)
    }

    fun getTaskById(taskId: String): LiveData<TaskEntity> {
        return taskDao.getTaskById(taskId)
    }

    fun getAllTasksFlow(): Flow<List<TaskEntity>> =
        taskDao.getAllTasksFlow()

    fun getRepeatTasksFlow(): Flow<List<TaskEntity>> =
        taskDao.getRepeatTasksFlow()


    //complete task

    suspend fun addTimerDuration(taskId: String, date: String, seconds: Long) {
        completionDao.addDuration(taskId, date, seconds)
    }

    suspend fun updateChecklistState(taskId: String, date: String, json: String) {
        checklistProgressDao.deleteForTaskDate(taskId, date)
        val progressItems = parseChecklistProgressItems(taskId, date, json)
        if (progressItems.isNotEmpty()) {
            checklistProgressDao.upsertAll(progressItems)
        }
        completionDao.updateChecklist(taskId, date, json)
    }

    suspend fun upsertTaskTrackingVersion(entity: TaskTrackingVersionEntity) {
        taskTrackingVersionDao.upsert(entity)
    }

    fun getAllTaskTrackingVersionsFlow(): Flow<List<TaskTrackingVersionEntity>> {
        return taskTrackingVersionDao.getAllFlow()
    }

    fun getAllTaskDaySnapshotsFlow(): Flow<List<TaskDaySnapshotEntity>> {
        return taskDaySnapshotDao.getAllFlow()
    }

    fun getTaskDaySnapshotsBetweenFlow(startDate: String, endDate: String): Flow<List<TaskDaySnapshotEntity>> {
        return taskDaySnapshotDao.getBetweenFlow(startDate, endDate)
    }

    fun getAllTaskExtraDatesFlow(): Flow<List<TaskExtraDateEntity>> {
        return taskExtraDateDao.getAllFlow()
    }

    fun getCompletionsBetweenFlow(startDate: String, endDate: String): Flow<List<TaskCompletionEntity>> {
        return completionDao.getCompletionsBetweenFlow(startDate, endDate)
    }

    fun getAllUntilCompleteChildrenFlow(): Flow<List<UntilCompleteChildEntity>> {
        return untilCompleteChildDao.getAllFlow()
    }

    suspend fun addTaskForExtraDate(taskId: String, date: String) {
        taskExtraDateDao.upsert(TaskExtraDateEntity(taskId = taskId, date = date))
        invalidateTaskSnapshotCache(taskId)
    }

    suspend fun removeTaskFromExtraDate(taskId: String, date: String) {
        taskExtraDateDao.delete(taskId, date)
        resetCompletion(taskId, date)
        invalidateTaskSnapshotCache(taskId)
    }

    suspend fun addUntilCompleteChild(parentTaskId: String, date: String): UntilCompleteChildEntity {
        val existing = untilCompleteChildDao.getChildForParentDate(parentTaskId, date)
        if (existing != null) return existing
        val child = UntilCompleteChildEntity(
            childTaskId = java.util.UUID.randomUUID().toString(),
            parentTaskId = parentTaskId,
            taskAddedDate = date
        )
        untilCompleteChildDao.upsert(child)
        invalidateTaskSnapshotCache(parentTaskId)
        return child
    }

    suspend fun getUntilCompleteChildForParentDate(parentTaskId: String, date: String): UntilCompleteChildEntity? {
        return untilCompleteChildDao.getChildForParentDate(parentTaskId, date)
    }

    suspend fun removeUntilCompleteChild(child: UntilCompleteChildEntity) {
        untilCompleteChildDao.deleteByChildId(child.childTaskId)
        completionDao.deleteAllForTask(child.childTaskId)
        checklistProgressDao.deleteAllForTask(child.childTaskId)
        taskDaySnapshotDao.deleteForTasks(listOf(child.childTaskId))
        invalidateTaskSnapshotCache(child.parentTaskId)
    }

    suspend fun replaceTaskDaySnapshots(
        taskIds: List<String>,
        startDate: String,
        endDate: String,
        snapshots: List<TaskDaySnapshotEntity>
    ) {
        if (taskIds.isEmpty()) return
        taskDaySnapshotDao.replaceForTasks(taskIds, snapshots)
    }

    fun startTaskDaySnapshotSync(scope: CoroutineScope) {
        if (snapshotSyncJob != null) return

        snapshotSyncJob = scope.launch(Dispatchers.Default) {
            combine(
                getAllTasksFlow(),
                getAllCompletionsFlow(),
                getAllTaskTrackingVersionsFlow(),
                getAllTaskExtraDatesFlow()
            ) { tasks, completions, trackingVersions, extraDates ->
                SnapshotSyncInput(tasks, completions, trackingVersions, extraDates)
            }.collectLatest { input ->
                val tasks = input.tasks
                val completions = input.completions
                val trackingVersions = input.trackingVersions
                if (tasks.isEmpty()) {
                    snapshotTaskSignatures.clear()
                    taskDaySnapshotDao.clearAll()
                    return@collectLatest
                }

                val today = LocalDate.now()
                val end = today.plusDays(120)
                val completionEntityMap = completions
                    .groupBy { it.date }
                    .mapValues { entry -> entry.value.associateBy { it.taskId } }
                val completionByTaskId = completions.groupBy { it.taskId }
                val trackingVersionMap = trackingVersions
                    .groupBy { it.taskId }
                    .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }
                val extraDateMap = input.extraDates
                    .groupBy { it.taskId }
                    .mapValues { entry -> entry.value.map { it.date }.toSet() }
                val taskById = tasks.associateBy { it.id }
                val currentTaskIds = taskById.keys

                val removedTaskIds = snapshotTaskSignatures.keys - currentTaskIds
                if (removedTaskIds.isNotEmpty()) {
                    taskDaySnapshotDao.deleteForTasks(removedTaskIds.toList())
                    removedTaskIds.forEach(snapshotTaskSignatures::remove)
                }

                val changedTasks = tasks.filter { task ->
                    val signature = buildSnapshotTaskSignature(
                        task = task,
                        completions = completionByTaskId[task.id].orEmpty(),
                        trackingVersions = trackingVersionMap[task.id].orEmpty(),
                        extraDates = extraDateMap[task.id].orEmpty()
                    )
                    val previous = snapshotTaskSignatures[task.id]
                    if (previous != signature) {
                        snapshotTaskSignatures[task.id] = signature
                        true
                    } else {
                        false
                    }
                }

                if (changedTasks.isEmpty()) return@collectLatest

                val start = changedTasks.minOfOrNull {
                    runCatching { LocalDate.parse(it.taskAddedDate) }.getOrElse { today }
                } ?: today

                val snapshots = buildTaskDaySnapshots(
                    tasks = changedTasks,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionMap,
                    extraDateMap = extraDateMap,
                    startDate = start,
                    endDate = end
                )

                replaceTaskDaySnapshots(
                    taskIds = changedTasks.map { it.id },
                    startDate = start.toString(),
                    endDate = end.toString(),
                    snapshots = snapshots
                )
            }
        }
    }

    private data class SnapshotSyncInput(
        val tasks: List<TaskEntity>,
        val completions: List<TaskCompletionEntity>,
        val trackingVersions: List<TaskTrackingVersionEntity>,
        val extraDates: List<TaskExtraDateEntity>
    )

    private fun buildSnapshotTaskSignature(
        task: TaskEntity,
        completions: List<TaskCompletionEntity>,
        trackingVersions: List<TaskTrackingVersionEntity>,
        extraDates: Set<String>
    ): Int {
        var signature = 17
        signature = 31 * signature + task.hashCode()
        signature = 31 * signature + completions.hashCode()
        signature = 31 * signature + trackingVersions.hashCode()
        signature = 31 * signature + extraDates.hashCode()
        return signature
    }

    suspend fun markCompleted(taskId: String, date: String) {
        val existing = completionDao.isTaskCompletedOnDate(taskId, date)
        if (existing == null) {
            completionDao.insertCompletion(TaskCompletionEntity(taskId, date, count = 1))
        } else {
            val newCount = existing.count + 1
            completionDao.insertCompletion(TaskCompletionEntity(taskId, date, count = newCount))
        }
    }

    suspend fun incrementCompletion(taskId: String, date: String) {
        val existing = completionDao.isTaskCompletedOnDate(taskId, date)

        if (existing == null) {
            completionDao.insertCompletion(
                TaskCompletionEntity(taskId, date, count = 1)
            )
        } else {
            completionDao.insertCompletion(
                existing.copy(count = existing.count + 1)
            )
        }
    }

    suspend fun decrementCompletion(taskId: String, date: String) {
        val existing = completionDao.isTaskCompletedOnDate(taskId, date) ?: return

        if (existing.count <= 1) {
            completionDao.delete(taskId, date)
            checklistProgressDao.deleteForTaskDate(taskId, date)
        } else {
            completionDao.insertCompletion(
                existing.copy(count = existing.count - 1)
            )
        }
    }

    suspend fun resetCompletion(taskId: String, date: String) {
        completionDao.delete(taskId, date)
        checklistProgressDao.deleteForTaskDate(taskId, date)
    }

    fun getAllCompletionsFlow(): Flow<List<TaskCompletionEntity>> =
        completionDao.getAllCompletionsFlow()


    fun getAllCompletions(): LiveData<List<TaskCompletionEntity>> {
        return completionDao.getAllCompletions()
    }

    suspend fun getMaxManualOrder(): Int? {
        return taskDao.getMaxManualOrder()
    }

    suspend fun updateTaskOrder(taskId: String, order: Int) {
        taskDao.updateTaskOrder(taskId, order)
    }

    suspend fun updateManualOrderBatch(orderedIds: List<String>) {
        taskDao.updateManualOrderBatch(orderedIds)
    }

    //List
    suspend fun insertList(list: ListEntity) {
        listDao.insertList(list)
    }

    fun getAllLists(): LiveData<List<ListEntity>> {
        return listDao.getAllLists()
    }

    fun getAllListsFlow(): Flow<List<ListEntity>> {
        return listDao.getAllListsFlow()
    }

    suspend fun getAllListsSync(): List<ListEntity> {
        return listDao.getAllListsSync()
    }

    fun getTaskIdsForListFlow(listId: String): Flow<List<String>> {
        return listDao.getTaskIdsForListFlow(listId)
    }

    suspend fun updateList(list: ListEntity) {
        listDao.updateList(list)
    }

    suspend fun updateListOrder(lists: List<ListEntity>) {
        listDao.updateListOrderBatch(lists)
    }

    suspend fun addTaskToList(listId: String, taskId: String) {
        listDao.insertListTask(
            ListTaskCrossRef(
                listId = listId,
                taskId = taskId
            )
        )
    }

    suspend fun syncTasksForList(
        listId: String,
        newTaskIds: List<String>
    ) {
        listDao.syncTasksForListBatch(listId, newTaskIds)
    }

    suspend fun getTaskIdsForList(listId: String): List<String> {
        return listDao.getTaskIdsForList(listId)
    }

    suspend fun getListIdsForTask(taskId: String): List<String> {
        return listDao.getListIdsForTask(taskId)
    }

    fun getAllListTaskCrossRefsFlow(): Flow<List<ListTaskCrossRef>> {
        return listDao.getAllListTaskCrossRefsFlow()
    }

    suspend fun deleteList(list: ListEntity) {
        listDao.deleteAllTaskRefsForList(list.id)   // clean up cross-ref rows first
        listDao.deleteList(list)
    }

    suspend fun removeTaskFromAllLists(taskId: String) {
        listDao.removeTaskFromAllLists(taskId)
    }

    suspend fun clearTaskHistoryBeforeDate(taskId: String, date: String) {
        completionDao.deleteCompletionsBeforeDate(taskId, date)
        completionDao.delete(taskId, date)
        taskDaySnapshotDao.deleteSnapshotsBeforeDate(taskId, date)
        taskTrackingVersionDao.deleteVersionsBeforeDate(taskId, date)
        checklistProgressDao.deleteBefore(taskId, date)
        checklistProgressDao.deleteForTaskDate(taskId, date)
        invalidateTaskSnapshotCache(taskId)
    }

    private fun parseChecklistProgressItems(
        taskId: String,
        date: String,
        json: String
    ): List<ChecklistProgressItemEntity> {
        return try {
            val array = org.json.JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val label = item.optString("label").trim()
                    if (label.isNotEmpty()) {
                        add(
                            ChecklistProgressItemEntity(
                                taskId = taskId,
                                date = date,
                                label = label,
                                isDone = item.optBoolean("done", false)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }


}
