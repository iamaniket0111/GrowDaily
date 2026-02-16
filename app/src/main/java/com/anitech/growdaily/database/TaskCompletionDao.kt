package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(entity: TaskCompletionEntity)

    @Query(
        """
        SELECT * FROM task_completions 
        WHERE taskId = :taskId AND date = :date
        LIMIT 1
    """
    )
    suspend fun isTaskCompletedOnDate(
        taskId: String,
        date: String
    ): TaskCompletionEntity?

    @Query(
        """
        SELECT MIN(date) FROM task_completions
        WHERE taskId = :taskId
    """
    )
    suspend fun getFirstCompletionDate(taskId: String): String?

    @Query(
        """
        DELETE FROM task_completions 
        WHERE taskId = :taskId
    """
    )
    suspend fun deleteAllForTask(taskId: String)


    @Query(
        """
    DELETE FROM task_completions 
    WHERE taskId = :taskId AND date = :date
    """
    )
    suspend fun delete(taskId: String, date: String)

    @Query("SELECT * FROM task_completions")
    fun getAllCompletions(): LiveData<List<TaskCompletionEntity>>

    @Query(
        """
    SELECT taskId FROM task_completions
    WHERE date = :date
"""
    )
    suspend fun getCompletedTaskIdsForDate(date: String): List<String>

    @Query("SELECT * FROM task_completions")
    fun getAllCompletionsTaskData(): LiveData<List<TaskCompletionEntity>>

    @Query(
        """
    DELETE FROM task_completions
    WHERE taskId = :taskId AND date < :newStartDate
"""
    )

    suspend fun deleteCompletionsBefore(
        taskId: String,
        newStartDate: String
    )

    @Query(
        """
    SELECT * FROM task_completions
    WHERE taskId = :taskId
    ORDER BY date ASC
"""
    )
    fun getCompletionsForTask(taskId: String): LiveData<List<TaskCompletionEntity>>


    @Query("SELECT * FROM task_completions WHERE date = :date")
    suspend fun getCompletionsForDateSuspend(date: String): List<TaskCompletionEntity>


    @Query("SELECT * FROM task_completions")
    fun getAllCompletionsFlow(): Flow<List<TaskCompletionEntity>>




}
