package com.anitech.growdaily.data_class

data class BarTimelineState(
    val scores: List<DailyScore> = emptyList(),
    val selectedDate: String,
    val isLoadingPast: Boolean = false,
    val isLoadingFuture: Boolean = false
)
