package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.RvConditionCheckItemBinding

class ListCheckAdapter(
    private var lists: List<ListEntity>,
    private val onCheckedChange: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ListCheckAdapter.ListViewHolder>() {

    private val selectedIds = mutableSetOf<String>()


    inner class ListViewHolder(
        private val binding: RvConditionCheckItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(list: ListEntity) {
            binding.checkBox.text = list.listTitle

            binding.checkBox.setOnCheckedChangeListener(null)
            binding.checkBox.isChecked = selectedIds.contains(list.id)

            binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedIds.add(list.id)
                } else {
                    selectedIds.remove(list.id)
                }
                onCheckedChange(list.id, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = RvConditionCheckItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(lists[position])
    }

    override fun getItemCount() = lists.size

    fun updateData(newLists: List<ListEntity>) {
        lists = newLists
        notifyDataSetChanged()
    }

    fun setPreselectedIds(ids: List<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<String> = selectedIds.toList()
}



