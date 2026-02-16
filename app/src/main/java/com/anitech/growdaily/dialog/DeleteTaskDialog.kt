package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.core.graphics.drawable.toDrawable
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity

class DeleteTaskDialog(
    private val context: Context,
    private val taskEntities: List<TaskEntity>,
    private val nonTaskEntities: List<TaskEntity>,
    private val currentDate: String,
    private val onDeleteDailyCompletely: (List<TaskEntity>) -> Unit,
    private val onUpdateDailyDate: (List<TaskEntity>) -> Unit,
    private val onDeleteNonDaily: (List<TaskEntity>) -> Unit
) {

    fun show() {
        val hasDailyTasks = taskEntities.isNotEmpty()
        var isRemoveCompletely = false

        val dialog = Dialog(context)
        val inflater = LayoutInflater.from(context)

        val continueView = inflater.inflate(R.layout.dialog_delete_continue, null)
        val warningView = inflater.inflate(R.layout.dialog_delete_warning, null)

        val continueBtn = continueView.findViewById<View>(R.id.btn_continue)
        val radioGroup = continueView.findViewById<RadioGroup>(R.id.radio_group)

        val deleteBtn = warningView.findViewById<View>(R.id.deleteButton)
        val cancelBtn = warningView.findViewById<View>(R.id.cancelButton)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            isRemoveCompletely = checkedId == R.id.radio_remove
        }

        deleteBtn.setOnClickListener {

            if (hasDailyTasks) {
                if (isRemoveCompletely) {
                    onDeleteDailyCompletely(taskEntities)
                } else {
                    val updated = taskEntities.map {
                        it.copy(taskRemovedDate = currentDate)
                    }
                    onUpdateDailyDate(updated)
                }
            }

            if (nonTaskEntities.isNotEmpty()) {
                onDeleteNonDaily(nonTaskEntities)
            }

            dialog.dismiss()
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }

        if (hasDailyTasks) {
            dialog.setContentView(continueView)
            continueBtn.setOnClickListener {
                dialog.setContentView(warningView)
            }
        } else {
            dialog.setContentView(warningView)
        }

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}

