package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
            if (accentColor != 0) {
                binding.userBubbleCard.setCardBackgroundColor(accentColor)
            } else {
                binding.userBubbleCard.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.brand_blue)
                )
            }
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

                    val iconBubble = cardView.findViewById<android.widget.FrameLayout>(R.id.iconBubbleContainer)
                    val ivIcon = cardView.findViewById<ImageView>(R.id.ivSuggestedTaskIcon)
                    val tvTitle = cardView.findViewById<TextView>(R.id.tvTaskTitle)
                    val tvNote = cardView.findViewById<TextView>(R.id.tvTaskNote)
                    val tvTimeBadge = cardView.findViewById<TextView>(R.id.tvTimeBadge)
                    val tvTargetBadge = cardView.findViewById<TextView>(R.id.tvTargetBadge)
                    val tvListBadge = cardView.findViewById<TextView>(R.id.tvListBadge)
                    val containerChecklist = cardView.findViewById<View>(R.id.containerChecklistPreview)
                    val tvChecklist = cardView.findViewById<TextView>(R.id.tvChecklistItems)
                    val btnAdd = cardView.findViewById<MaterialButton>(R.id.btnAddSuggestedTask)
                    val btnModify = cardView.findViewById<MaterialButton>(R.id.btnModifySuggestedTask)
                    val btnDismiss = cardView.findViewById<ImageButton>(R.id.btnDismissTask)

                    val taskColor = runCatching {
                        com.anitech.growdaily.enum_class.TaskColor.valueOf(suggestedTask.safeTaskColor)
                    }.getOrDefault(com.anitech.growdaily.enum_class.TaskColor.DARK_BLUE)
                    val taskColorInt = ContextCompat.getColor(itemView.context, taskColor.resId)
                    val effectiveColor = if (accentColor != 0) accentColor else taskColorInt

                    iconBubble.backgroundTintList = ColorStateList.valueOf(effectiveColor)
                    val taskIcon = com.anitech.growdaily.enum_class.TaskIcon.fromName(suggestedTask.safeTaskIcon)
                    ivIcon.setImageResource(taskIcon.resId)
                    ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.white))

                    tvTitle.text = suggestedTask.title
                    if (!suggestedTask.note.isNullOrBlank()) {
                        tvNote.text = suggestedTask.note
                        tvNote.visibility = View.VISIBLE
                    } else {
                        tvNote.visibility = View.GONE
                    }

                    if (!suggestedTask.scheduleTime.isNullOrBlank()) {
                        val startMins = com.anitech.growdaily.CommonMethods.timeToMinutes(suggestedTask.scheduleTime)
                        val durationSec = suggestedTask.targetDurationSeconds ?: 0L
                        val durationMins = if (durationSec > 0L) (durationSec / 60L).toInt() else 0

                        if (startMins != null && durationMins > 0) {
                            val endMins = (startMins + durationMins) % 1440
                            val endTimeStr = com.anitech.growdaily.CommonMethods.minutesToTime(endMins)
                            val durationStr = com.anitech.growdaily.CommonMethods.formatDuration(durationMins)
                            tvTimeBadge.text = "⏰ ${suggestedTask.scheduleTime} – $endTimeStr ($durationStr)"
                        } else {
                            tvTimeBadge.text = "⏰ ${suggestedTask.scheduleTime}"
                        }
                        tvTimeBadge.visibility = View.VISIBLE
                    } else {
                        tvTimeBadge.visibility = View.GONE
                    }

                    when (suggestedTask.safeTrackingType) {
                        "COUNT", "COUNTER" -> {
                            val count = suggestedTask.dailyTargetCount ?: 1
                            tvTargetBadge.text = "🎯 Target: $count reps"
                            tvTargetBadge.visibility = View.VISIBLE
                        }
                        "TIMER" -> {
                            val sec = suggestedTask.targetDurationSeconds ?: 0L
                            val mins = sec / 60
                            tvTargetBadge.text = if (mins > 0) "⏱️ Timer: ${mins}m" else "⏱️ Timer"
                            tvTargetBadge.visibility = View.VISIBLE
                        }
                        else -> {
                            tvTargetBadge.visibility = View.GONE
                        }
                    }

                    val displayListName = when {
                        !suggestedTask.createNewList.isNullOrBlank() -> "✨ New List: ${suggestedTask.createNewList}"
                        !suggestedTask.listName.isNullOrBlank() -> "🏷️ ${suggestedTask.listName}"
                        else -> null
                    }
                    if (displayListName != null) {
                        tvListBadge.text = displayListName
                        tvListBadge.visibility = View.VISIBLE
                    } else {
                        tvListBadge.visibility = View.GONE
                    }

                    if (suggestedTask.safeTrackingType == "CHECKLIST" && !suggestedTask.checklistItems.isNullOrEmpty()) {
                        tvChecklist.text = suggestedTask.checklistItems.joinToString("\n") { "• $it" }
                        containerChecklist.visibility = View.VISIBLE
                    } else {
                        containerChecklist.visibility = View.GONE
                    }

                    if (suggestedTask.isAdded) {
                        btnAdd.text = "Added ✓"
                        btnAdd.isEnabled = false
                        btnAdd.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.default_text_color))
                        btnModify.visibility = View.GONE
                    } else {
                        btnAdd.text = "+ Add"
                        btnAdd.isEnabled = true
                        btnAdd.backgroundTintList = ColorStateList.valueOf(effectiveColor)
                        btnAdd.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                        btnModify.visibility = View.VISIBLE
                        btnModify.setTextColor(effectiveColor)
                        btnModify.strokeColor = ColorStateList.valueOf(effectiveColor)

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

                    binding.containerSuggestedTasks.addView(cardView)
                }
            } else {
                binding.containerSuggestedTasks.visibility = View.GONE
            }
        }
    }
}
