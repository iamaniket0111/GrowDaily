package com.anitech.growdaily.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.view.BarView
import java.time.LocalDate

class BarAdapter(
    private val listener: OnBarInteractionListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_WEEK = 0
        const val TYPE_LOADING_START = 1
        const val TYPE_LOADING_END = 2
        const val PAYLOAD_SELECTION_CHANGED = "SELECTION_CHANGED"
        const val PAYLOAD_ACCENT_CHANGED = "ACCENT_CHANGED"
    }

    private sealed class BarItem {
        data class Week(val days: List<DailyScore>) : BarItem()
        data object LoadingStart : BarItem()
        data object LoadingEnd : BarItem()
    }

    private var selectedDate: LocalDate = LocalDate.now()
    private var scoreList: List<DailyScore> = emptyList()
    private var isLoadingPast = false
    private var isLoadingFuture = false
    private var items: List<BarItem> = emptyList()
    private var accentColor: Int? = null

    var isSelectingMode = false

    interface OnBarInteractionListener {
        fun onBarSelected(dailyScore: DailyScore)
        fun onTodayBarOutOfView(isFuture: Boolean)
        fun onTodayBarInView()
    }

    init {
        setHasStableIds(true)
    }

    class WeekViewHolder(val view: View, val dayHolders: List<DaySubHolder>) : RecyclerView.ViewHolder(view)

    class DaySubHolder(val view: View) {
        val barView: BarView = view.findViewById(R.id.barView)
        val textDate: TextView = view.findViewById(R.id.textDate)
    }

    class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val progressBar: ProgressBar = view.findViewById(R.id.progressBarEdge)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BarItem.LoadingStart -> TYPE_LOADING_START
            is BarItem.LoadingEnd -> TYPE_LOADING_END
            is BarItem.Week -> TYPE_WEEK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val parentWidth = parent.measuredWidth.takeIf { it > 0 } ?: parent.resources.displayMetrics.widthPixels
        
        return when (viewType) {
            TYPE_LOADING_START, TYPE_LOADING_END -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_bar_loading, parent, false)
                view.layoutParams = ViewGroup.LayoutParams(parentWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                LoadingViewHolder(view)
            }

            else -> {
                val weekLayout = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(parentWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                    weightSum = 7f
                }
                val holders = mutableListOf<DaySubHolder>()
                for (i in 0 until 7) {
                    val dayView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.rv_bar, weekLayout, false)
                    dayView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    weekLayout.addView(dayView)
                    holders.add(DaySubHolder(dayView))
                }
                WeekViewHolder(weekLayout, holders)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LoadingViewHolder -> {
                holder.progressBar.visibility = View.VISIBLE
            }

            is WeekViewHolder -> bindWeek(holder, position)
        }
    }

    private fun bindWeek(holder: WeekViewHolder, position: Int) {
        val week = (items.getOrNull(position) as? BarItem.Week) ?: return
        
        holder.dayHolders.forEachIndexed { index, dayHolder ->
            val score = week.days.getOrNull(index)
            if (score == null) {
                dayHolder.view.visibility = View.INVISIBLE
                dayHolder.view.setOnClickListener(null)
            } else {
                dayHolder.view.visibility = View.VISIBLE
                bindDay(dayHolder, score)
            }
        }
    }

    private fun bindDay(holder: DaySubHolder, score: DailyScore) {
        val currentDate = LocalDate.parse(score.date)

        accentColor?.let { holder.barView.setDefaultBarColor(it) }
        holder.barView.setScore(score.score)
        holder.textDate.text =
            if (currentDate == selectedDate) score.monthDayText else score.dayText

        val colorRes = if (currentDate == LocalDate.now()) {
            R.color.task_bar_today_text
        } else {
            R.color.task_bar_text
        }

        accentColor?.takeIf { currentDate == LocalDate.now() }?.let {
            holder.textDate.setTextColor(it)
        } ?: run {
            holder.textDate.setTextColor(ContextCompat.getColor(holder.view.context, colorRes))
        }

        if (currentDate == selectedDate) {
            holder.view.setBackgroundResource(R.drawable.task_bar_selected_background)
            holder.textDate.setTypeface(null, Typeface.BOLD)
        } else {
            holder.view.setBackgroundResource(0)
            holder.textDate.setTypeface(null, Typeface.NORMAL)
        }

        holder.view.setOnClickListener {
            if (!isSelectingMode) {
                refreshSelection(currentDate)
                listener.onBarSelected(score)
            }
        }
    }

    fun updateData(
        newScores: List<DailyScore>,
        selectedDate: LocalDate,
        isLoadingPast: Boolean,
        isLoadingFuture: Boolean
    ) {
        val oldItems = items
        val newItems = buildItems(newScores, isLoadingPast, isLoadingFuture)
        val previousSelectedDate = this.selectedDate

        this.scoreList = newScores
        val selectionChanged = previousSelectedDate != selectedDate
        this.selectedDate = selectedDate
        this.isLoadingPast = isLoadingPast
        this.isLoadingFuture = isLoadingFuture
        this.items = newItems

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size

            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldItems[oldItemPosition]
                val newItem = newItems[newItemPosition]
                return when {
                    oldItem is BarItem.Week && newItem is BarItem.Week ->
                        oldItem.days.firstOrNull()?.date == newItem.days.firstOrNull()?.date
                    oldItem is BarItem.LoadingStart && newItem is BarItem.LoadingStart -> true
                    oldItem is BarItem.LoadingEnd && newItem is BarItem.LoadingEnd -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }
        }, false)
        diff.dispatchUpdatesTo(this)
        
        if (selectionChanged) {
            notifyWeekChangedForDate(previousSelectedDate, selectionPayload = true)
            notifyWeekChangedForDate(selectedDate, selectionPayload = true)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION_CHANGED) || payloads.contains(PAYLOAD_ACCENT_CHANGED)) {
            if (holder is WeekViewHolder) {
                bindWeek(holder, position)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemId(position: Int): Long {
        return when (val item = items[position]) {
            is BarItem.LoadingStart -> Long.MIN_VALUE + 1
            is BarItem.LoadingEnd -> Long.MIN_VALUE + 2
            is BarItem.Week -> (item.days.firstOrNull()?.date?.hashCode()?.toLong())
                ?: RecyclerView.NO_ID
        }
    }

    fun checkIfTodayVisible(layoutManager: RecyclerView.LayoutManager) {
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            val todayWeekIndex = getAdapterPositionForDate(LocalDate.now())
            
            if (todayWeekIndex == RecyclerView.NO_POSITION || todayWeekIndex < firstVisible || todayWeekIndex > lastVisible) {
                val isFuture = if (todayWeekIndex == RecyclerView.NO_POSITION) {
                    // If not in window, compare first visible week date with today
                    val firstDateStr = getDateAtAdapterPosition(firstVisible)
                    if (firstDateStr != null) {
                        LocalDate.now().isBefore(LocalDate.parse(firstDateStr))
                    } else false
                } else {
                    todayWeekIndex < firstVisible
                }
                listener.onTodayBarOutOfView(isFuture)
            } else {
                listener.onTodayBarInView()
            }
        }
    }

    fun refreshTodayHighlight() {
        notifyWeekChangedForDate(LocalDate.now().minusDays(1))
        notifyWeekChangedForDate(LocalDate.now())
    }

    fun refreshSelection(newSelectedDate: LocalDate) {
        if (this.selectedDate == newSelectedDate) return
        val previousSelectedDate = this.selectedDate
        this.selectedDate = newSelectedDate
        notifyWeekChangedForDate(previousSelectedDate, selectionPayload = true)
        notifyWeekChangedForDate(newSelectedDate, selectionPayload = true)
    }

    fun setAccentColor(color: Int) {
        if (this.accentColor == color) return
        this.accentColor = color
        notifyItemRangeChanged(0, itemCount, PAYLOAD_ACCENT_CHANGED)
    }

    fun getAdapterPositionForDate(date: LocalDate): Int {
        val dayIndex = scoreList.indexOfFirst { it.date == date.toString() }
        if (dayIndex == -1) return RecyclerView.NO_POSITION
        val weekIndex = dayIndex / 7
        return weekIndex + if (isLoadingPast) 1 else 0
    }

    fun shouldLoadMorePast(firstVisiblePosition: Int): Boolean {
        // Trigger load when we are within the first 2 pages (weeks)
        return firstVisiblePosition <= 2 && isLoadingPast
    }

    fun shouldLoadMoreFuture(lastVisiblePosition: Int): Boolean {
        // Trigger load when we are within the last 2 pages (weeks)
        return lastVisiblePosition >= itemCount - 3 && isLoadingFuture
    }

    fun firstRealAdapterPosition(): Int = if (isLoadingPast) 1 else 0

    fun getDateAtAdapterPosition(adapterPosition: Int): String? {
        return (items.getOrNull(adapterPosition) as? BarItem.Week)?.days?.firstOrNull()?.date
    }

    private fun buildItems(
        scores: List<DailyScore>,
        isLoadingPast: Boolean,
        isLoadingFuture: Boolean
    ): List<BarItem> {
        val weeks = scores.chunked(7)
        return buildList {
            if (isLoadingPast) add(BarItem.LoadingStart)
            weeks.forEach { add(BarItem.Week(it)) }
            if (isLoadingFuture) add(BarItem.LoadingEnd)
        }
    }

    private fun notifyWeekChangedForDate(
        date: LocalDate,
        selectionPayload: Boolean = false
    ) {
        val position = getAdapterPositionForDate(date)
        if (position == RecyclerView.NO_POSITION) return
        if (selectionPayload) {
            notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED)
        } else {
            notifyItemChanged(position)
        }
    }
}
