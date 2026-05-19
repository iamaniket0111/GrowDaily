package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.adapter.BarAdapter2
import com.anitech.growdaily.adapter.HistoryAdapter
import com.anitech.growdaily.data_class.WeekHabit
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.viewmodel.AnalysisViewModel
import com.anitech.growdaily.database.viewmodel.AnalysisViewModelFactory
import com.anitech.growdaily.databinding.FragmentAnalysisRepeatTaskBinding
import com.anitech.growdaily.enum_class.PeriodType
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AnalysisRepeatTaskFragment : Fragment() {

    private var _binding: FragmentAnalysisRepeatTaskBinding? = null
    private val binding get() = _binding!!

    private val args: AnalysisRepeatTaskFragmentArgs by navArgs()

    private lateinit var barAdapter: BarAdapter2
    private lateinit var historyAdapter: HistoryAdapter
    private var hasAutoScrolledHistory = false
    private var heatmapBindJob: Job? = null
    private var isHeatmapDeferredFirstBind = true
    private var accentColor: Int? = null

    private val viewModel: AnalysisViewModel by viewModels {
        AnalysisViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisRepeatTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupHistoryAdapter()
        setupBarAdapter()
        viewModel.setTaskId(args.taskId)
        observeAccentColor()
        setClickListeners()
        observeViewModel()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.analysis_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    R.id.menu_edit -> {
                        val currentTask = viewModel.overviewState.value?.task ?: return true

                        val bundle = Bundle().apply {
                            putParcelable("task", currentTask)
                        }

                        findNavController().navigate(
                            R.id.nav_add_task,
                            bundle
                        )

                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            accentColor = color
            // Trigger rebuild of UI elements that use accentColor if necessary
            // For now, let's update the existing state if it's already there
            viewModel.overviewState.value?.let { state ->
                updateOverviewColors(state, color)
            }
            if (viewModel.barState.value != null) {
                updateBarColors(color)
            }
            viewModel.heatmapState.value?.let { state ->
                updateHeatmapColors(state, color)
            }
        }
    }

    private fun updateOverviewColors(state: com.anitech.growdaily.data_class.AnalysisOverviewState, color: Int) {
        val taskColor = TaskColor.fromName(state.task.colorCode)?.toColorInt(requireContext())
            ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)
            
        binding.overview.txtCurrentStreakValue.setTextColor(color)
        binding.overview.txtBestStreakValue.setTextColor(color)
        binding.totalCompletion.txtMainPercentage.setTextColor(color)
        binding.totalCompletion.txtMainPercentageLabel.setTextColor(color)
        binding.totalCompletion.progressOverall.setProgressColor(color)

        bindHeader(state.task, state.seriesStartDate, state.seriesEndDate, color)
        bindTaskIcon(state.task, taskColor) // Fixed to task color
        
        historyAdapter.updateData(
            progressByDate = state.progressByDate,
            taskColor = color
        )
        binding.weekExpanded.btnPrevYear.setColorFilter(color)
        binding.weekExpanded.btnNextYear.setColorFilter(color)
    }

    private fun updateBarColors(color: Int) {
        barAdapter.setBarColor(color)
        binding.progressBar.btnNext.setColorFilter(color)
        binding.progressBar.btnPrevious.setColorFilter(color)
    }

    private fun updateHeatmapColors(state: com.anitech.growdaily.data_class.AnalysisHeatmapState, color: Int) {
        binding.yearHeapMap.btnNextYear.setColorFilter(color)
        binding.yearHeapMap.btnPrevYear.setColorFilter(color)
        
        val unavailableDates = buildUnavailableDatesForYear(
            seriesStartDate = state.seriesStartDate,
            scheduledDates = state.scheduledDates,
            year = state.heatmapYear
        )
        
        scheduleHeatmapBind(
            heatmapYear = state.heatmapYear,
            taskStart = state.seriesStartDate,
            progressByDate = state.progressByDate,
            unavailableDates = unavailableDates,
            color = color
        )
    }

    // --------------------------------------------------
    // ADAPTER SETUP
    // --------------------------------------------------

    private fun setupHistoryAdapter() {
        historyAdapter = HistoryAdapter(
            taskAddedDate = LocalDate.now(),
            progressByDate = emptyMap(),
            taskColor = ContextCompat.getColor(requireContext(), R.color.brand_blue),
            weekList = emptyList(),
            listener = null
        )
        binding.weekExpanded.weekExpandedRv.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = historyAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }
    }


    private fun setupBarAdapter() {

        barAdapter = BarAdapter2()

        binding.progressBar.barGraph2.recyclerViewBar.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = barAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }
    }

    // --------------------------------------------------
    // STATE OBSERVER
    // --------------------------------------------------

    private fun observeViewModel() {

        viewModel.taskNotFound.observe(viewLifecycleOwner) { isNotFound ->
            if (isNotFound) findNavController().popBackStack()
        }

        // ---- OVERVIEW: rebuilds only on task/completions change ----
        viewModel.overviewState.observe(viewLifecycleOwner) { state ->
            val task = state.task
            val taskColor = TaskColor.fromName(task.colorCode)?.toColorInt(requireContext())
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)
            val displayColor = accentColor ?: taskColor
            val taskStart = state.seriesStartDate
            val historyItems = buildHistoryItems(state.scheduledDates)

            bindHeader(task, taskStart, state.seriesEndDate, displayColor)
            bindTaskIcon(task, taskColor) // Fixed to task color as requested

            binding.overview.txtCurrentStreakValue.text = "${state.currentStreak}"
            binding.overview.txtCurrentStreakValue.setTextColor(displayColor)
            binding.overview.txtBestStreakValue.text = "${state.bestStreak}"
            binding.overview.txtBestStreakValue.setTextColor(displayColor)
            binding.overview.txtLastCompletedDate.text = state.lastCompletedDate?.let { date ->
                getString(R.string.analysis_period_month_format, date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), date.dayOfMonth) + ", ${date.year}"
            } ?: getString(R.string.none_capitalized)
            binding.overview.txtLastMissedDate.text = state.lastMissedDate?.let { date ->
                getString(R.string.analysis_period_month_format, date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), date.dayOfMonth) + ", ${date.year}"
            } ?: getString(R.string.none_capitalized)

            binding.totalCompletion.txtMainPercentage.text = "${state.completionPercent}"
            binding.totalCompletion.txtMainPercentage.setTextColor(displayColor)
            binding.totalCompletion.txtMainPercentageLabel.setTextColor(displayColor)
            
            binding.totalCompletion.txtTotalAchieved.text = "${state.completedCount} Completed"
            binding.totalCompletion.txtTotalAchieved.setTextColor(displayColor)
            
            binding.totalCompletion.txtTotalMissed.text = "${state.totalDays - state.completedCount} Incomplete"
            
            binding.totalCompletion.txtCompletionRatio.text = "${state.completedCount}/${state.totalDays}"

            binding.totalCompletion.progressOverall.setProgressColor(displayColor)
            binding.totalCompletion.progressOverall.setProgress(state.completionPercent)

            historyAdapter.replaceData(
                taskAddedDate = taskStart,
                progressByDate = state.progressByDate,
                taskColor = displayColor,
                weekList = historyItems
            )
            binding.weekExpanded.btnPrevYear.setColorFilter(displayColor)
            binding.weekExpanded.btnNextYear.setColorFilter(displayColor)
            if (!hasAutoScrolledHistory && historyAdapter.itemCount > 0) {
                binding.weekExpanded.weekExpandedRv.post {
                    binding.weekExpanded.weekExpandedRv.scrollToPosition(historyAdapter.itemCount - 1)
                }
                hasAutoScrolledHistory = true
            }
        }

        // ---- BAR GRAPH: rebuilds only on period/anchor change ----
        viewModel.barState.observe(viewLifecycleOwner) { state ->
            val color = accentColor ?: viewModel.overviewState.value?.task?.colorCode
                ?.let { TaskColor.fromName(it)?.toColorInt(requireContext()) }
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)

            barAdapter.setPeriod(state.period)
            barAdapter.setBarColor(color)
            barAdapter.submitData(dates = state.barDates, scores = state.barScores)

            val formattedPeriodTitle = when (state.period) {
                PeriodType.WEEK -> {
                    val start = state.anchorDate.with(java.time.DayOfWeek.MONDAY)
                    val end = start.plusDays(6)
                    getString(R.string.analysis_period_week_format, start.dayOfMonth, start.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), end.dayOfMonth, end.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()))
                }
                PeriodType.MONTH -> getString(R.string.analysis_period_month_format, state.anchorDate.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()), state.anchorDate.year)
                PeriodType.YEAR -> getString(R.string.analysis_period_year_format, state.anchorDate.year)
            }
            binding.progressBar.txtCurrentPeriod.text = formattedPeriodTitle
            updateTabUI(state.period)
            binding.progressBar.btnNext.isEnabled = state.isNextEnabled
            binding.progressBar.btnNext.alpha = if (state.isNextEnabled) 1f else 0.3f
            binding.progressBar.btnPrevious.isEnabled = state.isPrevEnabled
            binding.progressBar.btnPrevious.alpha = if (state.isPrevEnabled) 1f else 0.3f
            binding.progressBar.btnNext.setColorFilter(color)
            binding.progressBar.btnPrevious.setColorFilter(color)
        }

        // ---- HEATMAP: rebuilds only on heatmapYear change ----
        viewModel.heatmapState.observe(viewLifecycleOwner) { state ->
            val task = viewModel.overviewState.value?.task ?: return@observe
            val taskStart = state.seriesStartDate
            val color = accentColor ?: TaskColor.fromName(task.colorCode)?.toColorInt(requireContext())
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)
            val unavailableDates = buildUnavailableDatesForYear(
                seriesStartDate = state.seriesStartDate,
                scheduledDates = state.scheduledDates,
                year = state.heatmapYear
            )

            binding.yearHeapMap.txtYear.text = state.heatmapYear.toString()
            binding.yearHeapMap.btnNextYear.setColorFilter(color)
            binding.yearHeapMap.btnPrevYear.setColorFilter(color)
            binding.yearHeapMap.btnNextYear.isEnabled = state.isHeatmapNextEnabled
            binding.yearHeapMap.btnNextYear.alpha = if (state.isHeatmapNextEnabled) 1f else 0.3f
            binding.yearHeapMap.btnPrevYear.isEnabled = state.isHeatmapPrevEnabled
            binding.yearHeapMap.btnPrevYear.alpha = if (state.isHeatmapPrevEnabled) 1f else 0.3f

            scheduleHeatmapBind(
                heatmapYear = state.heatmapYear,
                taskStart = taskStart,
                progressByDate = state.progressByDate,
                unavailableDates = unavailableDates,
                color = color
            )
        }
    }

    private fun scheduleHeatmapBind(
        heatmapYear: Int,
        taskStart: LocalDate,
        progressByDate: Map<LocalDate, Int>,
        unavailableDates: Set<LocalDate>,
        color: Int
    ) {
        heatmapBindJob?.cancel()
        heatmapBindJob = viewLifecycleOwner.lifecycleScope.launch {
            // Let the screen become interactive before binding the heaviest custom view.
            delay(if (isHeatmapDeferredFirstBind) 320 else 120)
            if (_binding == null) return@launch
            binding.yearHeapMap.heatmapLayout.post {
                if (_binding == null) return@post
                binding.yearHeapMap.heatmapLayout.setYear(heatmapYear)
                binding.yearHeapMap.heatmapLayout.bindHeatmap(
                    taskAddedDate = taskStart,
                    progressByDate = progressByDate,
                    unavailableDates = unavailableDates,
                    activeColor = color
                )
                isHeatmapDeferredFirstBind = false
            }
        }
    }

    // --------------------------------------------------
    // CLICK LISTENERS
    // --------------------------------------------------

    private fun setClickListeners() {

        binding.progressBar.tabWeek.setOnClickListener {
            viewModel.setAnalysisPeriod(PeriodType.WEEK)
        }

        binding.progressBar.tabMonth.setOnClickListener {
            viewModel.setAnalysisPeriod(PeriodType.MONTH)
        }

        binding.progressBar.tabYear.setOnClickListener {
            viewModel.setAnalysisPeriod(PeriodType.YEAR)
        }

        binding.progressBar.btnPrevious.setOnClickListener {
            viewModel.moveAnalysisAnchor(-1)
        }

        binding.progressBar.btnNext.setOnClickListener {
            viewModel.moveAnalysisAnchor(+1)
        }


        binding.yearHeapMap.btnPrevYear.setOnClickListener {
            viewModel.moveHeatmapYear(-1)
        }

        binding.yearHeapMap.btnNextYear.setOnClickListener {
            viewModel.moveHeatmapYear(+1)
        }

        binding.weekExpanded.scrollToStartDay.setOnClickListener {
            binding.weekExpanded.weekExpandedRv.smoothScrollToPosition(0)
        }

        binding.weekExpanded.scrollToCurrentDay.setOnClickListener {

            binding.weekExpanded.weekExpandedRv.smoothScrollToPosition(
                historyAdapter.itemCount - 1
            )
        }

    }

    private fun updateTabUI(period: PeriodType) {
        val color = accentColor ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)
        val alphaColor = ColorUtils.setAlphaComponent(color, 40)

        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.task_text_secondary)

        fun styleTab(view: TextView, isActive: Boolean) {
            view.setTextColor(if (isActive) color else inactiveTextColor)

            if (isActive) {
                view.setBackgroundResource(R.drawable.analysis_segment_active_bg)
                view.backgroundTintList = ColorStateList.valueOf(alphaColor)
            } else {
                view.background = null
            }
        }

        styleTab(binding.progressBar.tabWeek, period == PeriodType.WEEK)
        styleTab(binding.progressBar.tabMonth, period == PeriodType.MONTH)
        styleTab(binding.progressBar.tabYear, period == PeriodType.YEAR)
    }

    // --------------------------------------------------
    // HEADER + ICON
    // --------------------------------------------------

    private fun bindHeader(task: TaskEntity, taskStart: LocalDate, seriesEndDate: LocalDate, color: Int) {
        val header = binding.header.body
        header.txtTaskTitle.text = task.title

        if (task.note.isNullOrBlank()) {
            header.txtTaskNote.visibility = View.GONE
        } else {
            header.txtTaskNote.visibility = View.VISIBLE
            header.txtTaskNote.text = task.note
        }

        header.txtType.text = header.root.context.getString(task.taskType.labelRes)
        header.imgType.setColorFilter(color)

        header.txtReminder.text =
            if (task.reminderEnabled) getString(R.string.reminder_on_format, task.reminderTime) else getString(R.string.reminder_off)
        header.imgReminder.setColorFilter(color)

        header.txtSchedule.text =
            if (task.isScheduled) getString(R.string.scheduled_on_format, task.scheduledTime) else getString(R.string.not_scheduled)
        header.imgSchedule.setColorFilter(color)

        header.txtWeight.text =
            getString(R.string.priority_format, task.weight.name.lowercase().replaceFirstChar { it.uppercase() })
        header.imgWeight.setColorFilter(color)

        val today = LocalDate.now()
        val endDate = task.taskRemovedDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val hasPastEndDate = endDate != null && endDate.isBefore(today)

        header.txtStartedSince.text =
            getString(R.string.started_since_format, taskStart.dayOfMonth, taskStart.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), taskStart.year)
        header.txtStartedSince.setTextColor(color)
        header.imgStartedSince.setColorFilter(color)

        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val alphaFactor = if (isNight) 0.05f else 0.07f
        header.startedSinceContainer.backgroundTintList = ColorStateList.valueOf(color.adjustAlpha(alphaFactor))

        val daysRunning =
            ChronoUnit.DAYS.between(taskStart, seriesEndDate).toInt() + 1

        header.txtRunningFor.text = getString(R.string.running_for_days_format, daysRunning)
        header.txtRunningFor.setTextColor(color.adjustAlpha(0.7f))
        if (endDate != null) {
            header.endDateRow.visibility = View.VISIBLE
            header.imgEndDate.setColorFilter(color)
            header.txtEndDate.text = if (hasPastEndDate) {
                getString(R.string.ended_on_format, endDate.toDisplayFormat())
            } else {
                getString(R.string.ends_on_format, endDate.toDisplayFormat())
            }
        } else {
            header.endDateRow.visibility = View.GONE
        }

    }

    private fun bindTaskIcon(task: TaskEntity, color: Int) {
        val iconLayout = binding.header.iconLayout
        val iconRes = TaskIcon.fromName(task.iconResId).resId
        iconLayout.imgTaskIcon.setImageResource(iconRes)

        iconLayout.viewIconBg.setSolidBackgroundColorCompat(color)
    }

    private fun LocalDate.toDisplayFormat(): String {
        return context?.getString(R.string.analysis_period_month_format, month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), dayOfMonth) + ", $year"
    }

    private fun buildUnavailableDatesForYear(
        seriesStartDate: LocalDate,
        scheduledDates: Set<LocalDate>,
        year: Int
    ): Set<LocalDate> {
        val today = LocalDate.now()
        val start = maxOf(seriesStartDate, LocalDate.of(year, 1, 1))
        val end = minOf(today, LocalDate.of(year, 12, 31))
        if (end.isBefore(start)) return emptySet()

        val unavailable = mutableSetOf<LocalDate>()
        var date = start
        while (!date.isAfter(end)) {
            if (!scheduledDates.contains(date)) {
                unavailable.add(date)
            }
            date = date.plusDays(1)
        }
        return unavailable
    }

    private fun buildHistoryItems(scheduledDates: Set<LocalDate>): List<WeekHabit> {
        return scheduledDates
            .sorted()
            .map { date ->
                WeekHabit(
                    date = date,
                    dayLetter = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
                )
            }
    }

    override fun onDestroyView() {
        heatmapBindJob?.cancel()
        isHeatmapDeferredFirstBind = true
        super.onDestroyView()
        _binding = null
    }
}
