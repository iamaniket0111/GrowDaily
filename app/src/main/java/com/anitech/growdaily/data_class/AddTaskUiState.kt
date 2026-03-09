package com.anitech.growdaily.data_class

import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.enum_class.TaskWeight

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

    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val manualOrder: Int = 0
)
