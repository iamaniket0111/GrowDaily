package com.anitech.scoremyday.database

import androidx.lifecycle.LiveData
import com.anitech.scoremyday.data_class.DateItemEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DateItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDateItem(dateItem: DateItemEntity): Long

    @Query("SELECT * FROM date_items WHERE date = :date")
    suspend fun getDateItem(date: String): DateItemEntity?

    @Query("SELECT * FROM date_items WHERE date = :date")
    fun getDateItemObs(date: String): LiveData<DateItemEntity?>

    @Query("DELETE FROM date_items WHERE date = :date")
    suspend fun deleteDateItem(date: String)
}
