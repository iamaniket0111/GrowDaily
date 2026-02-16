package com.anitech.growdaily.data_class

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "day_notes",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskOwnerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskOwnerId")]
)
data class DayNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val taskOwnerId: String,
    val date: String,
    var note: String
)
