package com.anitech.growdaily.dialog

import android.content.Context
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity

class DeleteTaskDialog(
    private val context: Context,
    private val task: TaskEntity,
    private val onDeleteConfirmed: (TaskEntity) -> Unit
) {
    fun show() {
        TaskActionDialog(
            context = context,
            title = "Delete task?",
            message = "This will remove the task and its related progress data. This action cannot be undone.",
            primaryLabel = "Delete",
            iconRes = R.drawable.ic_warning,
            accentColor = context.getColor(R.color.category_red),
            iconBubbleColor = 0x50EF5350,
            onPrimaryAction = { onDeleteConfirmed(task) }
        ).show()
    }
}
