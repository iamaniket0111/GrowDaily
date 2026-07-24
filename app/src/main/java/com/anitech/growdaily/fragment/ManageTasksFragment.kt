package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ManageRepeatTaskAdapter
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.ManagedRepeatTaskUi
import com.anitech.growdaily.database.viewmodel.ManageRepeatTasksViewModel
import com.anitech.growdaily.database.viewmodel.ManageRepeatTasksViewModelFactory
import com.anitech.growdaily.databinding.FragmentManageTasksBinding
import com.anitech.growdaily.dialog.DeleteTaskDialog
import com.anitech.growdaily.dialog.ManageTaskCalendarDialog
import com.anitech.growdaily.dialog.PauseOptionsDialog
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.enum_class.ManageTaskSection
import com.anitech.growdaily.enum_class.TaskType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.widget.Toast

class ManageRepeatTasksFragment : Fragment() {

    private var _binding: FragmentManageTasksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ManageRepeatTasksViewModel by viewModels {
        ManageRepeatTasksViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    private var accentColor: Int = Color.BLUE
    private var topSection: TopSection = TopSection.DAY
    private var daySection: ManageTaskSection = ManageTaskSection.DAY_ALL
    private var repeatSection: ManageTaskSection = ManageTaskSection.REPEAT_ALL
    private var manageRepeatTaskAdapter: ManageRepeatTaskAdapter? = null

    private enum class TopSection {
        DAY,
        REPEAT
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeAccentColor()
        observeData()
        setupClicks()
        render()
    }

    private fun currentSection(): ManageTaskSection = when (topSection) {
        TopSection.DAY -> daySection
        TopSection.REPEAT -> repeatSection
    }

    private fun setupRecyclerView() {
        val adapterInstance = ManageRepeatTaskAdapter()
        manageRepeatTaskAdapter = adapterInstance
        adapterInstance.setOnItemClickListener { item ->
            when (item.section) {
                ManageTaskSection.DAY_ALL -> {
                    findNavController().navigate(
                        R.id.nav_add_task,
                        bundleOf("task" to item.task)
                    )
                }
                ManageTaskSection.DAY_ACTIVE,
                ManageTaskSection.DAY_MISSED,
                ManageTaskSection.REPEAT_ALL,
                ManageTaskSection.REPEAT_ACTIVE,
                ManageTaskSection.PAUSED,
                ManageTaskSection.ENDED -> {
                    findNavController().navigate(
                        R.id.taskDetailFragment,
                        bundleOf(
                            "taskId" to item.task.id,
                            "task" to item.task
                        )
                    )
                }
            }
        }
        adapterInstance.setOnActionClickListener { item, action ->
            when (action) {
                ManageRepeatTaskAdapter.Action.RESUME -> showResumeConfirmDialog(item)
                ManageRepeatTaskAdapter.Action.RESTART -> showRestartConfirmDialog(item)
            }
        }
        adapterInstance.setOnMenuActionListener { item, action ->
            when (action) {
                ManageRepeatTaskAdapter.MenuAction.ADD_REMOVE -> openManageCalendar(item)
                ManageRepeatTaskAdapter.MenuAction.DELETE -> confirmDelete(item)
                ManageRepeatTaskAdapter.MenuAction.PAUSE -> showPauseOptionsDialog(item)
                ManageRepeatTaskAdapter.MenuAction.RESUME -> showResumeConfirmDialog(item)
                ManageRepeatTaskAdapter.MenuAction.RESTART -> showRestartConfirmDialog(item)
            }
        }
        binding.recyclerManageRepeatTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adapterInstance
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun openManageCalendar(item: ManagedRepeatTaskUi) {
        val original = item.originalDate ?: item.task.taskAddedDate
        val active = item.activeDates.ifEmpty { setOf(original) }
        val dialog = ManageTaskCalendarDialog.newInstance(
            originalDate = original,
            activeDates = active,
            accentColor = accentColor
        ).apply {
            setCallback(object : ManageTaskCalendarDialog.Callback {
                override fun onToggle(date: String, shouldAdd: Boolean) {
                    if (date == original) return
                    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                    val formattedDate = parsedDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())) ?: date

                    if (shouldAdd) {
                        when (item.task.taskType) {
                            TaskType.UNTIL_COMPLETE -> viewModel.addUntilCompleteForDate(item, date)
                            else -> viewModel.addDayTaskForDate(item, date)
                        }
                        Toast.makeText(
                            this@ManageRepeatTasksFragment.requireContext(),
                            this@ManageRepeatTasksFragment.getString(R.string.toast_task_added_date, formattedDate),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val warningColor = ContextCompat.getColor(this@ManageRepeatTasksFragment.requireContext(), R.color.category_red)
                        val bubbleColor = 0x50EF5350.toInt()
                        TaskActionDialog(
                            context = this@ManageRepeatTasksFragment.requireContext(),
                            title = this@ManageRepeatTasksFragment.getString(R.string.dialog_remove_date_title),
                            message = this@ManageRepeatTasksFragment.getString(R.string.dialog_remove_date_message, formattedDate),
                            primaryLabel = this@ManageRepeatTasksFragment.getString(R.string.remove_button),
                            iconRes = R.drawable.ic_warning,
                            accentColor = warningColor,
                            iconBubbleColor = bubbleColor,
                            onPrimaryAction = {
                                when (item.task.taskType) {
                                    TaskType.UNTIL_COMPLETE -> viewModel.removeUntilCompleteFromDate(item, date)
                                    else -> viewModel.removeDayTaskFromDate(item, date)
                                }
                                Toast.makeText(
                                    this@ManageRepeatTasksFragment.requireContext(),
                                    this@ManageRepeatTasksFragment.getString(R.string.toast_task_removed_date, formattedDate),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ).show()
                    }
                }
            })
        }
        if (isAdded && !parentFragmentManager.isStateSaved) {
            dialog.show(parentFragmentManager, "MANAGE_TASK_CALENDAR")
        }
    }

    private fun confirmDelete(item: ManagedRepeatTaskUi) {
        DeleteTaskDialog(requireContext(), item.task) {
            viewModel.deleteTask(item)
        }.show()
    }

    private fun showPauseOptionsDialog(item: ManagedRepeatTaskUi) {
        val today = LocalDate.parse(CommonMethods.getTodayDate())
        val bubbleColor = ColorUtils.setAlphaComponent(accentColor, (255 * 0.20f).toInt())
        PauseOptionsDialog(
            context = requireContext(),
            title = getString(R.string.pause_daily_task_title),
            message = getString(R.string.pause_daily_task_message),
            iconRes = R.drawable.ic_pause,
            accentColor = accentColor,
            iconBubbleColor = bubbleColor,
            onPauseFromTomorrow = {
                viewModel.pauseRepeatTask(item, today.toString())
            },
            onPauseFromToday = {
                viewModel.pauseRepeatTask(item, today.minusDays(1).toString())
            }
        ).show()
    }

    private fun showResumeConfirmDialog(item: ManagedRepeatTaskUi) {
        val bubbleColor = ColorUtils.setAlphaComponent(accentColor, (255 * 0.20f).toInt())
        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.resume_daily_task_title),
            message = getString(R.string.resume_daily_task_message),
            primaryLabel = getString(R.string.resume_action),
            iconRes = android.R.drawable.ic_media_play,
            accentColor = accentColor,
            iconBubbleColor = bubbleColor,
            onPrimaryAction = {
                viewModel.resumeTask(item)
            }
        ).show()
    }

    private fun showRestartConfirmDialog(item: ManagedRepeatTaskUi) {
        val bubbleColor = ColorUtils.setAlphaComponent(accentColor, (255 * 0.20f).toInt())
        TaskActionDialog(
            context = requireContext(),
            title = "Restart Daily Task?",
            message = "This will create a new segment of this task active starting today.",
            primaryLabel = "Restart",
            iconRes = android.R.drawable.ic_media_play,
            accentColor = accentColor,
            iconBubbleColor = bubbleColor,
            onPrimaryAction = {
                viewModel.restartTask(item)
            }
        ).show()
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            accentColor = color
            manageRepeatTaskAdapter?.setAccentColor(color)
            render()
        }
    }

    private fun observeData() {
        viewModel.allDayTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.DAY && daySection == ManageTaskSection.DAY_ALL) render()
        }
        viewModel.dayActiveTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.DAY && daySection == ManageTaskSection.DAY_ACTIVE) render()
        }
        viewModel.dayMissedTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.DAY && daySection == ManageTaskSection.DAY_MISSED) render()
        }
        viewModel.allRepeatTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.REPEAT && repeatSection == ManageTaskSection.REPEAT_ALL) render()
        }
        viewModel.activeRepeatTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.REPEAT && repeatSection == ManageTaskSection.REPEAT_ACTIVE) render()
        }
        viewModel.pausedTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.REPEAT && repeatSection == ManageTaskSection.PAUSED) render()
        }
        viewModel.endedTasks.observe(viewLifecycleOwner) {
            if (topSection == TopSection.REPEAT && repeatSection == ManageTaskSection.ENDED) render()
        }
        viewModel.busySeriesIds.observe(viewLifecycleOwner) { busySeriesIds ->
            manageRepeatTaskAdapter?.setBusySeriesIds(busySeriesIds)
        }
    }

    private fun setupClicks() {
        binding.btnDay.setOnClickListener {
            topSection = TopSection.DAY
            render()
        }
        binding.btnRepeat.setOnClickListener {
            topSection = TopSection.REPEAT
            render()
        }
        binding.chipAllDay.setOnClickListener {
            daySection = ManageTaskSection.DAY_ALL
            render()
        }
        binding.chipDayActive.setOnClickListener {
            daySection = ManageTaskSection.DAY_ACTIVE
            render()
        }
        binding.chipDayMissed.setOnClickListener {
            daySection = ManageTaskSection.DAY_MISSED
            render()
        }
        binding.chipAllRepeat.setOnClickListener {
            repeatSection = ManageTaskSection.REPEAT_ALL
            render()
        }
        binding.chipActive.setOnClickListener {
            repeatSection = ManageTaskSection.REPEAT_ACTIVE
            render()
        }
        binding.chipPaused.setOnClickListener {
            repeatSection = ManageTaskSection.PAUSED
            render()
        }
        binding.chipEnded.setOnClickListener {
            repeatSection = ManageTaskSection.ENDED
            render()
        }
    }

    private fun render() {
        val isDay = topSection == TopSection.DAY
        binding.chipContainerDayScroll.visibility = if (isDay) View.VISIBLE else View.GONE
        binding.chipContainerRepeatScroll.visibility = if (isDay) View.GONE else View.VISIBLE

        styleTopButton(binding.btnDay, isDay)
        styleTopButton(binding.btnRepeat, !isDay)

        if (isDay) {
            styleChip(binding.chipAllDay, daySection == ManageTaskSection.DAY_ALL)
            styleChip(binding.chipDayActive, daySection == ManageTaskSection.DAY_ACTIVE)
            styleChip(binding.chipDayMissed, daySection == ManageTaskSection.DAY_MISSED)
        } else {
            styleChip(binding.chipAllRepeat, repeatSection == ManageTaskSection.REPEAT_ALL)
            styleChip(binding.chipActive, repeatSection == ManageTaskSection.REPEAT_ACTIVE)
            styleChip(binding.chipPaused, repeatSection == ManageTaskSection.PAUSED)
            styleChip(binding.chipEnded, repeatSection == ManageTaskSection.ENDED)
        }

        val section = currentSection()
        val items = when (section) {
            ManageTaskSection.DAY_ALL -> viewModel.allDayTasks.value.orEmpty()
            ManageTaskSection.DAY_ACTIVE -> viewModel.dayActiveTasks.value.orEmpty()
            ManageTaskSection.DAY_MISSED -> viewModel.dayMissedTasks.value.orEmpty()
            ManageTaskSection.REPEAT_ALL -> viewModel.allRepeatTasks.value.orEmpty()
            ManageTaskSection.REPEAT_ACTIVE -> viewModel.activeRepeatTasks.value.orEmpty()
            ManageTaskSection.PAUSED -> viewModel.pausedTasks.value.orEmpty()
            ManageTaskSection.ENDED -> viewModel.endedTasks.value.orEmpty()
        }
        manageRepeatTaskAdapter?.submitList(items)

        val (emptyTitle, emptySubtitle, emptyIcon) = when (section) {
            ManageTaskSection.DAY_ALL -> Triple(
                R.string.day_all_tasks_empty_title,
                R.string.day_all_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
            ManageTaskSection.DAY_ACTIVE -> Triple(
                R.string.day_active_tasks_empty_title,
                R.string.day_active_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
            ManageTaskSection.DAY_MISSED -> Triple(
                R.string.day_missed_tasks_empty_title,
                R.string.day_missed_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
            ManageTaskSection.REPEAT_ALL -> Triple(
                R.string.repeat_all_tasks_empty_title,
                R.string.repeat_all_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
            ManageTaskSection.REPEAT_ACTIVE -> Triple(
                R.string.active_repeat_tasks_empty_title,
                R.string.active_repeat_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
            ManageTaskSection.PAUSED -> Triple(
                R.string.paused_repeat_tasks_empty_title,
                R.string.paused_repeat_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
            ManageTaskSection.ENDED -> Triple(
                R.string.ended_repeat_tasks_empty_title,
                R.string.ended_repeat_tasks_empty_subtitle,
                R.drawable.ic_to_do_list
            )
        }
        binding.txtEmptyTitle.setText(emptyTitle)
        binding.txtEmptySubtitle.setText(emptySubtitle)
        binding.ivEmptyState.setImageResource(emptyIcon)

        binding.recyclerManageRepeatTasks.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyStateContainer.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        updateTopIndicator()

    }



    private fun updateTopIndicator() {
        val currentBinding = _binding ?: return
        currentBinding.topToggleContainer.post {
            val safetyBinding = _binding ?: return@post
            try {
                val target = if (topSection == TopSection.DAY) safetyBinding.btnDay else safetyBinding.btnRepeat
                val indicator = safetyBinding.selectionIndicator
                val density = resources.displayMetrics.density
                val sideMarginPx = (6 * density).toInt()

                val targetWidth = (target.width - sideMarginPx * 2).coerceAtLeast(0)
                val lp = indicator.layoutParams
                lp.width = targetWidth
                indicator.layoutParams = lp

                val targetX = target.left + sideMarginPx.toFloat()
                indicator.animate().translationX(targetX).setDuration(180).start()
                indicator.backgroundTintList = ColorStateList.valueOf(accentColor)
            } catch (_: Exception) {
            }
        }
    }

    private fun styleChip(view: View, isSelected: Boolean) {
        val textView = view as android.widget.TextView
        if (isSelected) {
            textView.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, 38))
            textView.setTextColor(accentColor)
        } else {
            textView.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.task_chip_surface))
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.task_text_secondary))
        }
    }

    private fun styleTopButton(button: com.google.android.material.button.MaterialButton, isSelected: Boolean) {
        val density = resources.displayMetrics.density
        val strokePx = (1 * density).toInt().coerceAtLeast(1)
        if (isSelected) {
            button.backgroundTintList = ColorStateList.valueOf(accentColor)
            button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            button.strokeWidth = 0
            button.elevation = 4f * density
        } else {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.task_text_secondary))
            button.strokeWidth = strokePx
            button.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, 60))
            button.elevation = 0f
        }
        button.isAllCaps = false
    }

    override fun onDestroyView() {
        if (_binding != null) {
            binding.recyclerManageRepeatTasks.adapter = null
        }
        super.onDestroyView()
        _binding = null
        manageRepeatTaskAdapter = null
    }
}
