package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.RvListVerticalBinding

class ListVerticalAdapter(
    private var listItems: MutableList<ListEntity>,
    private val listener: OnItemClickListener,
    private val dragStart: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ListVerticalAdapter.ListViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(item: ListEntity)
    }

    inner class ListViewHolder(val binding: RvListVerticalBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {

        val binding = RvListVerticalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {

        val item = listItems[position]

        holder.binding.txtTitle.text = item.listTitle

        holder.itemView.setOnClickListener {
            listener.onItemClick(item)
        }

        // start drag
        holder.binding.imgDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                dragStart(holder)
            }
            false
        }
    }

    override fun getItemCount() = listItems.size

    fun updateList(newList: List<ListEntity>) {
        listItems = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        val item = listItems.removeAt(from)
        listItems.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getCurrentList(): List<ListEntity> = listItems
}
