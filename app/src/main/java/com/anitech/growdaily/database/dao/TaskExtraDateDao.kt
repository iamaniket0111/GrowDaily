package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.TaskExtraDateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskExtraDateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskExtraDateEntity)

    @Query("DELETE FROM task_extra_dates WHERE taskId = :taskId AND date = :date")
    suspend fun delete(taskId: String, date: String)

    @Query("DELETE FROM task_extra_dates WHERE taskId = :taskId")
    suspend fun deleteAllForTask(taskId: String)

    @Query("SELECT * FROM task_extra_dates")
    fun getAllFlow(): Flow<List<TaskExtraDateEntity>>
}
