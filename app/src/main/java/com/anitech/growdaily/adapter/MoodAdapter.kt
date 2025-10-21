package com.anitech.growdaily.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.MoodItem

class MoodAdapter(
    private val items: List<MoodItem>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<MoodAdapter.MoodViewHolder>() {

    private var selectedPosition = 0

    inner class MoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.itemContainer)
        val emoji: TextView = itemView.findViewById(R.id.moodEmoji)
        val title: TextView = itemView.findViewById(R.id.moodTitle)
    }

    interface OnItemClickListener {
        fun onMoodItemClick(moodItem: MoodItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_mood_item, parent, false)
        return MoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        val item = items[position]
        holder.emoji.text = item.emoji
        holder.title.text = item.title

        // Check if selected
        if (selectedPosition == position) {
            holder.container.backgroundTintList =
                ContextCompat.getColorStateList(
                    holder.itemView.context,
                    R.color.category_dark_blue_25
                )

            holder.title.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.black
                )
            )
            holder.title.setTypeface(null, Typeface.BOLD)
        } else {
            holder.container.backgroundTintList =
                ContextCompat.getColorStateList(holder.itemView.context, R.color.background_color)

            holder.title.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.black
                )
            )
            holder.title.setTypeface(null, Typeface.NORMAL)
        }

        // Handle click
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            listener.onMoodItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }
}
