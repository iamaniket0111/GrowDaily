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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
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

    private val viewModel: AnalysisViewModel by viewModels {
        AnalysisViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
        setupHistoryAdapter()
        setupBarAdapter()
        viewModel.setTaskId(args.taskId)
        setClickListeners()
        observeViewModel()
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

        barAdapter = BarAdapter2(null)

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
            val color = TaskColor.fromName(task.colorCode)?.toColorInt(requireContext())
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)
            val taskStart = state.seriesStartDate
            val historyItems = buildHistoryItems(state.scheduledDates)

            bindHeader(task, taskStart)
            bindTaskIcon(task, color)

            binding.overview.txtCurrentStreakValue.text = "${state.currentStreak}"
            binding.overview.txtCurrentStreakValue.setTextColor(color)
            binding.overview.txtBestStreakValue.text = "${state.bestStreak}"
            binding.overview.txtBestStreakValue.setTextColor(color)
            binding.overview.txtLastCompletedDate.text = state.lastCompletedDate?.toDisplayFormat() ?: "None"
            binding.overview.txtLastMissedDate.text = state.lastMissedDate?.toDisplayFormat() ?: "None"

            binding.totalCompletion.txtMainPercentage.text = "${state.completionPercent}"
            binding.totalCompletion.txtMainPercentage.setTextColor(color)
            binding.totalCompletion.txtMainPercentageLabel.setTextColor(color)
            binding.totalCompletion.txtTotalAchieved.text = "${state.completedCount}"
            binding.totalCompletion.txtTotalMissed.text = "${state.totalDays - state.completedCount}"
            binding.totalCompletion.txtTotalDays.text = "${state.totalDays}"
            binding.totalCompletion.progressOverall.setProgressColor(color)
            binding.totalCompletion.progressOverall.setProgress(state.completionPercent)

            historyAdapter.replaceData(
                taskAddedDate = taskStart,
                progressByDate = state.progressByDate,
                taskColor = color,
                weekList = historyItems
            )
            binding.weekExpanded.btnPrevYear.setColorFilter(color)
            binding.weekExpanded.btnNextYear.setColorFilter(color)
            if (!hasAutoScrolledHistory && historyAdapter.itemCount > 0) {
                binding.weekExpanded.weekExpandedRv.post {
                    binding.weekExpanded.weekExpandedRv.scrollToPosition(historyAdapter.itemCount - 1)
                }
                hasAutoScrolledHistory = true
            }
        }

        // ---- BAR GRAPH: rebuilds only on period/anchor change ----
        viewModel.barState.observe(viewLifecycleOwner) { state ->
            val color = viewModel.overviewState.value?.task?.colorCode
                ?.let { TaskColor.fromName(it)?.toColorInt(requireContext()) }
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)

            barAdapter.setPeriod(state.period)
            barAdapter.setBarColor(color)
            barAdapter.submitData(dates = state.barDates, scores = state.barScores)
            binding.progressBar.txtCurrentPeriod.text = state.periodTitle
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
            val color = TaskColor.fromName(task.colorCode)?.toColorInt(requireContext())
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

        val activeTextColor = ContextCompat.getColor(requireContext(), R.color.task_text_primary)
        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.task_text_secondary)

        fun styleTab(view: TextView, isActive: Boolean) {
            view.setTextColor(if (isActive) activeTextColor else inactiveTextColor)

            if (isActive) {
                view.setBackgroundResource(R.drawable.analysis_segment_active_bg)
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

    private fun bindHeader(task: TaskEntity, taskStart: LocalDate) {
        val header = binding.header
        header.txtTaskTitle.text = task.title

        if (task.note.isNullOrBlank()) {
            header.txtTaskNote.visibility = View.GONE
        } else {
            header.txtTaskNote.text = task.note
        }

        header.txtReminder.text =
            if (task.reminderEnabled) "Reminder at ${task.reminderTime}" else "Reminder off"

        header.txtSchedule.text =
            if (task.isScheduled) "Scheduled at ${task.scheduledTime}" else "Not scheduled"

        header.txtWeight.text =
            "Priority: ${task.weight.name.lowercase().replaceFirstChar { it.uppercase() }}"

        val today = LocalDate.now()

        header.txtStartedSince.text =
            "Started since ${taskStart.dayOfMonth} ${taskStart.month.name.take(3)} ${taskStart.year}"

        val daysRunning =
            ChronoUnit.DAYS.between(taskStart, today).toInt() + 1

        header.txtRunningFor.text = "• $daysRunning days"

    }

    private fun bindTaskIcon(task: TaskEntity, color: Int) = with(binding.iconLayout) {
        val iconRes =
            TaskIcon.fromName(task.iconResId)?.resId ?: R.drawable.ic_trophy
        imgTaskIcon.setImageResource(iconRes)

        viewIconBg.setSolidBackgroundColorCompat(color)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.analysis_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateBackWithLoading()
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

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateBackWithLoading(message: String = "Going back...") {
        binding.tvNavigationLoading.text = message
        binding.navigationLoadingOverlay.visibility = View.VISIBLE
        binding.root.post {
            if (_binding == null || !isAdded) return@post
            findNavController().popBackStack()
        }
    }

    private fun LocalDate.toDisplayFormat(): String {
        val month = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$month $dayOfMonth, $year"
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
                    dayLetter = date.dayOfWeek.name.first().toString()
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
