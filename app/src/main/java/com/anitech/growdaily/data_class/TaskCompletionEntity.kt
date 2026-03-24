package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "date"]
)
data class TaskCompletionEntity(
    val taskId: String,
    val date: String,
    val count: Int = 0
)
//fixme beeter add task type into too to fetch only repeat tasks