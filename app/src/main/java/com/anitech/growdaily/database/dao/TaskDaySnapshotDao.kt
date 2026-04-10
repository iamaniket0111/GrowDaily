package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDaySnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TaskDaySnapshotEntity>)

    @Query("DELETE FROM task_day_snapshots WHERE taskId IN (:taskIds)")
    suspend fun deleteForTasks(taskIds: List<String>)

    @Query("DELETE FROM task_day_snapshots")
    suspend fun clearAll()

    @Query("SELECT * FROM task_day_snapshots")
    fun getAllFlow(): Flow<List<TaskDaySnapshotEntity>>
}
