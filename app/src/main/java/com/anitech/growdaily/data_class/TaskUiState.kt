package com.anitech.growdaily.data_class

import com.anitech.growdaily.enum_class.DateMode

data class TaskUiState(
    val date: String,
    val tasks: List<TaskUiItem> = emptyList(),
    val completionForDate: Map<String, Int> = emptyMap(),

    val dayScore: Float = 0f,
    val weekScore: Float = 0f,
    val monthScore: Float = 0f,

    val barScores: List<DailyScore> = emptyList(),

    val dateMode: DateMode,
    val isEmpty: Boolean = true,
    val selectedListId: String?

)

