package com.anitech.growdaily.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.anitech.growdaily.R
import com.anitech.growdaily.enum_class.TaskType

class TaskTypeDialog(
    private val onTypeSelected: (TaskType) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_task_type, null)

        view.findViewById<View>(R.id.optionDaily).setOnClickListener {
            onTypeSelected(TaskType.DAILY)
            dismiss()
        }

        view.findViewById<View>(R.id.optionDay).setOnClickListener {
            onTypeSelected(TaskType.DAY)
            dismiss()
        }

        view.findViewById<View>(R.id.optionUntil).setOnClickListener {
            onTypeSelected(TaskType.UNTIL_COMPLETE)
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}


