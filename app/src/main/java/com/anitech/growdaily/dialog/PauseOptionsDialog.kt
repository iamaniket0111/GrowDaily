package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toDrawable
import com.anitech.growdaily.R

class PauseOptionsDialog(
    private val context: Context,
    private val title: String,
    private val message: String,
    @DrawableRes private val iconRes: Int,
    @ColorInt private val accentColor: Int,
    @ColorInt private val iconBubbleColor: Int,
    private val onPauseFromTomorrow: () -> Unit,
    private val onPauseFromToday: () -> Unit
) {
    fun show() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_pause_options, null)

        val iconView = view.findViewById<ImageView>(R.id.ivActionIcon)
        val titleView = view.findViewById<TextView>(R.id.tvActionTitle)
        val messageView = view.findViewById<TextView>(R.id.tvActionMessage)
        val tomorrowButton = view.findViewById<Button>(R.id.primaryButton)
        val todayButton = view.findViewById<Button>(R.id.secondaryActionButton)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)

        iconView.setImageResource(iconRes)
        iconView.backgroundTintList = android.content.res.ColorStateList.valueOf(iconBubbleColor)
        iconView.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)
        titleView.text = title
        messageView.text = message
        tomorrowButton.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        todayButton.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)

        tomorrowButton.setOnClickListener {
            onPauseFromTomorrow()
            dialog.dismiss()
        }
        todayButton.setOnClickListener {
            onPauseFromToday()
            dialog.dismiss()
        }
        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}
