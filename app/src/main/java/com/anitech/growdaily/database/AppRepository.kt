package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.anitech.growdaily.CommonMethods.Companion.filterTasks
import com.anitech.growdaily.CommonMethods.Companion.calculateDailyScoreForDate
import com.anitech.growdaily.CommonMethods.Companion.filterTasks

import com.anitech.growdaily.DailyTaskDao
import com.anitech.growdaily.data_class.ConditionEntity
import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.data_class.DateItemEntity
import com.anitech.growdaily.data_class.DayLogEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.data_class.MoodHistoryItem
import com.anitech.growdaily.enum_class.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.collections.filter

class AppRepository(
    private val dao: DailyTaskDao,
    private val diaryEntryDao: DiaryEntryDao,
    private val moodDao: MoodHistoryDao,
    private val conditionDao: ConditionDao,
    private val dateItemDao: DateItemDao
) {
    //day score
    suspend fun insertTask(task: DailyTask) = dao.insertTask(task)

    suspend fun updateTask(task: DailyTask) = dao.updateTask(task)

    suspend fun deleteTask(task: DailyTask) = dao.deleteTask(task)

    suspend fun clearAllTasks() = dao.clearAllTasks()

    fun getAllTasks(): LiveData<List<DailyTask>> = dao.getAllTasks()

    suspend fun updateTasks(tasks: List<DailyTask>) = dao.updateTasks(tasks)

    suspend fun deleteTasks(tasks: List<DailyTask>) = dao.deleteTasks(tasks)

    fun getDailyTasksByCondition(conditionId: Int): LiveData<List<DailyTask>> {
        val id = conditionId.toString()
        return dao.getDailyTasksByCondition(
            id,
            "$id,%",
            "%,$id",
            "%,$id,%"
        )
    }

    fun getDailyTasksByConditionDirect(conditionId: Int): List<DailyTask> {
        val id = conditionId.toString()
        return dao.getDailyTasksByConditionDirect(
            id,
            "$id,%",
            "%,$id",
            "%,$id,%"
        )
    }

    fun getAllDailyTasks(): LiveData<List<DailyTask>> {
        return dao.getAllDailyTasks()
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

    //condition
    suspend fun insertAll(conditions: List<ConditionEntity>) {
        conditionDao.insertAll(conditions)
    }

    fun getAll(): LiveData<List<ConditionEntity>> = liveData {
        emit(conditionDao.getAll())
    }

    fun getAllConditions(): LiveData<List<ConditionEntity>> {
        return conditionDao.getAllConditions()
    }

    suspend fun updateCondition(condition: ConditionEntity) {
        conditionDao.updateCondition(condition)
    }

    suspend fun updateConditions(conditions: List<ConditionEntity>) {
        conditionDao.updateConditions(conditions)
    }


    //condition date
    suspend fun insertDateItem(dateItem: DateItemEntity): Long {
        return dateItemDao.insertDateItem(dateItem)
    }

    suspend fun getDateItem(date: String): DateItemEntity? {
        return dateItemDao.getDateItem(date)
    }

    suspend fun deleteDateItem(date: String) {
        dateItemDao.deleteDateItem(date)
    }

    suspend fun upsertDateItem(newDateItem: DateItemEntity) {
        val existing = dateItemDao.getDateItem(newDateItem.date)

        if (existing == null) {
            // Agar date hi nahi hai, to direct insert
            dateItemDao.insertDateItem(newDateItem)
        } else {
            // Purane data ko modify karna hoga
            val updatedData = existing.data.toMutableList()

            // Pehle se same type exist karta hai kya?
            val index = updatedData.indexOfFirst { it.type == newDateItem.data.first().type }

            if (index != -1) {
                // agar same type hai to purana replace karo
                updatedData[index] = newDateItem.data.first()
            } else {
                // agar same type nahi hai to append karo
                updatedData.add(newDateItem.data.first())
            }
            val updatedItem = existing.copy(data = updatedData)
            dateItemDao.insertDateItem(updatedItem) // REPLACE due to same PK (dateId)
        }
    }

    suspend fun removeConditionFromDate(date: String, conditionType: String) {
        val existing = dateItemDao.getDateItem(date)

        if (existing != null) {
            val updatedData = existing.data.filter { it.type != conditionType }

            if (updatedData.isEmpty()) {
                // pura DateItemEntity delete kar do
                dateItemDao.deleteDateItem(date)
            } else {
                // sirf updated list ke sath replace karo
                val updatedItem = existing.copy(data = updatedData)
                dateItemDao.insertDateItem(updatedItem) // REPLACE
            }
        }
    }

    fun getDateItemObs(date: String): LiveData<DateItemEntity?> {
        return dateItemDao.getDateItemObs(date)
    }

    fun getCombinedData(): Flow<List<DayLogEntity>> {
        val tasksFlow = dao.getAllTasksFlow()
        val diaryFlow = diaryEntryDao.getAllEntriesFlow()
        val dateFlow = dateItemDao.getDateEntries()
        val moodFlow = moodDao.getAllMoodsFlow()

        return combine(tasksFlow, diaryFlow, dateFlow, moodFlow) { tasks, diaries, dates, moods ->
            val result = mutableListOf<DayLogEntity>()

            val allDates = dates.map { it.date }
                .union(tasks.map { it.taskAddedDate })
                .union(diaries.map { it.date })
                .union(moods.map { it.date })
                .sortedDescending()

            for (date in allDates) {
                val validTasks = filterTasks(tasks, date)

                val doneCount = validTasks.count { it.isCompleted || date in it.completedDates }
                val pendingCount = validTasks.size - doneCount

                val diary = diaries.find { it.date == date }
                val mood = moods.find { it.date == date }

                // Use your existing reusable score function
                val tasksForDate = filterTasks(validTasks, date)
                .filter { it.taskType != TaskType.UNTIL_COMPLETE }
                val dayScore = calculateDailyScoreForDate(tasksForDate, date)
// FIXME: this method is also required in the bar adapter but their we are using diffraint one, try to use one of them 
                result.add(
                    DayLogEntity(
                        date = date,
                        title = diary?.title,
                        content = diary?.content,
                        emoji = mood?.emoji ?: "😐",
                        doneCount = doneCount,
                        pendingCount = pendingCount,
                        dayScore = dayScore,
                        diaryId = diary?.id
                    )
                )
            }

            result
        }
    }



}
