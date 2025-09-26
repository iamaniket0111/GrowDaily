package com.anitech.scoremyday.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anitech.scoremyday.data_class.MoodHistoryItem

@Dao
interface MoodHistoryDao {

    @Insert
    suspend fun insertMood(mood: MoodHistoryItem)

    @Update
    suspend fun updateMood(mood: MoodHistoryItem)

    @Delete
    suspend fun deleteMood(mood: MoodHistoryItem)

    @Query("SELECT * FROM mood_entries ORDER BY date DESC")
    fun getAllMoods(): LiveData<List<MoodHistoryItem>>

    @Query("DELETE FROM mood_entries")
    suspend fun clearAll()
}
