package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.AiChatMessage
import com.anitech.growdaily.data_class.ChatSender
import com.anitech.growdaily.data_class.SuggestedTask
import com.anitech.growdaily.databinding.ItemChatMessageAiBinding
import com.anitech.growdaily.databinding.ItemChatMessageUserBinding
import com.google.android.material.button.MaterialButton

class AiChatAdapter(
    private val onAddSuggestedTaskClicked: (messageId: String, taskIndex: Int) -> Unit,
    private val onAddAllSuggestedTasksClicked: (messageId: String) -> Unit,
    private val onModifySuggestedTaskClicked: (suggestedTask: SuggestedTask) -> Unit,
    private val onDismissSuggestedTaskClicked: (messageId: String, taskIndex: Int) -> Unit
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
            else -> VIEW_TYPE_AI
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
            holder.bind(item)
        } else if (holder is AiViewHolder) {
            holder.bind(item)
        }
    }

    inner class UserViewHolder(private val binding: ItemChatMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AiChatMessage) {
            binding.tvUserMessage.text = item.text
        }
    }

    inner class AiViewHolder(private val binding: ItemChatMessageAiBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AiChatMessage) {
            if (accentColor != 0) {
                binding.ivAiAvatar.imageTintList = ColorStateList.valueOf(accentColor)
                binding.progressLoading.setIndicatorColor(accentColor)
            }

            if (item.isLoading) {
                binding.progressLoading.visibility = View.VISIBLE
                binding.tvAiMessage.visibility = View.GONE
            } else {
                binding.progressLoading.visibility = View.GONE
                binding.tvAiMessage.visibility = View.VISIBLE
                binding.tvAiMessage.text = com.anitech.growdaily.util.MarkdownHelper.toSpannable(item.text)

                binding.tvAiMessage.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.task_text_primary)
                )
            }

            // Bind suggested task action cards
            binding.containerSuggestedTasks.removeAllViews()
            val tasks = item.suggestedTasks
            if (!tasks.isNullOrEmpty() && !item.isLoading) {
                binding.containerSuggestedTasks.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(itemView.context)

                // Render "Add All" Header if there are 2 or more un-added tasks
                val unaddedCount = tasks.count { !it.isAdded }
                if (unaddedCount >= 2) {
                    val headerView = inflater.inflate(
                        R.layout.item_suggested_header,
                        binding.containerSuggestedTasks,
                        false
                    )
                    val btnAddAll = headerView.findViewById<MaterialButton>(R.id.btnAddAllTasks)
                    btnAddAll.text = "⚡ Add All ($unaddedCount Tasks)"
                    if (accentColor != 0) {
                        btnAddAll.backgroundTintList = ColorStateList.valueOf(accentColor)
                    }
                    btnAddAll.setOnClickListener {
                        onAddAllSuggestedTasksClicked(item.id)
                    }
                    binding.containerSuggestedTasks.addView(headerView)
                }

                tasks.forEachIndexed { index, suggestedTask ->
                    val cardView = inflater.inflate(
                        R.layout.item_suggested_task_card,
                        binding.containerSuggestedTasks,
                        false
                    )

                    val tvTitle = cardView.findViewById<TextView>(R.id.tvTaskTitle)
                    val tvSubtitle = cardView.findViewById<TextView>(R.id.tvTaskSubtitle)
                    val btnAdd = cardView.findViewById<MaterialButton>(R.id.btnAddSuggestedTask)
                    val btnModify = cardView.findViewById<MaterialButton>(R.id.btnModifySuggestedTask)
                    val btnDismiss = cardView.findViewById<ImageButton>(R.id.btnDismissTask)

                    tvTitle.text = suggestedTask.title

                    val typeLabel = when (suggestedTask.safeTaskType) {
                        "DAY", "UNTIL_COMPLETE" -> "📅 Day Task"
                        else -> "🔄 Daily"
                    }

                    val trackingLabel = when (suggestedTask.safeTrackingType) {
                        "TIMER" -> {
                            val sec = suggestedTask.targetDurationSeconds ?: 0L
                            val mins = sec / 60
                            if (mins > 0) "⏱️ ${mins}m" else "⏱️ Timer"
                        }
                        "COUNT", "COUNTER" -> {
                            val count = suggestedTask.dailyTargetCount ?: 1
                            "🔢 Target: $count"
                        }
                        "CHECKLIST" -> {
                            val itemsCount = suggestedTask.checklistItems?.size ?: 0
                            if (itemsCount > 0) "📋 $itemsCount items" else "📋 Checklist"
                        }
                        else -> null
                    }

                    val subtitleDetails = buildList {
                        suggestedTask.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                        suggestedTask.scheduleTime?.takeIf { it.isNotBlank() }?.let { add("⏰ $it") }
                        add(typeLabel)
                        trackingLabel?.let { add(it) }
                    }.joinToString(" • ")

                    tvSubtitle.text = if (subtitleDetails.isNotBlank()) subtitleDetails else "Suggested Habit"

                    if (suggestedTask.isAdded) {
                        btnAdd.text = "Added ✓"
                        btnAdd.isEnabled = false
                        btnModify.visibility = View.GONE
                    } else {
                        btnAdd.text = "+ Add"
                        btnAdd.isEnabled = true
                        btnModify.visibility = View.VISIBLE
                        btnAdd.setOnClickListener {
                            onAddSuggestedTaskClicked(item.id, index)
                        }
                        btnModify.setOnClickListener {
                            onModifySuggestedTaskClicked(suggestedTask)
                        }
                    }

                    btnDismiss.setOnClickListener {
                        onDismissSuggestedTaskClicked(item.id, index)
                    }

                    if (accentColor != 0 && !suggestedTask.isAdded) {
                        btnAdd.setTextColor(accentColor)
                        btnAdd.strokeColor = ColorStateList.valueOf(accentColor)
                        btnModify.setTextColor(accentColor)
                        btnModify.strokeColor = ColorStateList.valueOf(accentColor)
                    }

                    binding.containerSuggestedTasks.addView(cardView)
                }
            } else {
                binding.containerSuggestedTasks.visibility = View.GONE
            }
        }
    }
}
