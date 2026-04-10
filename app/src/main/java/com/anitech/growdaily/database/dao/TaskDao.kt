package com.anitech.growdaily.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anitech.growdaily.data_class.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    //to get combined data
    @Query("SELECT * FROM daily_tasks")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM daily_tasks WHERE taskType = 'DAILY'")
    fun getRepeatTasksFlow(): Flow<List<TaskEntity>>

    //to get ordered data
    @Query("SELECT MAX(manualOrder) FROM daily_tasks")
    suspend fun getMaxManualOrder(): Int?

    @Query("UPDATE daily_tasks SET manualOrder = :order WHERE id = :taskId")
    suspend fun updateTaskOrder(taskId: String, order: Int)

    @Query("SELECT * FROM daily_tasks WHERE id = :taskId LIMIT 1")
    fun getTaskById(taskId: String): LiveData<TaskEntity>


}
