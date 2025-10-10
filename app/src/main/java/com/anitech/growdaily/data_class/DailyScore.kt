package com.anitech.growdaily.data_class

data class DailyScore(
    val date: String,// Format: "yyyy-MM-dd"
    val dayText: String, // For normal state: "1", "23" or  "Mon" or "Aug 2"
    val monthDayText: String, // For selected state: "10/1", "12/25"
    val score: Float,//out of 10 value
    val taskCount: Int// FIXME: don't think its required
)