package com.anitech.growdaily.database.repository

import androidx.lifecycle.LiveData
import com.anitech.growdaily.data_class.ChecklistProgressItemEntity
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import com.anitech.growdaily.database.dao.ChecklistProgressDao
import com.anitech.growdaily.database.dao.ListDao
import com.anitech.growdaily.database.dao.TaskCompletionDao
import com.anitech.growdaily.database.dao.TaskDao
import com.anitech.growdaily.database.dao.TaskDaySnapshotDao
import com.anitech.growdaily.database.dao.TaskTrackingVersionDao
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
    private val taskDaySnapshotDao: TaskDaySnapshotDao
) {
    private var snapshotSyncJob: Job? = null
    //day score
    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)

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

    suspend fun replaceTaskDaySnapshots(
        taskIds: List<String>,
        startDate: String,
        endDate: String,
        snapshots: List<TaskDaySnapshotEntity>
    ) {
        if (taskIds.isEmpty()) return
        taskDaySnapshotDao.deleteForTasks(taskIds)
        if (snapshots.isNotEmpty()) {
            taskDaySnapshotDao.upsertAll(snapshots)
        }
    }

    fun startTaskDaySnapshotSync(scope: CoroutineScope) {
        if (snapshotSyncJob != null) return

        snapshotSyncJob = scope.launch(Dispatchers.Default) {
            combine(
                getAllTasksFlow(),
                getAllCompletionsFlow(),
                getAllTaskTrackingVersionsFlow()
            ) { tasks, completions, trackingVersions ->
                Triple(tasks, completions, trackingVersions)
            }.collectLatest { (tasks, completions, trackingVersions) ->
                if (tasks.isEmpty()) {
                    taskDaySnapshotDao.clearAll()
                    return@collectLatest
                }

                val today = LocalDate.now()
                val start = tasks.minOfOrNull {
                    runCatching { LocalDate.parse(it.taskAddedDate) }.getOrElse { today }
                } ?: today
                val end = today.plusDays(120)
                val completionEntityMap = completions
                    .groupBy { it.date }
                    .mapValues { entry -> entry.value.associateBy { it.taskId } }
                val trackingVersionMap = trackingVersions
                    .groupBy { it.taskId }
                    .mapValues { entry -> entry.value.sortedBy { it.effectiveFromDate } }

                val snapshots = buildTaskDaySnapshots(
                    tasks = tasks,
                    completionEntityMap = completionEntityMap,
                    trackingVersionsMap = trackingVersionMap,
                    startDate = start,
                    endDate = end
                )

                replaceTaskDaySnapshots(
                    taskIds = tasks.map { it.id },
                    startDate = start.toString(),
                    endDate = end.toString(),
                    snapshots = snapshots
                )
            }
        }
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

    suspend fun deleteCompletionsBefore(taskId: String, newStartDate: String) {
        completionDao.deleteCompletionsBefore(taskId, newStartDate)
        checklistProgressDao.deleteBefore(taskId, newStartDate)
    }

    suspend fun getMaxManualOrder(): Int? {
        return taskDao.getMaxManualOrder()
    }

    suspend fun updateTaskOrder(taskId: String, order: Int) {
        taskDao.updateTaskOrder(taskId, order)
    }

    //List
    suspend fun insertList(list: ListEntity) {
        listDao.insertList(list)
    }

    fun getAllLists(): LiveData<List<ListEntity>> {
        return listDao.getAllLists()
    }
    fun getTaskIdsForListFlow(listId: String): Flow<List<String>> {
        return listDao.getTaskIdsForListFlow(listId)
    }


    suspend fun updateList(list: ListEntity) {
        listDao.updateList(list)
    }

    suspend fun updateListOrder(lists: List<ListEntity>) {
        listDao.updateLists(lists)
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
        val oldTaskIds = listDao.getTaskIdsForList(listId)

        val toAdd = newTaskIds.minus(oldTaskIds.toSet())
        val toRemove = oldTaskIds.minus(newTaskIds.toSet())

        toAdd.forEach { taskId ->
            listDao.insertListTask(
                ListTaskCrossRef(listId, taskId)
            )
        }

        toRemove.forEach { taskId ->
            listDao.deleteListTask(
                ListTaskCrossRef(listId, taskId)
            )
        }
    }

    suspend fun getTaskIdsForList(listId: String): List<String> {
        return listDao.getTaskIdsForList(listId)
    }

    suspend fun getListIdsForTask(taskId: String): List<String> {
        return listDao.getListIdsForTask(taskId)
    }

    suspend fun deleteList(list: ListEntity) {
        listDao.deleteAllTaskRefsForList(list.id)   // clean up cross-ref rows first
        listDao.deleteList(list)
    }

    suspend fun removeTaskFromAllLists(taskId: String) {
        listDao.removeTaskFromAllLists(taskId)
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
