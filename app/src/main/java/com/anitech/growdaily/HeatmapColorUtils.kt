package com.anitech.growdaily

import android.graphics.Color

fun lightenHeatmapColor(color: Int, factor: Float = 0.6f): Int {
    val r = Color.red(color)
    val g = Color.green(color)
    val b = Color.blue(color)
    return Color.rgb(
        (r + (255 - r) * factor).toInt(),
        (g + (255 - g) * factor).toInt(),
        (b + (255 - b) * factor).toInt()
    )
}

fun resolveHeatmapProgressColor(
    activeColor: Int,
    progressPercent: Int,
    emptyColor: Int
): Int {
    val progress = progressPercent.coerceIn(0, 100)
    return when {
        progress >= 100 -> activeColor
        progress >= 80 -> lightenHeatmapColor(activeColor, 0.15f)
        progress >= 60 -> lightenHeatmapColor(activeColor, 0.3f)
        progress >= 40 -> lightenHeatmapColor(activeColor, 0.45f)
        progress >= 20 -> lightenHeatmapColor(activeColor, 0.6f)
        progress > 0 -> lightenHeatmapColor(activeColor, 0.75f)
        else -> emptyColor
    }
}
