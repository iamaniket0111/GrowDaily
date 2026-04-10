package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.ChecklistVersionEntity

@Dao
interface ChecklistVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChecklistVersionEntity)

    @Query(
        """
        SELECT checklistItemsJson
        FROM checklist_versions
        WHERE taskId = :taskId AND effectiveFromDate <= :date
        ORDER BY effectiveFromDate DESC
        LIMIT 1
        """
    )
    suspend fun getChecklistItemsForDate(taskId: String, date: String): String?
}
