package com.anitech.growdaily.data_class

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "until_complete_children",
    primaryKeys = ["childTaskId"],
    indices = [
        Index(value = ["parentTaskId", "taskAddedDate"], unique = true),
        Index("parentTaskId")
    ]
)
data class UntilCompleteChildEntity(
    val childTaskId: String,
    val parentTaskId: String,
    val taskAddedDate: String
)
