package com.anitech.scoremyday

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.data_class.DayNoteEntity

@Dao
interface DailyTaskDao {

    @Insert
    suspend fun insertTask(task: DailyTask)

    @Update
    suspend fun updateTask(task: DailyTask)

    @Delete
    suspend fun deleteTask(task: DailyTask)

    @Query("SELECT * FROM daily_tasks")
    fun getAllTasks(): LiveData<List<DailyTask>>

    @Query("DELETE FROM daily_tasks")
    suspend fun clearAllTasks()

    @Update
    suspend fun updateTasks(tasks: List<DailyTask>)

    @Delete
    suspend fun deleteTasks(tasks: List<DailyTask>)

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
    @Query(
        """
        SELECT * FROM daily_tasks 
        WHERE isDaily = 1 
        AND (conditionIds = :id 
             OR conditionIds LIKE :idWithCommaPrefix 
             OR conditionIds LIKE :idWithCommaSuffix 
             OR conditionIds LIKE :idWithCommaBoth)
    """
    )
    fun getDailyTasksByCondition(
        id: String,
        idWithCommaPrefix: String,
        idWithCommaSuffix: String,
        idWithCommaBoth: String
    ): LiveData<List<DailyTask>>



     @Query(
        """
        SELECT * FROM daily_tasks 
        WHERE isDaily = 1 
        AND (conditionIds = :id 
             OR conditionIds LIKE :idWithCommaPrefix 
             OR conditionIds LIKE :idWithCommaSuffix 
             OR conditionIds LIKE :idWithCommaBoth)
    """
    )
    fun getDailyTasksByConditionDirect( id: String,
                                        idWithCommaPrefix: String,
                                        idWithCommaSuffix: String,
                                        idWithCommaBoth: String
     ): List<DailyTask>


    @Query("SELECT * FROM daily_tasks WHERE isDaily = 1")//daily task fetch karke condition waale task se compare karke containe or not decide karene
    fun getAllDailyTasks(): LiveData<List<DailyTask>>


}
