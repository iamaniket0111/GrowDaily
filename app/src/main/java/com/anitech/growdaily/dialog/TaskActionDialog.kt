package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
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

class TaskActionDialog(
    private val context: Context,
    private val title: String,
    private val message: String,
    private val primaryLabel: String,
    private val secondaryLabel: String = "Cancel",
    @DrawableRes private val iconRes: Int,
    @ColorInt private val accentColor: Int,
    @ColorInt private val iconBubbleColor: Int,
    private val onPrimaryAction: () -> Unit,
    private val onSecondaryAction: (() -> Unit)? = null
) {
    fun show() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_warning, null)

        val iconView = view.findViewById<ImageView>(R.id.ivActionIcon)
        val titleView = view.findViewById<TextView>(R.id.tvActionTitle)
        val messageView = view.findViewById<TextView>(R.id.tvActionMessage)
        val primaryButton = view.findViewById<Button>(R.id.primaryButton)
        val secondaryButton = view.findViewById<Button>(R.id.secondaryButton)

        iconView.setImageResource(iconRes)
        iconView.backgroundTintList = ColorStateList.valueOf(iconBubbleColor)
        iconView.imageTintList = ColorStateList.valueOf(accentColor)
        titleView.text = title
        messageView.text = message
        primaryButton.text = primaryLabel
        primaryButton.backgroundTintList = ColorStateList.valueOf(accentColor)
        secondaryButton.text = secondaryLabel

        primaryButton.setOnClickListener {
            onPrimaryAction()
            dialog.dismiss()
        }
        secondaryButton.setOnClickListener {
            onSecondaryAction?.invoke()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            ((context.resources.displayMetrics.widthPixels) * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}
