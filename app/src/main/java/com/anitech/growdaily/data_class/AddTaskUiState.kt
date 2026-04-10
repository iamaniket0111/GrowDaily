package com.anitech.growdaily.data_class

import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskWeight
import com.anitech.growdaily.enum_class.TrackingType

data class AddTaskUiState(
    val title: String = "",
    val note: String = "",
    val startDate: String = CommonMethods.getTodayDate(),
    val scheduleTime: String? = null,
    val reminderTime: String? = null,
    val isScheduled: Boolean = false,
    val isReminderEnabled: Boolean = false,
    val weight: TaskWeight = TaskWeight.VERY_LOW,
    val icon: String = "TROPHY",
    val color: String = "DARK_BLUE",
    val showUntilCompleted: Boolean = false,
    val showMissedOnGapDays: Boolean = false,

    // ── Tracking ──────────────────────────────────────────────────────────────
    val trackingType: TrackingType = TrackingType.BINARY,
    /** COUNT: how many times per day. Only relevant when trackingType == COUNT. */
    val dailyTargetCount: Int = 1,
    /** TIMER: target seconds per day. Only relevant when trackingType == TIMER. */
    val targetDurationSeconds: Long = 600L,   // default 10 min
    /** CHECKLIST: fixed label list. Only relevant when trackingType == CHECKLIST. */
    val checklistItems: List<String> = emptyList(),

    val repeatType: RepeatType = RepeatType.DAILY,
    val repeatDays: List<Int> = emptyList(),

    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val manualOrder: Int = 0
)
