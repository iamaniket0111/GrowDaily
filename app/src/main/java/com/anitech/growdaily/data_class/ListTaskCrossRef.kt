package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "list_task_cross_ref",
    primaryKeys = ["listId", "taskId"],
    indices = [androidx.room.Index(value = ["taskId"])]
)
data class ListTaskCrossRef(
    val listId: String,
    val taskId: String
)

