package com.anitech.growdaily.data_class

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "task_extra_dates",
    primaryKeys = ["taskId", "date"],
    indices = [Index("date")]
)
data class TaskExtraDateEntity(
    val taskId: String,
    val date: String
)
