package com.anitech.growdaily.data_class

import java.time.LocalDate

data class RepeatTaskUi(
    val task: TaskEntity,
    val completedDates: Map<LocalDate, Int>,  // was Set<LocalDate>
    val currentStreak: Int,
    val completionOutOf10: Float,
    val completedCount: Int,
    val totalDays: Int
)