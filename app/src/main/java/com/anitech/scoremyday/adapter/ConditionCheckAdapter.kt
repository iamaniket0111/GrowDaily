package com.anitech.scoremyday.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.data_class.ConditionEntity
import com.anitech.scoremyday.databinding.RvConditionCheckItemBinding

class ConditionCheckAdapter(
    private var conditions: List<ConditionEntity>,
    private var checkBoxEnabled: Boolean,
    private val onCheckedChange: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<ConditionCheckAdapter.ConditionViewHolder>() {

    private val selectedIds = mutableSetOf<Int>()


    inner class ConditionViewHolder(val binding: RvConditionCheckItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(condition: ConditionEntity) {
            binding.checkBox.text = condition.conditionTitle
            binding.checkBox.isEnabled = checkBoxEnabled
            // set listener to null first to avoid unwanted triggers while recycling
            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedIds.contains(condition.id)


            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (binding.checkBox.isEnabled) {
                    if (isChecked) {
                        selectedIds.add(condition.id)
                    } else {
                        selectedIds.remove(condition.id)
                    }
                    onCheckedChange(condition.id, isChecked)
                }
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionViewHolder {
        val binding = RvConditionCheckItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConditionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConditionViewHolder, position: Int) {
        holder.bind(conditions[position])
    }

    override fun getItemCount() = conditions.size

    fun getSelectedIds(): List<Int> = selectedIds.toList()

    fun updateData(newList: List<ConditionEntity>) {
        conditions = newList
        notifyDataSetChanged()
    }

    // 👇 new method: preselect ids (used for edit/update task)
    fun setPreselectedIds(ids: List<Int>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun setCheckBoxEnabled(enabled: Boolean) {
        checkBoxEnabled = enabled
        notifyDataSetChanged()  // refresh UI
    }
}



