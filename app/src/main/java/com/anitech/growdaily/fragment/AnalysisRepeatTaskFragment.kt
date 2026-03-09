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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.BarAdapter2
import com.anitech.growdaily.adapter.HistoryAdapterAnalysis
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.AnalysisViewModel
import com.anitech.growdaily.database.AnalysisViewModelFactory
import com.anitech.growdaily.databinding.FragmentAnalysisRepeatTaskBinding
import com.anitech.growdaily.enum_class.PeriodType
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AnalysisRepeatTaskFragment : Fragment() {

    private var _binding: FragmentAnalysisRepeatTaskBinding? = null
    private val binding get() = _binding!!

    private val args: AnalysisRepeatTaskFragmentArgs by navArgs()

    private lateinit var barAdapter: BarAdapter2
    private lateinit var historyAdapter: HistoryAdapterAnalysis

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
        viewModel.setTaskId(args.taskId)
        setupHistoryAdapter()
        setupBarAdapter()
        setClickListeners()
        observeViewModel()
    }

    // --------------------------------------------------
    // ADAPTER SETUP
    // --------------------------------------------------

    private fun setupHistoryAdapter() {

        historyAdapter = HistoryAdapterAnalysis(
            startDate = LocalDate.now(),
            completedDates = emptySet(),
            taskColor = ContextCompat.getColor(requireContext(), R.color.brand_blue)
        )

        binding.weekExpanded.weekExpandedRv.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = historyAdapter
        }
    }


    private fun setupBarAdapter() {

        barAdapter = BarAdapter2(null)

        binding.progressBar.barGraph2.recyclerViewBar.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = barAdapter
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
            val taskStart = LocalDate.parse(task.taskAddedDate)

            bindHeader(task)
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
            binding.totalCompletion.txtTotalDays.setTextColor(color)
            binding.totalCompletion.progressOverall.setProgressColor(color)
            binding.totalCompletion.progressOverall.setProgress(state.completionPercent)

            // History — only auto-scroll on first load
            val isFirstLoad = historyAdapter.itemCount == 0
            historyAdapter.updateData(taskStart, state.completedDates, color)
            binding.weekExpanded.btnPrevYear.setColorFilter(color)
            binding.weekExpanded.btnNextYear.setColorFilter(color)
                binding.weekExpanded.weekExpandedRv.post {
                    binding.weekExpanded.weekExpandedRv.scrollToPosition(historyAdapter.itemCount - 1)

            }
        }

        // ---- BAR GRAPH: rebuilds only on period/anchor change ----
        viewModel.barState.observe(viewLifecycleOwner) { state ->
            val color = viewModel.overviewState.value?.task?.colorCode
                ?.let { TaskColor.fromName(it)?.toColorInt(requireContext()) }
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)

            barAdapter.setPeriod(state.period)
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
            val taskStart = LocalDate.parse(task.taskAddedDate)
            val color = TaskColor.fromName(task.colorCode)?.toColorInt(requireContext())
                ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)

            binding.yearHeapMap.txtYear.text = state.heatmapYear.toString()
            binding.yearHeapMap.heatmapLayout.setYear(state.heatmapYear)
            binding.yearHeapMap.heatmapLayout.bindHeatmap(
                taskAddedDate = taskStart,
                completedDates = state.heatmapDates,
                activeColor = color
            )
            binding.yearHeapMap.btnNextYear.setColorFilter(color)
            binding.yearHeapMap.btnPrevYear.setColorFilter(color)
            binding.yearHeapMap.btnNextYear.isEnabled = state.isHeatmapNextEnabled
            binding.yearHeapMap.btnNextYear.alpha = if (state.isHeatmapNextEnabled) 1f else 0.3f
            binding.yearHeapMap.btnPrevYear.isEnabled = state.isHeatmapPrevEnabled
            binding.yearHeapMap.btnPrevYear.alpha = if (state.isHeatmapPrevEnabled) 1f else 0.3f
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

        val activeTextColor = ContextCompat.getColor(requireContext(), R.color.black)
        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.gray)

        fun styleTab(view: TextView, isActive: Boolean) {
            view.setTextColor(if (isActive) activeTextColor else inactiveTextColor)

            if (isActive) {
                view.setBackgroundResource(R.drawable.circular_corners)
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

    private fun bindHeader(task: TaskEntity) {
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

        val startDate = LocalDate.parse(task.taskAddedDate)
        val today = LocalDate.now()

        header.txtStartedSince.text =
            "Started since ${startDate.dayOfMonth} ${startDate.month.name.take(3)} ${startDate.year}"

        val daysRunning =
            ChronoUnit.DAYS.between(startDate, today).toInt() + 1

        header.txtRunningFor.text = "• $daysRunning days"

    }

    private fun bindTaskIcon(task: TaskEntity, color: Int) = with(binding.iconLayout) {
        val iconRes =
            TaskIcon.fromName(task.iconResId)?.resId ?: R.drawable.ic_trophy
        imgTaskIcon.setImageResource(iconRes)

        viewIconBg.backgroundTintList = ColorStateList.valueOf(color)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.analysis_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

    private fun LocalDate.toDisplayFormat(): String {
        val month = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$month $dayOfMonth, $year"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
