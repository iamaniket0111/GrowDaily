package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AiChatMessage
import com.anitech.growdaily.data_class.ChatSender
import com.anitech.growdaily.databinding.ItemChatMessageAiBinding
import com.anitech.growdaily.databinding.ItemChatMessageUserBinding

class AiChatAdapter(
    private val onAddSuggestedTaskClicked: (messageId: String, taskIndex: Int) -> Unit
) : ListAdapter<AiChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    private var accentColor: Int = 0

    fun updateAccentColor(color: Int) {
        this.accentColor = color
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<AiChatMessage>() {
            override fun areItemsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).sender) {
            ChatSender.USER -> VIEW_TYPE_USER
            ChatSender.AI, ChatSender.SYSTEM -> VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemChatMessageUserBinding.inflate(inflater, parent, false)
            UserViewHolder(binding)
        } else {
            val binding = ItemChatMessageAiBinding.inflate(inflater, parent, false)
            AiViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is UserViewHolder) {
            holder.bind(item, accentColor)
        } else if (holder is AiViewHolder) {
            holder.bind(item, accentColor, onAddSuggestedTaskClicked)
        }
    }

    class UserViewHolder(private val binding: ItemChatMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AiChatMessage, accentColor: Int) {
            binding.tvUserMessage.text = item.text
            if (accentColor != 0) {
                binding.userBubbleCard.setCardBackgroundColor(accentColor)
            }
        }
    }

    class AiViewHolder(private val binding: ItemChatMessageAiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: AiChatMessage,
            accentColor: Int,
            onAddSuggestedTaskClicked: (messageId: String, taskIndex: Int) -> Unit
        ) {
            if (accentColor != 0) {
                binding.ivAiAvatar.setColorFilter(accentColor)
            }

            if (item.isLoading) {
                binding.tvAiMessage.visibility = View.GONE
                binding.progressLoading.visibility = View.VISIBLE
            } else {
                binding.progressLoading.visibility = View.GONE
                binding.tvAiMessage.visibility = View.VISIBLE
                binding.tvAiMessage.text = item.text

                if (item.isError) {
                    binding.tvAiMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                    )
                } else {
                    binding.tvAiMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.task_text_primary)
                    )
                }
            }

            // Bind suggested task action cards
            binding.containerSuggestedTasks.removeAllViews()
            val tasks = item.suggestedTasks
            if (!tasks.isNullOrEmpty() && !item.isLoading) {
                binding.containerSuggestedTasks.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(itemView.context)

                tasks.forEachIndexed { index, suggestedTask ->
                    val cardView = inflater.inflate(
                        R.layout.item_suggested_task_card,
                        binding.containerSuggestedTasks,
                        false
                    )

                    val tvTitle = cardView.findViewById<TextView>(R.id.tvTaskTitle)
                    val tvSubtitle = cardView.findViewById<TextView>(R.id.tvTaskSubtitle)
                    val btnAdd = cardView.findViewById<Button>(R.id.btnAddSuggestedTask)

                    tvTitle.text = suggestedTask.title

                    val subtitleDetails = buildList {
                        suggestedTask.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                        suggestedTask.scheduleTime?.takeIf { it.isNotBlank() }?.let { add("⏰ $it") }
                        suggestedTask.repeatType.takeIf { it.isNotBlank() }?.let { add("🔄 $it") }
                    }.joinToString(" • ")

                    tvSubtitle.text = if (subtitleDetails.isNotBlank()) subtitleDetails else "Suggested Habit"

                    if (suggestedTask.isAdded) {
                        btnAdd.text = "Added ✓"
                        btnAdd.isEnabled = false
                    } else {
                        btnAdd.text = "+ Add"
                        btnAdd.isEnabled = true
                        btnAdd.setOnClickListener {
                            onAddSuggestedTaskClicked(item.id, index)
                        }
                    }

                    if (accentColor != 0 && !suggestedTask.isAdded) {
                        btnAdd.setTextColor(accentColor)
                    }

                    binding.containerSuggestedTasks.addView(cardView)
                }
            } else {
                binding.containerSuggestedTasks.visibility = View.GONE
            }
        }
    }
}
