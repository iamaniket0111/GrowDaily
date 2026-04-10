package com.anitech.growdaily.data_class

import java.time.LocalDate

data class WeekHabit(
    val date: LocalDate? = null,
    val dayLetter: String = "",
    val isDivider: Boolean = false
)

