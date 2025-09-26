package com.anitech.scoremyday.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.ConditionEntity
import java.util.Collections

class ConditionReorderAdapter(
    private var conditionList: MutableList<ConditionEntity>,
    private val touchHelperProvider: TouchHelperProvider
) : RecyclerView.Adapter<ConditionReorderAdapter.ConditionViewHolder>() {

    inner class ConditionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.conditionTv)
        val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_reorder_condition_item, parent, false)
        return ConditionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConditionViewHolder, position: Int) {
        val condition = conditionList[position]
        holder.tvTitle.text = condition.conditionTitle

        // dragHandle se drag start
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                touchHelperProvider.startDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = conditionList.size

    fun swapItems(fromPosition: Int, toPosition: Int) {
        Collections.swap(conditionList, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun setData(newList: List<ConditionEntity>) {
        conditionList = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<ConditionEntity> =
        conditionList.mapIndexed { index, item -> item.copy(sortOrder = index) }
}
