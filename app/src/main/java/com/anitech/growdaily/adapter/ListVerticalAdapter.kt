package com.anitech.growdaily.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.RvListVerticalBinding
import java.util.Collections

class ListVerticalAdapter(
    private var listItems: MutableList<ListEntity>,
    private val listener: OnItemClickListener,
    private val dragStart: (RecyclerView.ViewHolder) -> Unit,
) : RecyclerView.Adapter<ListVerticalAdapter.ListViewHolder>() {

    companion object {
        private const val TAG = "ListVerticalAdapter"
    }

    private var accentColor: Int? = null

    interface OnItemClickListener {
        fun onItemClick(item: ListEntity)
    }

    inner class ListViewHolder(val binding: RvListVerticalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: ListEntity) {
            binding.txtTitle.text = item.listTitle

            accentColor?.let { color ->
                binding.imgDrag.setColorFilter(color)
            }

            itemView.setOnClickListener {
                listener.onItemClick(item)
            }

            binding.imgDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragStart(this)
                }
                false
            }
            
            // Reset visual state
            binding.root.translationZ = 0f
            binding.root.alpha = 1.0f
        }

        fun onItemSelected() {
            binding.root.animate()
                .translationZ(8f)
                .alpha(0.9f)
                .setDuration(150)
                .start()
        }

        fun onItemClear() {
            binding.root.animate()
                .translationZ(0f)
                .alpha(1.0f)
                .setDuration(150)
                .start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = RvListVerticalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(listItems[position])
    }

    override fun getItemCount() = listItems.size

    fun setAccentColor(color: Int) {
        this.accentColor = color
        // Use notifyItemRangeChanged instead of notifyDataSetChanged for better performance
        // This re-binds items without recreating ViewHolders
        if (listItems.isNotEmpty()) {
            notifyItemRangeChanged(0, listItems.size)
        }
        Log.d(TAG, "Accent color updated")
    }

    fun updateList(newList: List<ListEntity>) {
        // Prevent redundant updates if the list hasn't changed
        if (listItems == newList) {
            Log.d(TAG, "List update skipped - no changes detected")
            return
        }

        try {
            val diffResult = DiffUtil.calculateDiff(ListDiffCallback(listItems, newList))
            listItems = newList.toMutableList()
            diffResult.dispatchUpdatesTo(this)
            Log.d(TAG, "List updated with ${newList.size} items using DiffUtil")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating list with DiffUtil, falling back to full refresh", e)
            // Fallback to full refresh using notifyItemRangeChanged instead of notifyDataSetChanged
            listItems = newList.toMutableList()
            if (listItems.isNotEmpty()) {
                notifyItemRangeChanged(0, listItems.size)
            } else {
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }
    }

    fun moveItem(from: Int, to: Int) {
        // Add bounds checking to prevent crashes
        if (from == to) {
            Log.d(TAG, "moveItem: from and to are the same ($from)")
            return
        }

        if ((from < 0 || from >= listItems.size || to < 0 || to >= listItems.size)) {
            Log.e(TAG, "moveItem: Invalid indices - from: $from, to: $to, listSize: ${listItems.size}")
            return
        }

        try {
            Collections.swap(listItems, from, to)
            notifyItemMoved(from, to)
            Log.d(TAG, "Item moved successfully from $from to $to")
        } catch (e: Exception) {
            Log.e(TAG, "Error moving item from $from to $to", e)
        }
    }

    fun getCurrentList(): List<ListEntity> = listItems

    private class ListDiffCallback(
        private val oldList: List<ListEntity>,
        private val newList: List<ListEntity>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos] == newList[newPos]
    }
}
