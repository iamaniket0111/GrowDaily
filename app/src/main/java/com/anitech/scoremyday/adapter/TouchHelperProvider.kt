package com.anitech.scoremyday.adapter

import androidx.recyclerview.widget.RecyclerView

interface TouchHelperProvider {
    fun startDrag(viewHolder: RecyclerView.ViewHolder)
}
