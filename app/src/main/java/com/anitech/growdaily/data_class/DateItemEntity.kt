package com.anitech.growdaily.data_class

import androidx.room.*

@Entity(tableName = "date_items")
data class DateItemEntity(
    @PrimaryKey(autoGenerate = true) val dateId: Long = 0,
    val date: String,
    val data: List<DateDataEntity>
)
