package com.anitech.growdaily.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anitech.growdaily.data_class.UntilCompleteChildEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UntilCompleteChildDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UntilCompleteChildEntity)

    @Query("DELETE FROM until_complete_children WHERE childTaskId = :childTaskId")
    suspend fun deleteByChildId(childTaskId: String)

    @Query("DELETE FROM until_complete_children WHERE parentTaskId = :parentTaskId")
    suspend fun deleteAllForParent(parentTaskId: String)

    @Query(
        """
        SELECT * FROM until_complete_children
        WHERE parentTaskId = :parentTaskId AND taskAddedDate = :date
        LIMIT 1
        """
    )
    suspend fun getChildForParentDate(parentTaskId: String, date: String): UntilCompleteChildEntity?

    @Query("SELECT * FROM until_complete_children WHERE parentTaskId = :parentTaskId")
    suspend fun getAllNowForParent(parentTaskId: String): List<UntilCompleteChildEntity>

    @Query("SELECT * FROM until_complete_children")
    fun getAllFlow(): Flow<List<UntilCompleteChildEntity>>
}
