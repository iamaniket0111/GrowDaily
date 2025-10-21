package com.anitech.growdaily.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ConditionEntity
import com.anitech.growdaily.data_class.DateDataEntity

class ConditionAdapter(
    private var conditionList: List<ConditionEntity>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<ConditionAdapter.ConditionViewHolder>() {

    private var conditionDataItemList: List<DateDataEntity> = emptyList()
    var isSelectingMode = false

    interface OnItemClickListener {
        fun onItemClick(conditionItem: ConditionEntity, isSelected: Boolean)
        fun onNoneClick()
        fun onLongPress(conditionList: List<ConditionEntity>)
    }

    inner class ConditionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.conditionTv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_condition_item, parent, false)
        return ConditionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConditionViewHolder, position: Int) {
        val context = holder.itemView.context
        holder.itemView.backgroundTintList =
            ContextCompat.getColorStateList(context, R.color.white)

        Log.d("ConditionAdapterTag", "data changed")

        if (position == 0) {
            // "None" item
            holder.textView.text = "None"
            holder.itemView.setOnClickListener {
                if (conditionDataItemList.isNotEmpty()) {
                    listener.onNoneClick()
                }
            }

            // Highlight if no condition selected
            if (conditionDataItemList.isEmpty()) {
                holder.itemView.backgroundTintList =
                    ContextCompat.getColorStateList(context, R.color.category_dark_blue_25)
            }
        } else {
            val item = conditionList[position - 1] // -1 because "None" is first
            holder.textView.text = item.conditionTitle

            // Highlight if this item is present in conditionDataItemList
            val isSelected = conditionDataItemList.any { it.type == item.conditionTitle }
            if (isSelected) {
                holder.itemView.backgroundTintList =
                    ContextCompat.getColorStateList(context, R.color.category_dark_blue_25)
            }

            holder.itemView.setOnClickListener {
                if (!isSelectingMode) {
                    listener.onItemClick(item, isSelected)
                }
            }

            holder.itemView.setOnLongClickListener {
                if (!isSelectingMode){
                    listener.onLongPress(conditionList)
                    true
                }else false

            }
        }
    }

    override fun getItemCount(): Int = conditionList.size + 1

    fun setData(newList: List<ConditionEntity>) {
        conditionList = newList
        notifyDataSetChanged()
        Log.d("ConditionAdapterTag", "new data set")
    }

    fun updateDate(newList: List<DateDataEntity>) {
        conditionDataItemList = newList
        notifyDataSetChanged()
        Log.d("ConditionAdapterTag", "updateDate: list provided (${newList.size})")
    }
}