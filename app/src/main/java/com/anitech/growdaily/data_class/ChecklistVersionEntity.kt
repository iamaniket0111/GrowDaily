package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "checklist_versions",
    primaryKeys = ["taskId", "effectiveFromDate"]
)
data class ChecklistVersionEntity(
    val taskId: String,
    val effectiveFromDate: String,
    val checklistItemsJson: String
)
