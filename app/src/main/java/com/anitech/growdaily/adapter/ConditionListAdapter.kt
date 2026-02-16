package com.anitech.growdaily.adapter

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ListEntity

class ConditionListAdapter(
    private val context: Context,
    private var conditionList: List<ListEntity>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<ConditionListAdapter.ConditionViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(conditionItem: ListEntity)
    }

    inner class ConditionViewHolder(val textView: TextView) :
        RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionViewHolder {
        val textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(12, 16, 12, 16)
            setTextColor(ContextCompat.getColor(context, R.color.black))
            textSize = 16f
        }
        return ConditionViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ConditionViewHolder, position: Int) {
        holder.textView.text = conditionList[position].listTitle
        holder.itemView.setOnClickListener {
            listener.onItemClick(conditionList[position])
        }
    }

    override fun getItemCount(): Int = conditionList.size

    fun updateList(newList: List<ListEntity>) {
        conditionList = newList
        notifyDataSetChanged()
    }
}
