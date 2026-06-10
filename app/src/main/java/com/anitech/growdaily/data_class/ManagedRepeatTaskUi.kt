package com.anitech.growdaily.data_class

import com.anitech.growdaily.enum_class.ManageTaskSection
import java.time.LocalDate

data class ManagedRepeatTaskUi(
    val task: TaskEntity,
    val section: ManageTaskSection,
    val actionKey: String,
    val metaDate: LocalDate,
    val completionOutOf10: Float,
    val bestStreak: Int,
    val completedCount: Int,
    val totalDays: Int,
    val isAddedForToday: Boolean = false,
    val showAction: Boolean = true,
    val showMenu: Boolean = false,
    val originalDate: String? = null,
    val activeDates: Set<String> = emptySet()
)
