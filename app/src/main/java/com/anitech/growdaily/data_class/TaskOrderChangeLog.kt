package com.anitech.growdaily.data_class

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_order_change_log",
    indices = [Index(value = ["dateOfChange"], unique = true)]
)data class TaskOrderChangeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateOfChange: String,  // Reorder kab hua: e.g., "2025-10-23"
    val effectiveFromDate: String,  // Is order se kis date se valid: e.g., "2025-10-23"
    val taskIds: List<String>  // Ordered task IDs: ["task1_id", "task2_id", ...]
)