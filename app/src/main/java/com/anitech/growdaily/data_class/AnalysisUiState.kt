package com.anitech.growdaily.data_class

import com.anitech.growdaily.enum_class.PeriodType
import java.time.LocalDate

// Rebuilt only when task/completions change
data class AnalysisOverviewState(
    val task: TaskEntity,
    val seriesStartDate: LocalDate = LocalDate.now(),
    val scheduledDates: Set<LocalDate> = emptySet(),
    val progressByDate: Map<LocalDate, Int> = emptyMap(),
    val completedDates: Set<LocalDate>,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completionPercent: Int = 0,
    val completedCount: Int = 0,
    val totalDays: Int = 0,
    val lastCompletedDate: LocalDate? = null,
    val lastMissedDate: LocalDate? = null
)

// Rebuilt only when period/anchor/completions change
data class AnalysisBarState(
    val period: PeriodType = PeriodType.WEEK,
    val anchorDate: LocalDate = LocalDate.now(),
    val barDates: List<LocalDate> = emptyList(),
    val barScores: List<Float> = emptyList(),
    val periodTitle: String = "",
    val isNextEnabled: Boolean = true,
    val isPrevEnabled: Boolean = true
)

// Rebuilt only when heatmapYear/completions change
data class AnalysisHeatmapState(
    val heatmapYear: Int = LocalDate.now().year,
    val seriesStartDate: LocalDate = LocalDate.now(),
    val scheduledDates: Set<LocalDate> = emptySet(),
    val progressByDate: Map<LocalDate, Int> = emptyMap(),
    val isHeatmapNextEnabled: Boolean = true,
    val isHeatmapPrevEnabled: Boolean = true
)
