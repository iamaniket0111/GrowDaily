package com.anitech.growdaily.data_class

import com.anitech.growdaily.enum_class.DateMode
import com.anitech.growdaily.enum_class.TimeState

data class TaskUiItem(
    val task: TaskEntity,
    val isActive: Boolean,
    val timeState: TimeState,
    val dateMode: DateMode,
    val currentStreak: Int = 0,
    val completionCount: Int,
    val isListFiltered: Boolean

)
