package com.anitech.growdaily.data_class

import java.time.LocalDate

data class TaskHeatmapUi(
    val task: TaskEntity,
    val completedDates: Set<LocalDate>
)
