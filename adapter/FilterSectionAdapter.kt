package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.databinding.ConditionLayoutBinding

class FilterSectionAdapter(
    private val listAdapter: ListAdapter
) : RecyclerView.Adapter<FilterSectionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ConditionLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(private val binding: ConditionLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.recyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = listAdapter
                addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
                })
            }
        }

        fun bind() {
            // listAdapter updates are handled externally
        }
    }
}
