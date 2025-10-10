package com.anitech.growdaily.adapter

import androidx.recyclerview.widget.RecyclerView

interface TouchHelperProvider {
    fun startDrag(viewHolder: RecyclerView.ViewHolder)
}
