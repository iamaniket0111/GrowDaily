package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "checklist_progress_items",
    primaryKeys = ["taskId", "date", "label"]
)
data class ChecklistProgressItemEntity(
    val taskId: String,
    val date: String,
    val label: String,
    val isDone: Boolean
)
