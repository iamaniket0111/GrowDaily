package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.ChecklistProgressItemEntity

@Dao
interface ChecklistProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChecklistProgressItemEntity>)

    @Query(
        """
        SELECT * FROM checklist_progress_items
        WHERE taskId = :taskId AND date = :date
        ORDER BY label ASC
        """
    )
    suspend fun getForTaskDate(taskId: String, date: String): List<ChecklistProgressItemEntity>

    @Query(
        """
        DELETE FROM checklist_progress_items
        WHERE taskId = :taskId AND date = :date
        """
    )
    suspend fun deleteForTaskDate(taskId: String, date: String)

    @Query(
        """
        DELETE FROM checklist_progress_items
        WHERE taskId = :taskId
        """
    )
    suspend fun deleteAllForTask(taskId: String)

    @Query(
        """
        DELETE FROM checklist_progress_items
        WHERE taskId = :taskId AND date < :newStartDate
        """
    )
    suspend fun deleteBefore(taskId: String, newStartDate: String)
}
