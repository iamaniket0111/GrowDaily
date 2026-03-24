package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ListEntity

class DeleteListDialog(
    private val context: Context,
    private val list: ListEntity,
    private val onDeleteList: (ListEntity) -> Unit
) {
    fun show() {
        val dialog = Dialog(context)
        val inflater = LayoutInflater.from(context)

        val warningView = inflater.inflate(R.layout.dialog_delete_warning, null)

        val deleteBtn = warningView.findViewById<View>(R.id.deleteButton)
        val cancelBtn = warningView.findViewById<View>(R.id.cancelButton)

        deleteBtn.setOnClickListener {
            onDeleteList(list)
            dialog.dismiss()
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }

        dialog.setContentView(warningView)

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.show()
    }
}