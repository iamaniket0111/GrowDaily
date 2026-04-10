package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R

class TaskIconAdapter(
    private val symbolList: List<Int>,
    private val listener: OnSymbolClickListener,
    private var preSelectedResId: Int? = null,
    private var selectedBubbleColor: Int
) : RecyclerView.Adapter<TaskIconAdapter.SymbolViewHolder>() {

    interface OnSymbolClickListener {
        fun onSymbolClick(symbolResId: Int)
    }

    private var selectedPosition = RecyclerView.NO_POSITION

    init {
        preSelectedResId?.let { resId ->
            val index = symbolList.indexOf(resId)
            if (index != -1) {
                selectedPosition = index
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_category_icon_grid, parent, false)
        return SymbolViewHolder(view)
    }

    override fun onBindViewHolder(holder: SymbolViewHolder, position: Int) {
        val resId = symbolList[position]
        holder.bind(resId, position == selectedPosition, selectedBubbleColor)

        holder.itemView.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            // UI update for old + new selection
            if (oldPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(oldPosition)
            }
            notifyItemChanged(selectedPosition)
            // Callback
            listener.onSymbolClick(resId)
        }
    }

    override fun getItemCount(): Int = symbolList.size

    fun updateSelectedBubbleColor(color: Int) {
        if (selectedBubbleColor == color) return
        selectedBubbleColor = color
        if (selectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(selectedPosition)
        }
    }

    class SymbolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.symbolImage)
        private val isSelectedImg: ImageView = itemView.findViewById(R.id.isSelectedImg)

        fun bind(resId: Int, isSelected: Boolean, selectedBubbleColor: Int) {
            imageView.setImageResource(resId)
            isSelectedImg.visibility = if (isSelected) View.VISIBLE else View.GONE
            if (isSelected) {
                isSelectedImg.backgroundTintList = ColorStateList.valueOf(selectedBubbleColor)
            }
        }
    }
}
