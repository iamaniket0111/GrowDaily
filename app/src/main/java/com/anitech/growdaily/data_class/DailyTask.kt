package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "daily_tasks")
data class DailyTask(
    @PrimaryKey val id: String,
    val title: String,
    val note: String?,
    val isCompleted: Boolean,
    val weight: TaskWeight,
    val scheduledTime: String?,
    val completedTime: String?,
    val taskAddedDate: String,
    val taskRemovedDate: String?,
    val reminderEnabled: Boolean,
    var completedDates: List<String>,
    var conditionIds: List<Int>,
    val iconResId: String,
    val colorCode: String,
    val taskType: TaskType,
    val isScheduled: Boolean = false
): Parcelable
