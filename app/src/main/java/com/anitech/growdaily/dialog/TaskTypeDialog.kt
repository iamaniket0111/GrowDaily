package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.content.res.Configuration
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.enum_class.TaskType

class TaskTypeDialog(
    private val onTypeSelected: (TaskType) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.dialog_task_type, null)

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        (requireActivity() as? MainActivity)?.accentColor?.value?.let { color ->
            applyAccentColor(view, color)
        }

        view.findViewById<View>(R.id.optionDaily).setOnClickListener {
            onTypeSelected(TaskType.DAILY)
            dismiss()
        }

        view.findViewById<View>(R.id.optionDay).setOnClickListener {
            onTypeSelected(TaskType.DAY)
            dismiss()
        }

        dialog.window?.setLayout(
            ((resources.displayMetrics.widthPixels) * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private fun applyAccentColor(view: View, color: Int) {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val alphaColor = adjustAlpha(color, 0.12f)
        
        if (!isDarkMode) {
            view.findViewById<TextView>(R.id.txtTitle)?.setTextColor(color)
            view.findViewById<TextView>(R.id.optionDailyTitle)?.setTextColor(color)
            view.findViewById<TextView>(R.id.optionDayTitle)?.setTextColor(color)
        }

        view.findViewById<View>(R.id.dailyIconContainer)?.backgroundTintList = ColorStateList.valueOf(alphaColor)
        view.findViewById<ImageView>(R.id.dailyIcon)?.imageTintList = ColorStateList.valueOf(color)
        
        view.findViewById<View>(R.id.dayIconContainer)?.backgroundTintList = ColorStateList.valueOf(alphaColor)
        view.findViewById<ImageView>(R.id.dayIcon)?.imageTintList = ColorStateList.valueOf(color)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
