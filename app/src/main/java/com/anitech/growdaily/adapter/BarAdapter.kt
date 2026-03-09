package com.anitech.growdaily.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.BarView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class BarAdapter(
    private val listener: OnBarInteractionListener
) : RecyclerView.Adapter<BarAdapter.BarViewHolder>() {
    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private val today: LocalDate = LocalDate.now()
    private val rangeDays = 91
     private var startDate: LocalDate = today.minusDays(rangeDays / 2L)
    private val totalDays: Int = rangeDays
    private var centerDate: LocalDate = LocalDate.now()



    var isSelectingMode = false
     private var scoreMap: Map<String, DailyScore> = emptyMap()

    interface OnBarInteractionListener {
        fun onBarSelected(dailyScore: DailyScore)
        fun onTodayBarOutOfView()
    }

    inner class BarViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_bar, parent, false)
        return BarViewHolder(view)
    }

    override fun onBindViewHolder(holder: BarViewHolder, position: Int) {
        val currentDate = startDate.plusDays(position.toLong())
        val dateString = currentDate.toString()

        val dailyScore = scoreMap[dateString]
            ?: DailyScore(
                date = dateString,
                dayText = currentDate.dayOfMonth.toString(),
                monthDayText = "${currentDate.monthValue}/${currentDate.dayOfMonth}",
                score = 0f,
                taskCount = 0
            )



        // Set score
        holder.barView.setScore(dailyScore.score)

        // Update date text
        holder.textDate.text =
            if (position == selectedPosition) dailyScore.monthDayText else dailyScore.dayText

        // Highlight today
        val colorRes = if (currentDate == today) R.color.category_dark_blue else R.color.black
        holder.textDate.setTextColor(ContextCompat.getColor(holder.view.context, colorRes))

        // Highlight selection
        if (position == selectedPosition) {
            holder.view.setBackgroundResource(R.drawable.circular_corners_stroke)
            holder.textDate.setTypeface(null, Typeface.BOLD)
        } else {
            holder.view.setBackgroundResource(0)
            holder.textDate.setTypeface(null, Typeface.NORMAL)
        }

        // Handle click
        holder.view.setOnClickListener {
            if (!isSelectingMode) {
                val previousPos = selectedPosition
                selectedPosition = holder.adapterPosition
                if (previousPos != RecyclerView.NO_POSITION) notifyItemChanged(previousPos)
                notifyItemChanged(selectedPosition)
                listener.onBarSelected(dailyScore)
            }
        }
    }

    fun updateData(newScores: List<DailyScore>) {
        scoreMap = newScores.associateBy { it.date }
        notifyDataSetChanged()

        if (selectedPosition == RecyclerView.NO_POSITION) {
            selectedPosition =
                ChronoUnit.DAYS.between(startDate, today).toInt()
                    .coerceIn(0, itemCount - 1)
        }
    }



    override fun getItemCount(): Int = totalDays


    fun checkIfTodayVisible(layoutManager: RecyclerView.LayoutManager) {
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()

            val todayIndex = ChronoUnit.DAYS.between(startDate, today).toInt()
            if (todayIndex < firstVisible || todayIndex > lastVisible) {
                listener.onTodayBarOutOfView()
            }
        }
    }

    fun setCenterDate(date: LocalDate) {
        centerDate = date
        startDate = centerDate.minusDays(rangeDays / 2L)
        selectedPosition = rangeDays / 2
        notifyDataSetChanged()
    }



}


