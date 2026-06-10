package com.anitech.growdaily.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.databinding.ItemChecklistLabelRowBinding

class ChecklistAdapter(
    private val onRemoveItem: (Int) -> Unit,
    private val onEditItem: (Int, String) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<String, ChecklistAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChecklistLabelRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemChecklistLabelRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: String) {
            binding.tvChecklistLabel.text = item

            // Drag trigger - start immediately on touch down
            binding.ivDragHandle.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onStartDrag(this)
                }
                false
            }
            
            // Edit trigger
            binding.tvChecklistLabel.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onEditItem(currentPos, item)
                }
            }

            binding.btnRemoveChecklistItem.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onRemoveItem(currentPos)
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem == newItem
            }
        }
    }
}
