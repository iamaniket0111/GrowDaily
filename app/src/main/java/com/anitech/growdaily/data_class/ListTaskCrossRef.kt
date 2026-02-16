package com.anitech.growdaily.data_class

import androidx.room.Entity

@Entity(
    tableName = "list_task_cross_ref",
    primaryKeys = ["listId", "taskId"]
)
data class ListTaskCrossRef(
    val listId: String,
    val taskId: String
)

