package com.anitech.scoremyday.adapter

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.data_class.DiaryEntry

class DiaryAdapter(
    private var diaryData: List<DiaryEntry>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<DiaryAdapter.ViewHolder>() {

    private var highlightedPosition: Int? = null


    val currentList: List<DiaryEntry>
        get() = diaryData

    interface OnItemClickListener {
        fun moveToEditListener(diaryEntry: DiaryEntry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_diary_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val diaryEntry = diaryData[position]

        holder.tvDate.text = diaryEntry.date
        holder.tvTitle.text = diaryEntry.title
        holder.tvContent.text = diaryEntry.content

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

    fun updateData(newList: List<DiaryEntry>) {
        diaryData = newList
        notifyDataSetChanged()
    }

    fun highlightItem(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)

        // 1 sec baad reset
        Handler(Looper.getMainLooper()).postDelayed({
            highlightedPosition = null
            notifyItemChanged(position)
        }, 1000)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvContent: TextView = itemView.findViewById(R.id.tvContent)
    }
}
