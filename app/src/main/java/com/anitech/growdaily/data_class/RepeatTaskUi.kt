package com.anitech.growdaily.data_class

import java.time.LocalDate

data class RepeatTaskUi(
    val task: TaskEntity,
    val completedDates: Set<LocalDate>,
    val currentStreak: Int = 0,
    val completionOutOf10: Float = 0f,  // e.g. 7.3 shown as "7.3/10"
    val completedCount: Int = 0,
    val totalDays: Int = 0
)