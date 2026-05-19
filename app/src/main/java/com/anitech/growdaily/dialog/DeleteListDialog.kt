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
            title = context.getString(R.string.delete_list_title),
            message = context.getString(R.string.delete_list_message),
            primaryLabel = context.getString(R.string.delete_button),
            iconRes = R.drawable.ic_warning,
            accentColor = context.getColor(R.color.category_red),
            iconBubbleColor = 0x50EF5350,
            onPrimaryAction = { onDeleteList(list) }
        ).show()
    }
}
