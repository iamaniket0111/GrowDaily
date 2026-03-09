package com.anitech.growdaily

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.DayNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM daily_tasks")
    suspend fun clearAllTasks()

    @Update
    suspend fun updateTasks(tasks: List<TaskEntity>)

    @Delete
    suspend fun deleteTasks(tasks: List<TaskEntity>)

    //day note
    @Insert
    suspend fun insertDayNote(note: DayNoteEntity)

    @Update
    suspend fun updateDayNote(note: DayNoteEntity)

    @Delete
    suspend fun deleteDayNote(note: DayNoteEntity)

    @Query("SELECT * FROM day_notes WHERE taskOwnerId = :taskId AND date = :date LIMIT 1")
    suspend fun getNoteForDate(taskId: String, date: String): DayNoteEntity?

    @Query("SELECT * FROM day_notes WHERE date = :date")
    suspend fun getNotesOnDate(date: String): List<DayNoteEntity>

    //condition work
    @Query("SELECT * FROM daily_tasks WHERE taskType = 'DAILY'")//daily task fetch karke condition waale task se compare karke containe or not decide karene
    fun getAllDailyTasks(): LiveData<List<TaskEntity>>

    //to get combined data
    @Query("SELECT * FROM daily_tasks")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    //to get ordered data
    @Query("SELECT MAX(manualOrder) FROM daily_tasks")
    suspend fun getMaxManualOrder(): Int?

    @Query("UPDATE daily_tasks SET manualOrder = :order WHERE id = :taskId")
    suspend fun updateTaskOrder(taskId: String, order: Int)

    @Query("SELECT * FROM daily_tasks WHERE id = :taskId LIMIT 1")
    fun getTaskById(taskId: String): LiveData<TaskEntity>


}
