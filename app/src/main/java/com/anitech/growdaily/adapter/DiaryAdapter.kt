package com.anitech.growdaily.adapter

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.DayLogEntity

class DiaryAdapter(
    private var diaryData: List<DayLogEntity>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<DiaryAdapter.ViewHolder>() {

    private var highlightedPosition: Int? = null

    val currentList: List<DayLogEntity>
        get() = diaryData

    interface OnItemClickListener {
        fun moveToEditListener(diaryEntry: DayLogEntity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_diary_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val diaryEntry = diaryData[position]

        holder.tvDate.text = diaryEntry.date
        if (diaryEntry.title == null) {
            holder.tvTitle.visibility = View.GONE
        } else {
            holder.tvTitle.text = diaryEntry.title
        }

        if (diaryEntry.title == null) {
            holder.tvContent.visibility = View.GONE
        } else {
            holder.tvContent.text = diaryEntry.content
        }

        holder.countTaskDone.text = diaryEntry.doneCount.toString()
        holder.countTaskPending.text = diaryEntry.pendingCount.toString()
        holder.dayProgress.text = diaryEntry.dayScore
        holder.dayMood.text = diaryEntry.emoji

        holder.itemView.setOnClickListener {
            listener.moveToEditListener(diaryEntry)
        }

        if (highlightedPosition == position) {
            holder.itemView.setBackgroundResource(R.drawable.highlight_bg)
        }
    }

    override fun getItemCount(): Int {
        return diaryData.size
    }

    fun updateData(newList: List<DayLogEntity>) {
        diaryData = newList
        notifyDataSetChanged()
    }

    fun highlightItem(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)

        Handler(Looper.getMainLooper()).postDelayed({
            highlightedPosition = null
            notifyItemChanged(position)
        }, 1000)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        val countTaskDone: TextView = itemView.findViewById(R.id.countTaskDone)
        val countTaskPending: TextView = itemView.findViewById(R.id.countTaskPending)
        val dayProgress: TextView = itemView.findViewById(R.id.dayProgress)
        val dayMood: TextView = itemView.findViewById(R.id.dayMood)
    }
}
