package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "task_day_snapshots",
    primaryKeys = ["taskId", "date"]
)
data class TaskDaySnapshotEntity(
    val taskId: String,
    val date: String,
    val completionCount: Int,
    val progressPercent: Int,
    val isCompleted: Boolean
)
