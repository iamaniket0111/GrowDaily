package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.databinding.ScoreLayoutBinding
import java.util.Locale
import kotlin.math.roundToInt

class ScoreSectionAdapter : RecyclerView.Adapter<ScoreSectionAdapter.ViewHolder>() {

    private var dayScore: Float = 0f
    private var weekScore: Float = 0f
    private var monthScore: Float = 0f
    private var dayText: String = ""
    private var weekText: String = ""
    private var monthText: String = ""

    fun updateScores(
        dayScore: Float,
        weekScore: Float,
        monthScore: Float,
        dayText: String,
        weekText: String,
        monthText: String
    ) {
        this.dayScore = dayScore
        this.weekScore = weekScore
        this.monthScore = monthScore
        this.dayText = dayText
        this.weekText = weekText
        this.monthText = monthText
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ScoreLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(private val binding: ScoreLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // Set max to 10 to match your scoring scale
            binding.cpi.max = 10
            binding.cpiWeek.max = 10
            binding.cpiMonth.max = 10
        }

        fun bind() {
            binding.doneWeight.text = formatScore(dayScore)
            binding.cpi.setProgress(dayScore.roundToInt(), true)
            binding.dayText.text = dayText

            binding.doneWeekWeight.text = formatScore(weekScore)
            binding.cpiWeek.setProgress(weekScore.roundToInt(), true)
            binding.weekText.text = weekText

            binding.doneMonthWeight.text = formatScore(monthScore)
            binding.cpiMonth.setProgress(monthScore.roundToInt(), true)
            binding.monthText.text = monthText
        }

        private fun formatScore(value: Float): String {
            return if (value % 1f == 0f) {
                value.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.1f", value)
            }
        }
    }
}
