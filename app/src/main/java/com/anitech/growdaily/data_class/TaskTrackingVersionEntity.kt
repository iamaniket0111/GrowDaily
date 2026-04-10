package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "task_tracking_versions",
    primaryKeys = ["taskId", "effectiveFromDate"]
)
data class TaskTrackingVersionEntity(
    val taskId: String,
    val effectiveFromDate: String,
    val weightValue: Int,
    val dailyTargetCount: Int,
    val targetDurationSeconds: Long,
    val checklistItemsJson: String?
)
