package com.anitech.growdaily.data_class

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "daily_tasks")
data class TaskEntity(

    @PrimaryKey val id: String,
    val seriesId: String,

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
    val showUntilCompleted: Boolean = false,
    val showMissedOnGapDays: Boolean = false,

    val repeatType: RepeatType?,
    val repeatDays: String?,

    val dailyTargetCount: Int,
    val manualOrder: Int,
    val scheduledMinutes: Int?,

    // ── Tracking ──────────────────────────────────────────────────────────────
    /** How completion is tracked for this task. Defaults to BINARY for existing tasks. */
    val trackingType: TrackingType = TrackingType.BINARY,

    /**
     * For CHECKLIST tasks only: JSON array of fixed label strings defined at creation.
     * e.g. ["Warm up", "Main set", "Cool down"]
     * Null for all other tracking types.
     */
    val checklistItems: String? = null,

    /**
     * For TIMER tasks only: the target duration in seconds the user must reach
     * for the task to count as completed on a given day.
     * e.g. 1800 = 30 minutes. 0 for all other tracking types.
     */
    val targetDurationSeconds: Long = 0L

) : Parcelable
