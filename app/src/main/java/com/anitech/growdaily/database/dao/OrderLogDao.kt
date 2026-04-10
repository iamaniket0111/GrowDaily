package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.anitech.growdaily.data_class.TaskOrderChangeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderLogDao {
    @Upsert
    suspend fun upsertLog(log: TaskOrderChangeLog)

    @Query("SELECT * FROM task_order_change_log WHERE effectiveFromDate <= :requestedDate ORDER BY dateOfChange DESC LIMIT 1")
    suspend fun getLatestLogForDate(requestedDate: String): TaskOrderChangeLog?

    @Query("SELECT * FROM task_order_change_log ORDER BY dateOfChange DESC")
    fun getAllLogs(): Flow<List<TaskOrderChangeLog>>  // For debugging, optional

    @Query("SELECT * FROM task_order_change_log WHERE effectiveFromDate = :effectiveFromDate LIMIT 1")
    suspend fun getLogByEffectiveDate(effectiveFromDate: String): TaskOrderChangeLog?

}
