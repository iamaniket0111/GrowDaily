package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import com.anitech.growdaily.TaskDao
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.DayLogEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.ListWithTasks
import com.anitech.growdaily.data_class.MoodHistoryItem
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskOrderChangeLog
import com.anitech.growdaily.enum_class.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn


class AppRepository(
    private val dao: TaskDao,
    private val diaryEntryDao: DiaryEntryDao,
    private val moodDao: MoodHistoryDao,
    private val listDao: ListDao,
    private val orderLogDao: OrderLogDao,
    private val completionDao: TaskCompletionDao
) {
    //day score
    suspend fun insertTask(task: TaskEntity) = dao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)

    suspend fun deleteTask(task: TaskEntity) = dao.deleteTask(task)

    suspend fun clearAllTasks() = dao.clearAllTasks()

    fun getAllTasksFlow(): Flow<List<TaskEntity>> =
        dao.getAllTasksFlow()


    suspend fun updateTasks(tasks: List<TaskEntity>) = dao.updateTasks(tasks)

    suspend fun deleteTasks(tasks: List<TaskEntity>) = dao.deleteTasks(tasks)

    fun getAllDailyTasks(): LiveData<List<TaskEntity>> {
        return dao.getAllDailyTasks()
    }

    //complete task
    suspend fun isTaskCompleted(taskId: String, date: String): Boolean {
        return completionDao.isTaskCompletedOnDate(taskId, date) != null
    }

    suspend fun getFirstCompletionDate(taskId: String): String? {
        return completionDao.getFirstCompletionDate(taskId)
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
        } else {
            completionDao.insertCompletion(
                existing.copy(count = existing.count - 1)
            )
        }
    }

    suspend fun resetCompletion(taskId: String, date: String) {
        completionDao.delete(taskId, date)
    }


    suspend fun removeAllCompletionsForDate(taskId: String, date: String) {
        completionDao.delete(taskId, date)
    }


    suspend fun removeCompletion(taskId: String, date: String) {
        completionDao.delete(taskId, date)
    }

    fun getAllCompletions(): LiveData<List<TaskCompletionEntity>> {
        return completionDao.getAllCompletions()
    }

    fun getAllCompletionsTaskData(): LiveData<List<TaskCompletionEntity>> {
        return completionDao.getAllCompletionsTaskData()
    }

    suspend fun deleteCompletionsBefore(taskId: String, newStartDate: String) {
        completionDao.deleteCompletionsBefore(taskId, newStartDate)
    }

    fun getCompletionsForTask(taskId: String): LiveData<List<TaskCompletionEntity>> {
        return completionDao.getCompletionsForTask(taskId)
    }


    fun calculateDailyScoreForDatePure(
        tasks: List<TaskEntity>,
        completionMapForDate: Map<String, Int>
    ): String {

        var totalWeight = 0f
        var completedWeight = 0f

        for (task in tasks) {

            val weight = task.weight.weight
            totalWeight += weight

            val count = completionMapForDate[task.id] ?: 0
            val target = task.dailyTargetCount.coerceAtLeast(1)

            if (count >= target) {
                completedWeight += weight
            }
        }

        val score =
            if (totalWeight > 0f) {
                ((completedWeight / totalWeight) * 10f * 10)
                    .roundToInt() / 10f
            } else 0f

        return if (score % 1f == 0f)
            score.toInt().toString()
        else
            String.format("%.1f", score)
    }

    //diary
    val allEntries: LiveData<List<DiaryEntry>> = diaryEntryDao.getAllEntries()

    suspend fun insert(entry: DiaryEntry) = diaryEntryDao.insert(entry)

    suspend fun update(entry: DiaryEntry) = diaryEntryDao.update(entry)

    suspend fun delete(entry: DiaryEntry) = diaryEntryDao.delete(entry)

    suspend fun getEntryById(id: String): DiaryEntry? = diaryEntryDao.getEntryById(id)

    suspend fun clearAll() {
        diaryEntryDao.clearAll()
    }

    //day note
    suspend fun insertDayNote(note: DayNoteEntity) {
        dao.insertDayNote(note)
    }

    suspend fun updateDayNote(note: DayNoteEntity) {
        dao.updateDayNote(note)
    }

    suspend fun deleteDayNote(note: DayNoteEntity) {
        dao.deleteDayNote(note)
    }

    // Specific task + date ka ek note
    suspend fun getNoteForDate(taskId: String, date: String): DayNoteEntity? {
        return dao.getNoteForDate(taskId, date)
    }

    // Ek specific date ke saare notes (jitne bhi tasks ke ho)
    suspend fun getNotesOnDate(date: String): List<DayNoteEntity> {
        return dao.getNotesOnDate(date)
    }

    //mood history
    val allMoods: LiveData<List<MoodHistoryItem>> = moodDao.getAllMoods()

    suspend fun insert(mood: MoodHistoryItem) {
        moodDao.insertMood(mood)
    }

    suspend fun update(mood: MoodHistoryItem) {
        moodDao.updateMood(mood)
    }

    suspend fun delete(mood: MoodHistoryItem) {
        moodDao.deleteMood(mood)
    }

    fun getMoodByDateLive(todayDate: String): LiveData<MoodHistoryItem?> {
        return moodDao.getMoodByDateLive(todayDate)
    }

    suspend fun clearAllMood() {
        moodDao.clearAll()
    }

    //List
    suspend fun insertList(list: ListEntity) {
        listDao.insertList(list)
    }

    fun getAllLists(): LiveData<List<ListEntity>> {
        return listDao.getAllLists()
    }

    suspend fun updateList(list: ListEntity) {
        listDao.updateList(list)
    }

    suspend fun addTaskToList(listId: String, taskId: String) {
        listDao.insertListTask(
            ListTaskCrossRef(
                listId = listId,
                taskId = taskId
            )
        )
    }

    suspend fun removeTaskFromList(listId: String, taskId: String) {
        listDao.deleteListTask(
            ListTaskCrossRef(
                listId = listId,
                taskId = taskId
            )
        )
    }

    fun getListWithTasks(listId: String): LiveData<ListWithTasks> {
        return listDao.getListWithTasks(listId)
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


    //log
    fun getCombinedData(): Flow<List<DayLogEntity>> {

        val tasksFlow = dao.getAllTasksFlow()
        val diaryFlow = diaryEntryDao.getAllEntriesFlow()
        val moodFlow = moodDao.getAllMoodsFlow()
        val completionFlow = completionDao.getAllCompletionsFlow()

        return combine(
            tasksFlow,
            diaryFlow,
            moodFlow,
            completionFlow
        ) { tasks, diaries, moods, completions ->

            val result = mutableListOf<DayLogEntity>()

            // 🔥 Build completion map once
            val completionMapByDate =
                completions
                    .groupBy { it.date }
                    .mapValues { entry ->
                        entry.value.associate { it.taskId to it.count }
                    }

            // Collect all unique dates
            val allDates =
                tasks.map { it.taskAddedDate }
                    .union(diaries.map { it.date })
                    .union(moods.map { it.date })
                    .sortedDescending()

            for (date in allDates) {

                // pure filtering
                val validTasks = tasks.filter { task ->
                    when (task.taskType) {

                        TaskType.DAILY ->
                            task.taskAddedDate <= date &&
                                    (task.taskRemovedDate == null ||
                                            task.taskRemovedDate >= date)

                        TaskType.DAY ->
                            task.taskAddedDate == date

                        TaskType.UNTIL_COMPLETE ->
                            task.taskAddedDate <= date &&
                                    (task.taskRemovedDate == null ||
                                            task.taskRemovedDate >= date)
                    }
                }

                val diary = diaries.find { it.date == date }
                val mood = moods.find { it.date == date }

                val tasksForDate =
                    validTasks.filter {
                        it.taskType != TaskType.UNTIL_COMPLETE
                    }

                val completionForDate =
                    completionMapByDate[date] ?: emptyMap()

                val dayScore =
                    calculateDailyScoreForDatePure(
                        tasksForDate,
                        completionForDate
                    )

                result.add(
                    DayLogEntity(
                        date = date,
                        title = diary?.title,
                        content = diary?.content,
                        emoji = mood?.emoji ?: "😐",
                        doneCount = completionForDate.size,
                        pendingCount = validTasks.size - completionForDate.size,
                        dayScore = dayScore,
                        diaryId = diary?.id
                    )
                )
            }

            result
        }
            .flowOn(Dispatchers.Default)
    }


    //reorder
    suspend fun getLatestLogForDate(requestedDate: String): TaskOrderChangeLog? {
        return orderLogDao.getLatestLogForDate(requestedDate)
    }

    suspend fun logReorder(dateOfChange: String, effectiveFromDate: String, taskIds: List<String>) {
        val existingLog = orderLogDao.getLogByEffectiveDate(effectiveFromDate)
        val newLog = existingLog?.copy(
            dateOfChange = dateOfChange,
            taskIds = taskIds
        )
            ?: TaskOrderChangeLog(
                dateOfChange = dateOfChange,
                effectiveFromDate = effectiveFromDate,
                taskIds = taskIds
            )
        orderLogDao.upsertLog(newLog)
    }


}
