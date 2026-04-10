package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
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
            val context = binding.root.context
            val isSelected = selectedIds.contains(list.id)

            binding.tvListTitle.text = list.listTitle
            binding.tvListMeta.text = if (isSelected) {
                context.getString(R.string.list_selected_meta)
            } else {
                context.getString(R.string.list_unselected_meta)
            }
            binding.tvSelectionState.text = if (isSelected) {
                context.getString(R.string.state_selected)
            } else {
                context.getString(R.string.state_add)
            }
            binding.tvSelectionState.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.brand_blue else R.color.task_done_track
                )
            )
            binding.tvSelectionState.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.white else R.color.task_text_secondary
                )
            )
            binding.rowContainer.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.task_filter_selected_bg else R.color.task_card_surface
                )
            )
            binding.tvListTitle.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.brand_blue else R.color.task_text_primary
                )
            )

            binding.rowContainer.setOnClickListener {
                val nowSelected = !selectedIds.contains(list.id)
                if (nowSelected) {
                    selectedIds.add(list.id)
                } else {
                    selectedIds.remove(list.id)
                }
                notifyItemChanged(bindingAdapterPosition)
                onCheckedChange(list.id, nowSelected)
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
