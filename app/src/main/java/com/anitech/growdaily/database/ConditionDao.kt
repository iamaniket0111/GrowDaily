package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anitech.growdaily.data_class.ConditionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conditions: List<ConditionEntity>)

    @Query("SELECT * FROM conditions ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ConditionEntity>

    @Query("SELECT * FROM conditions ORDER BY sortOrder ASC")
    fun getAllConditions(): LiveData<List<ConditionEntity>>

    @Query("SELECT * FROM conditions ORDER BY sortOrder ASC")
    fun getAllConditionsFlow(): Flow<List<ConditionEntity>>
    @Update
    suspend fun updateCondition(condition: ConditionEntity)

    @Update
    suspend fun updateConditions(conditions: List<ConditionEntity>)
}