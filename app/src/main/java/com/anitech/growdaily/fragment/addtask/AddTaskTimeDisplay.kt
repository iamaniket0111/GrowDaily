package com.anitech.growdaily.fragment.addtask

import android.widget.TextView
import androidx.annotation.ColorInt

internal fun TextView.bindAddTaskTimeValue(
    time: String?,
    placeholder: String,
    @ColorInt accentColor: Int,
    @ColorInt placeholderColor: Int,
) {
    if (time != null) {
        text = time
        setTextColor(accentColor)
    } else {
        text = placeholder
        setTextColor(placeholderColor)
    }
}
