package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "daily_tasks")
data class TaskEntity(

    @PrimaryKey val id: String,

    val title: String,
    val note: String?,
    val weight: TaskWeight,

    val scheduledTime: String?,
    val reminderTime: String?,
    val reminderEnabled: Boolean,
    val isScheduled: Boolean,

    val taskAddedDate: String,
    val taskRemovedDate: String?,

    val iconResId: String,
    val colorCode: String,
    val taskType: TaskType,

    val repeatType: RepeatType?,
    val repeatDays: String?,

    val dailyTargetCount: Int

) : Parcelable

