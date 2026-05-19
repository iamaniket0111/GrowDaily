package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
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
    private var accentColor: Int? = null
    private var attachedHolder: ViewHolder? = null
    private var pendingAnimateDay: Boolean = false
    private var pendingAnimateWeek: Boolean = false
    private var pendingAnimateMonth: Boolean = false

    fun updateScores(
        dayScore: Float,
        weekScore: Float,
        monthScore: Float,
        dayText: String,
        weekText: String,
        monthText: String
    ) {
        val dayScoreChanged = this.dayScore != dayScore
        val weekScoreChanged = this.weekScore != weekScore
        val monthScoreChanged = this.monthScore != monthScore
        val contentChanged =
            dayScoreChanged ||
                weekScoreChanged ||
                monthScoreChanged ||
                this.dayText != dayText ||
                this.weekText != weekText ||
                this.monthText != monthText

        if (!contentChanged) return

        this.dayScore = dayScore
        this.weekScore = weekScore
        this.monthScore = monthScore
        this.dayText = dayText
        this.weekText = weekText
        this.monthText = monthText

        attachedHolder?.bind(
            animateDay = dayScoreChanged,
            animateWeek = weekScoreChanged,
            animateMonth = monthScoreChanged
        ) ?: run {
            pendingAnimateDay = dayScoreChanged
            pendingAnimateWeek = weekScoreChanged
            pendingAnimateMonth = monthScoreChanged
            notifyItemChanged(0)
        }
    }

    fun setAccentColor(color: Int) {
        if (this.accentColor == color) return
        this.accentColor = color
        attachedHolder?.bind(
            animateDay = false,
            animateWeek = false,
            animateMonth = false
        ) ?: run {
            pendingAnimateDay = false
            pendingAnimateWeek = false
            pendingAnimateMonth = false
            notifyItemChanged(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ScoreLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        attachedHolder = holder
        holder.bind(
            animateDay = pendingAnimateDay,
            animateWeek = pendingAnimateWeek,
            animateMonth = pendingAnimateMonth
        )
        pendingAnimateDay = false
        pendingAnimateWeek = false
        pendingAnimateMonth = false
    }

    override fun getItemCount(): Int = 1

    override fun onViewRecycled(holder: ViewHolder) {
        if (attachedHolder === holder) {
            attachedHolder = null
        }
        super.onViewRecycled(holder)
    }

    inner class ViewHolder(private val binding: ScoreLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.cpi.max = 100
            binding.cpiWeek.max = 100
            binding.cpiMonth.max = 100
        }

        fun bind(
            animateDay: Boolean,
            animateWeek: Boolean,
            animateMonth: Boolean
        ) {
            val context = binding.root.context
            
            // Define base colors for Week and Month
            val baseWeekColor = ContextCompat.getColor(context, R.color.category_purple)
            val baseMonthColor = ContextCompat.getColor(context, R.color.category_teal)
            
            // Dynamic color assignment to avoid clashes
            val (finalDayColor, finalWeekColor, finalMonthColor) = if (accentColor != null) {
                val accent = accentColor!!
                
                // If accent is Purple, shift Week to Orange. Otherwise use Purple.
                val week = if (accent == baseWeekColor) {
                    ContextCompat.getColor(context, R.color.category_orange)
                } else {
                    baseWeekColor
                }
                
                // If accent is Teal, shift Month to Green. Otherwise use Teal.
                val month = if (accent == baseMonthColor) {
                    ContextCompat.getColor(context, R.color.category_green)
                } else {
                    baseMonthColor
                }
                
                Triple<Int, Int, Int>(accent, week, month)
            } else {
                // Default fallback if accent is not yet loaded
                Triple<Int, Int, Int>(
                    ContextCompat.getColor(context, R.color.category_dark_blue),
                    baseWeekColor,
                    baseMonthColor
                )
            }

            // Apply colors to Today Card
            binding.cpi.setIndicatorColor(finalDayColor)
            binding.cpi.trackColor = finalDayColor.adjustAlpha(0.25f)
            binding.doneWeight.setTextColor(finalDayColor)
            binding.doneWeight.text = formatScore(dayScore)
            binding.cpi.setProgressCompat(scoreToProgress(dayScore), animateDay)
            binding.dayText.text = dayText

            // Apply colors to Week Card
            binding.cpiWeek.setIndicatorColor(finalWeekColor)
            binding.cpiWeek.trackColor = finalWeekColor.adjustAlpha(0.25f)
            binding.doneWeekWeight.setTextColor(finalWeekColor)
            binding.doneWeekWeight.text = formatScore(weekScore)
            binding.cpiWeek.setProgressCompat(scoreToProgress(weekScore), animateWeek)
            binding.weekText.text = weekText

            // Apply colors to Month Card
            binding.cpiMonth.setIndicatorColor(finalMonthColor)
            binding.cpiMonth.trackColor = finalMonthColor.adjustAlpha(0.25f)
            binding.doneMonthWeight.setTextColor(finalMonthColor)
            binding.doneMonthWeight.text = formatScore(monthScore)
            binding.cpiMonth.setProgressCompat(scoreToProgress(monthScore), animateMonth)
            binding.monthText.text = monthText
        }

        private fun formatScore(value: Float): String {
            return if (value % 1f == 0f) {
                value.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.1f", value)
            }
        }

        private fun scoreToProgress(value: Float): Int {
            return (value.coerceIn(0f, 10f) * 10f).roundToInt()
        }
    }
}
