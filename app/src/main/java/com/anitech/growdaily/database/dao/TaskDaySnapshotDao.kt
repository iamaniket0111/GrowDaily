package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anitech.growdaily.data_class.TaskDaySnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDaySnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(items: List<TaskDaySnapshotEntity>)

    @Query("DELETE FROM task_day_snapshots WHERE taskId IN (:taskIds)")
    abstract suspend fun deleteForTasks(taskIds: List<String>)

    @Query("DELETE FROM task_day_snapshots")
    abstract suspend fun clearAll()

    @Query("SELECT * FROM task_day_snapshots")
    abstract fun getAllFlow(): Flow<List<TaskDaySnapshotEntity>>

    @Transaction
    open suspend fun replaceForTasks(
        taskIds: List<String>,
        items: List<TaskDaySnapshotEntity>
    ) {
        if (taskIds.isEmpty()) return
        deleteForTasks(taskIds)
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
