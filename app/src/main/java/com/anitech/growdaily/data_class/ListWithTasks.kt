package com.anitech.growdaily.data_class

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ListWithTasks(
    @Embedded val list: ListEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ListTaskCrossRef::class,
            parentColumn = "listId",
            entityColumn = "taskId"
        )
    )
    val tasks: List<TaskEntity>
)
