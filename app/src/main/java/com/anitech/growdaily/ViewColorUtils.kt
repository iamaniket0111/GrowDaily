package com.anitech.growdaily

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View

fun View.setSolidBackgroundColorCompat(color: Int) {
    val bg = background?.mutate()
    if (bg is GradientDrawable) {
        bg.setColor(color)
        background = bg
    } else {
        backgroundTintList = ColorStateList.valueOf(color)
    }
}
