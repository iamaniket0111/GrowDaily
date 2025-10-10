package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.MoodHistoryItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WeekMoodAdapter(
    private var moodHistory: List<MoodHistoryItem>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<WeekMoodAdapter.WeekMoodViewHolder>() {

    private val weekDays: List<String>
    private val weekDates: List<LocalDate>
    private val today: LocalDate = LocalDate.now()
    private var selectedPosition: Int = -1 // track selected item

    init {
        val currentDayOfWeek = today.dayOfWeek.value // 1=Mon ... 7=Sun
        val monday = today.minusDays((currentDayOfWeek - 1).toLong())

        weekDates = (0..6).map { monday.plusDays(it.toLong()) }
        weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        // set default selection = today's index
        selectedPosition = weekDates.indexOfFirst { it.isEqual(today) }
    }

    interface OnItemClickListener {
        fun onMoodItemClick(moodHistoryItem: MoodHistoryItem)
    }

    inner class WeekMoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val moodEmoji: TextView = itemView.findViewById(R.id.dayMood)
        val weekDay: TextView = itemView.findViewById(R.id.weekDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekMoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_mood_history, parent, false)
        return WeekMoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekMoodViewHolder, position: Int) {
        val dayName = weekDays[position]
        val date = weekDates[position]

        holder.weekDay.text = dayName

        if (date.isAfter(today)) {
            // Future dates
            holder.moodEmoji.text = "--"
            holder.moodEmoji.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.category_dark_blue)
            )
            holder.weekDay.setTextColor(ColorStateList.valueOf(Color.GRAY))
            holder.itemView.isClickable = false
            holder.itemView.backgroundTintList = null
        } else {
            // Past or today
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val dateStr = date.format(formatter)
            val moodItem = moodHistory.find { it.date == dateStr }

            val finalMoodItem = moodItem ?: MoodHistoryItem(
                id = 0,
                emoji = "😐",
                date = dateStr
            )

            holder.moodEmoji.text = finalMoodItem.emoji

            if (date.isEqual(today)) {
                holder.weekDay.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.category_dark_blue)
                )
            } else {
                holder.weekDay.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.black)
                )
            }

            if (position == selectedPosition) {
                holder.itemView.backgroundTintList =
                    ContextCompat.getColorStateList(
                        holder.itemView.context,
                        R.color.category_dark_blue_25
                    )
            } else {
                holder.itemView.backgroundTintList = null
            }

            holder.itemView.setOnClickListener {
                val oldPos = selectedPosition
                selectedPosition = holder.adapterPosition
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPosition)
                listener.onMoodItemClick(finalMoodItem)
            }
        }
    }

    override fun getItemCount() = weekDays.size

    fun updateData(newList: List<MoodHistoryItem>) {
        moodHistory = newList
        notifyDataSetChanged()
    }
}
