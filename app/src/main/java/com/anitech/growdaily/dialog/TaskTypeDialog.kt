package com.anitech.growdaily.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
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
}


