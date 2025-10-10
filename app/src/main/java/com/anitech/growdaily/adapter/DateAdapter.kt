package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import java.time.LocalDate

class DateAdapter(
    private val today: LocalDate,
    private val totalItems: Int = 365,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    private var diaryDates: List<String> = emptyList()
    private var selectedPosition: Int = RecyclerView.NO_POSITION  // track selection

    interface OnItemClickListener {
        fun addIntoDiary(date: LocalDate)
        fun scrollToDiary(date: LocalDate)
    }

    inner class DateViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val month: TextView = view.findViewById(R.id.tvMonth)
        val dayNumber: TextView = view.findViewById(R.id.tvDayNumber)
        val dayName: TextView = view.findViewById(R.id.tvDayName)
        val infoContainer: LinearLayout = view.findViewById(R.id.infoContainer)
        val footer: View = view.findViewById(R.id.footer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_calender_item, parent, false)
        return DateViewHolder(v)
    }

    override fun getItemCount(): Int = totalItems

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        val date = today.minusDays(position.toLong())
 

        if (date.toString() == today.toString()){
            holder.month.setTextColor(ContextCompat.getColor(holder.view.context, R.color.category_dark_blue))
            holder.dayNumber.setTextColor(ContextCompat.getColor(holder.view.context, R.color.category_dark_blue))
            holder.dayName.setTextColor(ContextCompat.getColor(holder.view.context, R.color.category_dark_blue))

        }else{
            holder.month.setTextColor(ContextCompat.getColor(holder.view.context, R.color.gray))
            holder.dayNumber.setTextColor(ContextCompat.getColor(holder.view.context, R.color.black))
            holder.dayName.setTextColor(ContextCompat.getColor(holder.view.context, R.color.gray))
        }
        holder.month.text = date.month.name.take(3)
        holder.dayNumber.text = date.dayOfMonth.toString()
        holder.dayName.text = date.dayOfWeek.name.take(3)

        // Default background white
        holder.infoContainer.backgroundTintList =
            ContextCompat.getColorStateList(holder.view.context, R.color.white)

        if (diaryDates.contains(date.toString())) {
            holder.footer.visibility = View.VISIBLE
        } else {
            holder.footer.visibility = View.INVISIBLE
        }

        // Highlight only selected item
        if (selectedPosition == holder.adapterPosition) {
            holder.infoContainer.backgroundTintList =
                ContextCompat.getColorStateList(holder.view.context, R.color.category_dark_blue_25)
        }

        holder.itemView.setOnClickListener {
            if (diaryDates.contains(date.toString())) {
                // update selected position
                val previous = selectedPosition
                selectedPosition = holder.adapterPosition
                notifyItemChanged(previous)
                notifyItemChanged(selectedPosition)

                listener.scrollToDiary(date)
            } else {
                listener.addIntoDiary(date)
            }
        }
    }

    fun updateData(newList: List<String>) {
        diaryDates = newList
        notifyDataSetChanged()
    }
}

