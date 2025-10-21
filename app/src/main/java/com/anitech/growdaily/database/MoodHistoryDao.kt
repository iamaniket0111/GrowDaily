package com.anitech.growdaily.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anitech.growdaily.data_class.MoodHistoryItem
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM mood_entries ORDER BY date DESC")
    fun getAllMoodsFlow(): Flow<List<MoodHistoryItem>>

    @Query("SELECT * FROM mood_entries WHERE date = :todayDate LIMIT 1")
    fun getMoodByDateLive(todayDate: String): LiveData<MoodHistoryItem?>

}
