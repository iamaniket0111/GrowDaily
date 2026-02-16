package com.anitech.growdaily.data_class

data class TaskUiState(
    val date: String,
    val tasks: List<TaskEntity> = emptyList(),
    val completionForDate: Map<String, Int> = emptyMap(),

    val dayScore: Float = 0f,
    val weekScore: Float = 0f,
    val monthScore: Float = 0f,

    val weekBarScores: List<DailyScore> = emptyList(),

    val isFutureDate: Boolean = false,
    val isEmpty: Boolean = true
)

