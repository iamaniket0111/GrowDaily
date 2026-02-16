package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.graphics.Typeface
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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.BarAdapter2
import com.anitech.growdaily.adapter.WeekHabitAdapterHaveToCombine
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentAnalysisRepeatTaskBinding
import com.anitech.growdaily.enum_class.PeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon

class AnalysisRepeatTaskFragment : Fragment() {

    private var _binding: FragmentAnalysisRepeatTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private val args: AnalysisRepeatTaskFragmentArgs by navArgs()

    private lateinit var barAdapter: BarAdapter2
    private lateinit var task: TaskEntity
    private var currentPeriod: PeriodType? = null
    private var currentAnchor = LocalDate.now()
    private lateinit var weekAdapter: WeekHabitAdapterHaveToCombine
    private var currentHeatmapYear = LocalDate.now().year
    private var allCompletedDates: Set<LocalDate> = emptySet()





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // 👈 menu enable
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

        task = args.task
        //icon
        bindTaskIcon()

        //header
        bindHeader()
        //overview
        updateOverview()
        val taskColor = TaskColor
            .fromName(task.colorCode)
            ?.toColorInt(requireContext())
            ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)
        binding.overview.txtCompletionPercent.setTextColor(taskColor)
        binding.overview.progressCompletion.progressTintList =
            ColorStateList.valueOf(taskColor)

        //heatmap
        binding.yearHeapMap.txtYear.text = currentHeatmapYear.toString()

        binding.yearHeapMap.heatmapLayout.setYear(currentHeatmapYear)

        updateHeatmapNavigationButtons()

        //week adapter
           weekAdapter = WeekHabitAdapterHaveToCombine(
            task = task,
            completedDates = emptySet(),
            taskColor = taskColor
        )

        binding.weekExpanded.weekExpandedRv.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = weekAdapter
        }



        //bar adapter
        barAdapter = BarAdapter2(task)

        binding.progressBar.barGraph2.recyclerViewBar.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = barAdapter
        }
        selectTab(PeriodType.WEEK)
        setClickListeners()
        observeViewModel()
    }


    private fun setClickListeners() {
        binding.progressBar.tabWeek.setOnClickListener {
            selectTab(PeriodType.WEEK)
        }

        binding.progressBar.tabMonth.setOnClickListener {
            selectTab(PeriodType.MONTH)
        }

        binding.progressBar.tabYear.setOnClickListener {
            selectTab(PeriodType.YEAR)
        }

        //navigation
        binding.progressBar.btnPrevious.setOnClickListener {
            val period = currentPeriod ?: return@setOnClickListener
            currentAnchor = when (period) {
                PeriodType.WEEK -> currentAnchor.minusWeeks(1)
                PeriodType.MONTH -> currentAnchor.minusMonths(1)
                PeriodType.YEAR -> currentAnchor.minusYears(1)
            }

            barAdapter.setAnchorDate(currentAnchor)
            updatePeriodTitle()
            updateNavigationButtons()
        }

        binding.progressBar.btnNext.setOnClickListener {
            val period = currentPeriod ?: return@setOnClickListener
            val nextDate = when (period) {
                PeriodType.WEEK -> currentAnchor.plusWeeks(1)
                PeriodType.MONTH -> currentAnchor.plusMonths(1)
                PeriodType.YEAR -> currentAnchor.plusYears(1)
            }

            // prevent going to future
            if (!nextDate.isAfter(LocalDate.now())) {
                currentAnchor = nextDate
                barAdapter.setAnchorDate(currentAnchor)
                updatePeriodTitle()
                updateNavigationButtons()
            }
        }


        binding.weekExpanded.scrollToStartDay.setOnClickListener {
            if (weekAdapter.itemCount > 0) {
                binding.weekExpanded.weekExpandedRv.post {
                    binding.weekExpanded.weekExpandedRv.smoothScrollToPosition(0)
                }
            }        }

        binding.weekExpanded.scrollToCurrentDay.setOnClickListener {
            if (weekAdapter.itemCount > 0) {
                binding.weekExpanded.weekExpandedRv.post {
                    binding.weekExpanded.weekExpandedRv
                        .smoothScrollToPosition(weekAdapter.itemCount - 1)
                }
            }
        }

        binding.yearHeapMap.btnPrevYear.setOnClickListener {

            currentHeatmapYear--

            binding.yearHeapMap.txtYear.text = currentHeatmapYear.toString()
            binding.yearHeapMap.heatmapLayout.setYear(currentHeatmapYear)

            updateHeatmapNavigationButtons()
            bindHeatmapData()
        }

        binding.yearHeapMap.btnNextYear.setOnClickListener {

            if (currentHeatmapYear < LocalDate.now().year) {

                currentHeatmapYear++

                binding.yearHeapMap.txtYear.text = currentHeatmapYear.toString()
                binding.yearHeapMap.heatmapLayout.setYear(currentHeatmapYear)

                updateHeatmapNavigationButtons()
                bindHeatmapData()
            }
        }

    }


    private fun observeViewModel() {
//        viewModel.getCompletionsForTask(task.id).observe(viewLifecycleOwner) { list ->
//            // 🔥 Heatmap
//            allCompletedDates = list
//                .map { LocalDate.parse(it.date) }
//                .toSet()
//            bindHeatmapData()
//
//            //history adapter
//            val completedDates = list
//                .map { LocalDate.parse(it.date) }
//                .toSet()
//
//            weekAdapter.updateCompletedDates(completedDates)
//            binding.weekExpanded.weekExpandedRv.post {
//                binding.weekExpanded.weekExpandedRv.scrollToPosition(weekAdapter.itemCount - 1)
//            }
//            //bar adapter
//            barAdapter.setCompletions(list)
//
//
//            //overview
//            updateOverview()
//
//
//        }

    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.analysis_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_edit -> {
                val bundle = Bundle().apply {
                    putParcelable("task", args.task)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



    private fun selectTab(type: PeriodType) {

        if (currentPeriod == type) return // prevent double click

        currentPeriod = type
        barAdapter.setPeriod(type)

        // reset styles
        resetTabsUI()

        when (type) {
            PeriodType.WEEK -> highlightTab(binding.progressBar.tabWeek)
            PeriodType.MONTH -> highlightTab(binding.progressBar.tabMonth)
            PeriodType.YEAR -> highlightTab(binding.progressBar.tabYear)
        }

        updatePeriodTitle()
        updateNavigationButtons()
    }

    private fun resetTabsUI() {
        val normalColor = ContextCompat.getColor(requireContext(), R.color.gray)

        listOf(
            binding.progressBar.tabWeek,
            binding.progressBar.tabMonth,
            binding.progressBar.tabYear
        ).forEach {
            it.setBackgroundResource(0)
            it.setTextColor(normalColor)
            it.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun highlightTab(view: TextView) {
        view.setBackgroundResource(R.drawable.circular_corners)
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        view.setTypeface(null, Typeface.BOLD)
    }

    private fun updatePeriodTitle() {
        val period = currentPeriod ?: return
        val text = when (period) {

            PeriodType.WEEK -> {
                val start = currentAnchor.with(DayOfWeek.MONDAY)
                val end = start.plusDays(6)
                "${start.dayOfMonth} ${start.month.name.take(3)} - ${end.dayOfMonth} ${
                    end.month.name.take(
                        3
                    )
                }"
            }

            PeriodType.MONTH -> {
                "${
                    currentAnchor.month.name.lowercase().replaceFirstChar { it.uppercase() }
                } ${currentAnchor.year}"
            }

            PeriodType.YEAR -> {
                currentAnchor.year.toString()
            }
        }

        binding.progressBar.txtCurrentPeriod.text = text
    }

    private fun updateNavigationButtons() {
        val period = currentPeriod ?: return
        val isFutureBlocked = when (period) {
            PeriodType.WEEK -> currentAnchor.plusWeeks(1).isAfter(LocalDate.now())
            PeriodType.MONTH -> currentAnchor.plusMonths(1).isAfter(LocalDate.now())
            PeriodType.YEAR -> currentAnchor.plusYears(1).isAfter(LocalDate.now())
        }

        binding.progressBar.btnNext.apply {
            alpha = if (isFutureBlocked) 0.3f else 1f
            isEnabled = !isFutureBlocked
        }
    }


    private fun updateHeatmapNavigationButtons() {

        val isFuture = currentHeatmapYear >= LocalDate.now().year

        binding.yearHeapMap.btnNextYear.apply {
            alpha = if (isFuture) 0.3f else 1f
            isEnabled = !isFuture
        }
    }

    private fun bindHeatmapData() {

        val taskStart = LocalDate.parse(task.taskAddedDate)
        val taskColor = ContextCompat.getColor(requireContext(), R.color.brand_blue)

        binding.yearHeapMap.heatmapLayout.bindHeatmap(
            taskAddedDate = taskStart,
            completedDates = allCompletedDates,
            activeColor = taskColor
        )
    }

    private fun calculateCurrentStreak(
        taskStart: LocalDate,
        completedDates: Set<LocalDate>
    ): Int {

        var streak = 0
        var date = LocalDate.now()

        while (!date.isBefore(taskStart)) {

            if (completedDates.contains(date)) {
                streak++
                date = date.minusDays(1)
            } else {
                break
            }
        }

        return streak
    }


    private fun calculateBestStreak(
        taskStart: LocalDate,
        completedDates: Set<LocalDate>
    ): Int {

        var best = 0
        var current = 0

        var date = taskStart
        val today = LocalDate.now()

        while (!date.isAfter(today)) {

            if (completedDates.contains(date)) {
                current++
                best = maxOf(best, current)
            } else {
                current = 0
            }

            date = date.plusDays(1)
        }

        return best
    }


    private fun updateOverview() {

        val taskStart = LocalDate.parse(task.taskAddedDate)
        val today = LocalDate.now()

        val totalDays =
            ChronoUnit.DAYS.between(taskStart, today).toInt() + 1

        val completedCount =
            allCompletedDates.count {
                !it.isBefore(taskStart) && !it.isAfter(today)
            }

        val percent =
            if (totalDays > 0)
                (completedCount * 100) / totalDays
            else 0

        val currentStreak =
            calculateCurrentStreak(taskStart, allCompletedDates)

        val bestStreak =
            calculateBestStreak(taskStart, allCompletedDates)

        // 🔥 Bind UI
        binding.overview.txtCurrentStreak.text = "🔥 $currentStreak"
        binding.overview.txtBestStreak.text = "⭐ $bestStreak"

        binding.overview.txtCompletionPercent.text = "$percent%"
        binding.overview.txtCompletionCount.text =
            "$completedCount / $totalDays days"

        binding.overview.progressCompletion.progress = percent
    }

    private fun bindHeader() {

        val header = binding.header

        // Title
        header.txtTaskTitle.text = task.title

        // Note
        if (task.note.isNullOrBlank()) {
            header.txtTaskNote.visibility = View.GONE
         } else {
            header.txtTaskNote.text = task.note
         }

        // Task type
//        header.txtTaskType.text = when (task.taskType) {
//            TaskType.DAILY -> "Daily habit"
//            TaskType.REPEAT -> "Repeat task"
//            TaskType.UNTIL_COMPLETE -> "Until complete"
//        }

        // Reminder
        header.txtReminder.text =
            if (task.reminderEnabled && !task.reminderTime.isNullOrBlank())
                "Reminder at ${task.reminderTime}"
            else
                "Reminder at -- am/pm"

        // Schedule
        header.txtSchedule.text =
            if (task.isScheduled && !task.scheduledTime.isNullOrBlank())
                "Scheduled at ${task.scheduledTime}"
            else
                "Reminder at -- am/pm"

        // Weight / priority
        header.txtWeight.text = "Priority: ${task.weight.name.lowercase().replaceFirstChar { it.uppercase() }}"

        // Started since
        val startDate = LocalDate.parse(task.taskAddedDate)
        val today = LocalDate.now()

        header.txtStartedSince.text =
            "Started since ${startDate.dayOfMonth} ${startDate.month.name.take(3)} ${startDate.year}"

        val daysRunning = ChronoUnit.DAYS.between(startDate, today).toInt()

        header.txtRunningFor.text = "• $daysRunning days"
    }

    private fun bindTaskIcon() = with(binding.iconLayout) {

        // ----- Resolve Icon -----
        val iconRes = TaskIcon
            .fromName(task.iconResId)
            ?.resId
            ?: R.drawable.ic_trophy

        imgTaskIcon.setImageResource(iconRes)

        // ----- Resolve Color -----
        val colorInt = TaskColor
            .fromName(task.colorCode)
            ?.toColorInt(requireContext())
            ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)

        viewIconBg.backgroundTintList = ColorStateList.valueOf(colorInt)
    }






}
