package com.anitech.growdaily.adapter

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ManagedRepeatTaskUi
import com.anitech.growdaily.databinding.ItemManageRepeatTaskBinding
import com.anitech.growdaily.enum_class.ManageTaskSection
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.setSolidBackgroundColorCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ManageRepeatTaskAdapter :
    ListAdapter<ManagedRepeatTaskUi, ManageRepeatTaskAdapter.ViewHolder>(DiffCallback) {

    fun interface OnItemClickListener {
        fun onItemClick(item: ManagedRepeatTaskUi)
    }

    fun interface OnActionClickListener {
        fun onActionClick(item: ManagedRepeatTaskUi, action: Action)
    }

    fun interface OnMenuActionListener {
        fun onMenuAction(item: ManagedRepeatTaskUi, action: MenuAction)
    }

    enum class Action {
        RESUME,
        RESTART
    }

    enum class MenuAction {
        ADD_REMOVE,
        DELETE,
        PAUSE,
        RESUME,
        RESTART
    }

    private var accentColor: Int? = null
    private var busySeriesIds: Set<String> = emptySet()
    private var onItemClickListener: OnItemClickListener? = null
    private var onActionClickListener: OnActionClickListener? = null
    private var onMenuActionListener: OnMenuActionListener? = null

    fun setAccentColor(color: Int) {
        accentColor = color
        notifyItemRangeChanged(0, itemCount)
    }

    fun setBusySeriesIds(seriesIds: Set<String>) {
        busySeriesIds = seriesIds
        notifyItemRangeChanged(0, itemCount)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    fun setOnActionClickListener(listener: OnActionClickListener) {
        onActionClickListener = listener
    }

    fun setOnMenuActionListener(listener: OnMenuActionListener) {
        onMenuActionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemManageRepeatTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            item = getItem(position),
            accentColor = accentColor,
            isBusy = busySeriesIds.contains(getItem(position).actionKey),
            onItemClickListener = onItemClickListener,
            onActionClickListener = onActionClickListener,
            onMenuActionListener = onMenuActionListener
        )
    }

    class ViewHolder(
        private val binding: ItemManageRepeatTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ManagedRepeatTaskUi,
            accentColor: Int?,
            isBusy: Boolean,
            onItemClickListener: OnItemClickListener?,
            onActionClickListener: OnActionClickListener?,
            onMenuActionListener: OnMenuActionListener?
        ) {
            val context = binding.root.context
            val task = item.task
            val taskColor = TaskColor.fromName(task.colorCode)?.toColorInt(context)
                ?: ContextCompat.getColor(context, R.color.category_blue)
            val taskIcon = TaskIcon.fromName(task.iconResId)

            binding.imageProfile.setSolidBackgroundColorCompat(taskColor)
            binding.imageProfile.setImageResource(taskIcon.resId)
            binding.imageProfile.setColorFilter(ContextCompat.getColor(context, R.color.white))
            binding.txtTaskType.text = context.getString(task.taskType.labelRes)
            binding.txtTaskType.setTextColor(taskColor)
            binding.txtTitle.text = task.title

            val label = when (item.section) {
                ManageTaskSection.PAUSED -> R.string.paused_on_format
                ManageTaskSection.ENDED -> R.string.ended_on_format
                ManageTaskSection.DAY_ALL,
                ManageTaskSection.DAY_ACTIVE,
                ManageTaskSection.DAY_MISSED -> R.string.added_on_format
                ManageTaskSection.REPEAT_ACTIVE -> R.string.added_on_format
                ManageTaskSection.REPEAT_ALL -> {
                    when (task.inactiveReason) {
                        com.anitech.growdaily.enum_class.TaskInactiveReason.PAUSED -> R.string.paused_on_format
                        com.anitech.growdaily.enum_class.TaskInactiveReason.ENDED -> R.string.ended_on_format
                        else -> R.string.added_on_format
                    }
                }
            }
            val dateToFormat = if (task.inactiveReason != null && task.taskRemovedDate != null) {
                runCatching { LocalDate.parse(task.taskRemovedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrNull()?.plusDays(1) ?: item.metaDate
            } else {
                item.metaDate
            }
            val formattedDate = dateToFormat.format(
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
            )
            binding.txtMeta.text = context.getString(label, formattedDate)
            binding.txtMeta.setTextColor(taskColor)

            val action = when (item.section) {
                ManageTaskSection.PAUSED -> Action.RESUME
                ManageTaskSection.ENDED -> Action.RESTART
                else -> null
            }
            val resolvedAccent = accentColor ?: taskColor
            if (action != null) {
                binding.btnAction.text = context.getString(
                    when (action) {
                        Action.RESUME -> R.string.resume_action
                        Action.RESTART -> R.string.restart_from_today_action
                    }
                )
                binding.btnAction.background = buildActionBackground(context, resolvedAccent)
                binding.btnAction.setTextColor(resolvedAccent)
                binding.btnAction.isEnabled = !isBusy
                binding.btnAction.alpha = if (isBusy) 0.45f else 1f
                binding.btnAction.visibility = if (item.showAction) View.VISIBLE else View.GONE
                binding.btnAction.setOnClickListener {
                    onActionClickListener?.onActionClick(item, action)
                }
            } else {
                binding.btnAction.visibility = View.GONE
            }

            if (item.showMenu) {
                binding.btnMenu.visibility = View.VISIBLE
                binding.btnMenu.isEnabled = !isBusy
                binding.btnMenu.alpha = if (isBusy) 0.45f else 1f
                binding.btnMenu.setOnClickListener { anchor ->
                    val menuRes = when (item.section) {
                        ManageTaskSection.DAY_ALL,
                        ManageTaskSection.DAY_ACTIVE,
                        ManageTaskSection.DAY_MISSED -> R.menu.menu_manage_day_task
                        ManageTaskSection.REPEAT_ALL,
                        ManageTaskSection.REPEAT_ACTIVE,
                        ManageTaskSection.PAUSED,
                        ManageTaskSection.ENDED -> R.menu.menu_manage_repeat_task
                    }
                    PopupMenu(context, anchor).apply {
                        menuInflater.inflate(menuRes, menu)
                        if (menuRes == R.menu.menu_manage_repeat_task) {
                            when (task.inactiveReason) {
                                com.anitech.growdaily.enum_class.TaskInactiveReason.PAUSED -> {
                                    menu.findItem(R.id.action_pause)?.isVisible = false
                                    menu.findItem(R.id.action_resume)?.isVisible = true
                                    menu.findItem(R.id.action_restart)?.isVisible = false
                                }
                                com.anitech.growdaily.enum_class.TaskInactiveReason.ENDED -> {
                                    menu.findItem(R.id.action_pause)?.isVisible = false
                                    menu.findItem(R.id.action_resume)?.isVisible = false
                                    menu.findItem(R.id.action_restart)?.isVisible = true
                                }
                                else -> {
                                    menu.findItem(R.id.action_pause)?.isVisible = true
                                    menu.findItem(R.id.action_resume)?.isVisible = false
                                    menu.findItem(R.id.action_restart)?.isVisible = false
                                }
                            }
                        }
                        setOnMenuItemClickListener { menuItem ->
                            val menuAction = when (menuItem.itemId) {
                                R.id.action_add_remove -> MenuAction.ADD_REMOVE
                                R.id.action_delete -> MenuAction.DELETE
                                R.id.action_pause -> MenuAction.PAUSE
                                R.id.action_resume -> MenuAction.RESUME
                                R.id.action_restart -> MenuAction.RESTART
                                else -> return@setOnMenuItemClickListener false
                            }
                            onMenuActionListener?.onMenuAction(item, menuAction)
                            true
                        }
                        show()
                    }
                }
            } else {
                binding.btnMenu.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClickListener?.onItemClick(item)
            }
        }

        private fun buildActionBackground(
            context: android.content.Context,
            accentColor: Int
        ): GradientDrawable {
            val isDarkMode =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val fillAlpha = if (isDarkMode) 42 else 22
            val strokeAlpha = if (isDarkMode) 96 else 82
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.resources.displayMetrics.density * 14f
                setColor(ColorUtils.setAlphaComponent(accentColor, fillAlpha))
                setStroke(
                    (context.resources.displayMetrics.density * 1).toInt().coerceAtLeast(1),
                    ColorUtils.setAlphaComponent(accentColor, strokeAlpha)
                )
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ManagedRepeatTaskUi>() {
        override fun areItemsTheSame(oldItem: ManagedRepeatTaskUi, newItem: ManagedRepeatTaskUi): Boolean {
            return oldItem.section == newItem.section && oldItem.actionKey == newItem.actionKey
        }

        override fun areContentsTheSame(oldItem: ManagedRepeatTaskUi, newItem: ManagedRepeatTaskUi): Boolean {
            return oldItem == newItem
        }
    }
}
