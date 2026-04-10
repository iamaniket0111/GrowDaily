package com.anitech.growdaily.dialog

import android.content.Context
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ListEntity

class DeleteListDialog(
    private val context: Context,
    private val list: ListEntity,
    private val onDeleteList: (ListEntity) -> Unit
) {
    fun show() {
        TaskActionDialog(
            context = context,
            title = "Delete list?",
            message = "This will remove the list. Tasks stay available, but the list itself cannot be recovered.",
            primaryLabel = "Delete",
            iconRes = R.drawable.ic_warning,
            accentColor = context.getColor(R.color.category_red),
            iconBubbleColor = 0x50EF5350,
            onPrimaryAction = { onDeleteList(list) }
        ).show()
    }
}
