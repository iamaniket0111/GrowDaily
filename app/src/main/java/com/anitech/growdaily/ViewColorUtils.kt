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

fun Int.adjustAlpha(factor: Float): Int {
    val alpha = Math.round(android.graphics.Color.alpha(this) * factor)
    val red = android.graphics.Color.red(this)
    val green = android.graphics.Color.green(this)
    val blue = android.graphics.Color.blue(this)
    return android.graphics.Color.argb(alpha, red, green, blue)
}
