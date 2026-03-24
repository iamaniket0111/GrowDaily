package com.anitech.growdaily.enum_class

import androidx.annotation.StringRes
import com.anitech.growdaily.R

enum class TaskType(@StringRes val labelRes: Int) {
    DAILY(R.string.task_type_daily),
    DAY(R.string.task_type_today),
    UNTIL_COMPLETE(R.string.task_type_until_done)
}