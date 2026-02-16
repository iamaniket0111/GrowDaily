package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R

class BoxAdapter(
    private val totalBoxes: Int
) : RecyclerView.Adapter<BoxAdapter.BoxViewHolder>() {

    inner class BoxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_box, parent, false)
        return BoxViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoxViewHolder, position: Int) {
        // future me active/inactive logic yahin aayega
    }

    override fun getItemCount() = totalBoxes
}
