package com.anitech.growdaily.data_class

import java.time.LocalDate

data class RepeatTaskUi(
    val task: TaskEntity,
    val seriesStartDate: LocalDate,
    val completionByDate: Map<LocalDate, TaskCompletionEntity>,
    val progressByDate: Map<LocalDate, Int>,
    val taskIdByDate: Map<LocalDate, String>,
    val completedDays: Set<LocalDate>,
    val trackingVersions: List<TaskTrackingVersionEntity>,
    val currentStreak: Int,
    val completionOutOf10: Float,
    val completedCount: Int,
    val totalDays: Int
)
