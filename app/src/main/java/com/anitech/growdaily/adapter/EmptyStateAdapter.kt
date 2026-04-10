package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.databinding.NoDayTaskLayoutBinding

class EmptyStateAdapter : RecyclerView.Adapter<EmptyStateAdapter.ViewHolder>() {

    private var isVisible: Boolean = false
    @DrawableRes
    private var imageRes: Int = R.drawable.add_task_ic
    private var title: String = "Nothing scheduled for this day"
    private var subtitle: String = "Tap + to add a task for today."

    fun setVisible(visible: Boolean) {
        if (this.isVisible != visible) {
            this.isVisible = visible
            notifyDataSetChanged()
        }
    }

    fun setContent(
        @DrawableRes imageRes: Int,
        title: String,
        subtitle: String
    ) {
        if (this.imageRes == imageRes && this.title == title && this.subtitle == subtitle) return
        this.imageRes = imageRes
        this.title = title
        this.subtitle = subtitle
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = NoDayTaskLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.visibility = if (isVisible) View.VISIBLE else View.GONE
        holder.binding.ivEmptyStateImage.setImageResource(imageRes)
        holder.binding.tvEmptyStateTitle.text = title
        holder.binding.tvEmptyStateSubtitle.text = subtitle
        // Adjust layout params to ensure it doesn't take space when hidden
        val params = holder.itemView.layoutParams
        if (!isVisible) {
            params.height = 0
            params.width = 0
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        holder.itemView.layoutParams = params
    }

    override fun getItemCount(): Int = if (isVisible) 1 else 0

    class ViewHolder(val binding: NoDayTaskLayoutBinding) : RecyclerView.ViewHolder(binding.root)
}
