package com.anitech.scoremyday.data_class

data class DailyScore(
    val date: String, // Format: "yyyy-MM-dd"
    val dayText: String, // Format: "Mon" or "Aug 2"
    val score: Float, //out of 10 value
    val taskCount: Int // FIXME: don't think its required
)