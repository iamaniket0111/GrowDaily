package com.anitech.scoremyday.adapter

import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.BarView
import com.anitech.scoremyday.CommonMethods.Companion.getTodayDate
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.DailyScore

class BarAdapter(
    private var items: List<DailyScore>,
    private val listener: (DailyScore) -> Unit
) : RecyclerView.Adapter<BarAdapter.BarViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    inner class BarViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.rv_score_bar, parent, false)
        return BarViewHolder(v)
    }

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {
        val item = items[position]
        holder.barView.setScore(item.score)
        holder.textDate.text = item.dayText

        if (item.date == getTodayDate()){
            holder.textDate.setTextColor(ContextCompat.getColor(holder.view.context, R.color.category_dark_blue))
        }else{
            holder.textDate.setTextColor(ContextCompat.getColor(holder.view.context, R.color.black))
        }

        // 🔹 Background handling
        if (position == selectedPosition) {
            holder.view.setBackgroundResource(R.drawable.circular_corners_stroke)
            holder.textDate.setTypeface(null, Typeface.BOLD)
        } else {
            holder.view.setBackgroundResource(0)
            holder.textDate.setTypeface(null, Typeface.NORMAL)
        }
        Log.d("BarAdapter", "dates: ${item.date}")

        // 🔹 Click listener
        holder.view.setOnClickListener {
            val previousPos = selectedPosition
            selectedPosition = holder.getAdapterPosition()

            // refresh only changed items
            if (previousPos != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousPos)
            }
            notifyItemChanged(selectedPosition)

            listener(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<DailyScore>) {
        items = newList
        notifyDataSetChanged()

        // Agar abhi tak koi selection nahi hai tabhi default today select karo
        if (selectedPosition == RecyclerView.NO_POSITION) {
            val today = getTodayDate()
            Log.d("BarAdapter", "Today's date:$today")
            selectedPosition = items.indexOfFirst { it.date == today }
            if (selectedPosition == -1) selectedPosition = RecyclerView.NO_POSITION
        }
    }

}
