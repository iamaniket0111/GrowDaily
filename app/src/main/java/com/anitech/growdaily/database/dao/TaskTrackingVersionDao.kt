package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.TaskTrackingVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTrackingVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskTrackingVersionEntity)

    @Query("SELECT * FROM task_tracking_versions")
    fun getAllFlow(): Flow<List<TaskTrackingVersionEntity>>
}
