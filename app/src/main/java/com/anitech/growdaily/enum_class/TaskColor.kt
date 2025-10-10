package com.anitech.growdaily.enum_class

import android.content.Context
import androidx.core.content.ContextCompat
import com.anitech.growdaily.R

enum class TaskColor(val resId: Int) {
    RED(R.color.category_red),
    ORANGE(R.color.category_orange),
    YELLOW(R.color.category_yellow),
    GREEN(R.color.category_green),
    TEAL(R.color.category_teal),
    BLUE(R.color.category_blue),
    PURPLE(R.color.category_purple),
    DARK_BLUE(R.color.category_dark_blue);

    fun toColorInt(context: Context): Int {
        return ContextCompat.getColor(context, resId)
    }

    companion object {
        fun fromResId(resId: Int): TaskColor? {
            return entries.find { it.resId == resId }
        }

        fun fromName(name: String): TaskColor? {
            return entries.find { it.name == name }
        }
    }
}
