package com.anitech.growdaily.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.CommonMethods.Companion.formatDate
import com.anitech.growdaily.CommonMethods.Companion.isTodayDate
import com.anitech.growdaily.CommonMethods.Companion.isTomorrowDate
import com.anitech.growdaily.CommonMethods.Companion.isYesterdayDate
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.BarAdapter
import com.anitech.growdaily.adapter.EmptyStateAdapter
import com.anitech.growdaily.adapter.FilterSectionAdapter
import com.anitech.growdaily.adapter.ListAdapter
import com.anitech.growdaily.adapter.ScoreSectionAdapter
import com.anitech.growdaily.adapter.TaskAdapter
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskUiState
import com.anitech.growdaily.database.viewmodel.TaskViewModel
import com.anitech.growdaily.database.viewmodel.TaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentTaskBinding
import com.anitech.growdaily.dialog.CompletionInputDialog
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.enum_class.CompletionAction
import com.anitech.growdaily.enum_class.TaskType
import com.anitech.growdaily.enum_class.TrackingType
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

class TaskFragment : Fragment() {
    private var _binding: FragmentTaskBinding? = null
    private val binding get() = _binding!!
    
    private var taskAdapter: TaskAdapter? = null
    private var listAdapter: ListAdapter? = null
    private var barAdapter: BarAdapter? = null

    // New section adapters
    private var scoreSectionAdapter: ScoreSectionAdapter? = null
    private var filterSectionAdapter: FilterSectionAdapter? = null
    private var emptyStateAdapter: EmptyStateAdapter? = null
    private var hasPositionedBarInitially = false
    private var pendingScrollToToday = false
    private var pendingScrollToDate: String? = null
    private var pendingPastAnchorDate: String? = null
    private var pendingPastAnchorOffset: Int = 0
    private var accentColor: Int = Color.BLUE
    
    private val viewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskBinding.inflate(inflater, container, false)
        viewModel.ensureDate(CommonMethods.getTodayDate())
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideNavigationLoading()
        setupMenu()
        setupRecyclerViews()
        setupCompletionDialogResult()
        observeUiState()
        observeAccentColor()
        startMidnightRefresh()
    }

    private fun setupCompletionDialogResult() {
        childFragmentManager.setFragmentResultListener(
            CompletionInputDialog.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val (taskId, date, action) = CompletionAction.fromResultBundle(bundle)
                ?: return@setFragmentResultListener
            when (action) {
                is CompletionAction.CountDelta ->
                    viewModel.changeTaskCompletionBy(taskId, date, action.delta)
                is CompletionAction.TimerAdd ->
                    viewModel.addTimerDuration(taskId, date, action.seconds)
                is CompletionAction.ChecklistUpdate ->
                    viewModel.updateChecklist(taskId, date, action.json)
            }
        }
    }

    private fun setupMenu() {
        // We now handle the calendar icon globally in MainActivity for better 
        // reliability and to prevent leaks in this ViewPager setup.
    }

    fun getSelectedDateFormatted(): String? {
        val dateStr = viewModel.selectedDate.value ?: return null
        val localDate = LocalDate.parse(dateStr)
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
        return localDate.format(formatter)
    }

    fun showDatePicker() {
        val current = LocalDate.parse(viewModel.selectedDate.value ?: CommonMethods.getTodayDate())
        val calendar = Calendar.getInstance().apply {
            set(current.year, current.monthValue - 1, current.dayOfMonth)
        }
        val constraints = CalendarConstraints.Builder()
            .setOpenAt(calendar.timeInMillis)
            .build()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_task_date))
            .setSelection(calendar.timeInMillis)
            .setCalendarConstraints(constraints)
            .setPositiveButtonText(getString(R.string.picker_set_date))
            .setNegativeButtonText(getString(R.string.cancel_button))
            .setTheme(resolveDatePickerThemeRes())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedCalendar = Calendar.getInstance().apply {
                timeInMillis = selection
            }
            val selected = LocalDate.of(
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH) + 1,
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
            )
            pendingScrollToDate = selected.toString()
            viewModel.jumpToDate(selected)
            viewModel.setDate(selected.toString())
        }

        datePicker.show(parentFragmentManager, "TASK_DATE_PICKER")
    }

    override fun onResume() {
        super.onResume()
        hideNavigationLoading()
    }

    override fun onStop() {
        super.onStop()
        if (_binding != null) {
            hideNavigationLoading()
        }
    }

    private fun setupRecyclerViews() {
        setupTaskRecycler()
        setupListRecycler()
        setupBarRecycler()
        
        val sAdapter = ScoreSectionAdapter().also { scoreSectionAdapter = it }
        val lAdapter = listAdapter ?: return
        val fAdapter = FilterSectionAdapter(lAdapter).also { filterSectionAdapter = it }
        val tAdapter = taskAdapter ?: return
        val eAdapter = EmptyStateAdapter().also { emptyStateAdapter = it }

        // Combine all into TaskFragmentConcatAdapter
        val taskFragmentConcatAdapter = ConcatAdapter(
            sAdapter,
            fAdapter,
            tAdapter,
            eAdapter
        )

        binding.mainRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskFragmentConcatAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun observeUiState() {
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            // Update the adapter's selected date and refresh highlights
            barAdapter?.refreshSelection(LocalDate.parse(date))
            checkCurrentWeekStatus()

            // Update toolbar date
            val localDate = LocalDate.parse(date)
            val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
            (requireActivity() as? MainActivity)?.updateToolbarDate(localDate.format(formatter))
        }

        viewModel.taskUiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }

        viewModel.barTimelineState.observe(viewLifecycleOwner) { state ->
            renderBarTimeline(state)
        }

        viewModel.allLists.observe(viewLifecycleOwner) {
            listAdapter?.setData(it)
            val currentId = viewModel.selectedListId.value
            if (currentId != null && it.none { list -> list.id == currentId }) {
                viewModel.setSelectedList(null)
            }
        }
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            accentColor = color
            scoreSectionAdapter?.setAccentColor(color)
            listAdapter?.setAccentColor(color)
            barAdapter?.setAccentColor(color)
            //taskAdapter?.setAccentColor(color)
            binding.barGraph2.scoreBarBg.setAccentColor(color)
            binding.navigationLoadingOverlay.findViewById<android.widget.ProgressBar>(R.id.progressBarAnalysis)?.indeterminateTintList =
                android.content.res.ColorStateList.valueOf(color)
        }
    }

    @StyleRes
    private fun resolveDatePickerThemeRes(): Int {
        val context = requireContext()
        return when (accentColor) {
            ContextCompat.getColor(context, R.color.category_red) -> R.style.Theme_GrowDaily_MaterialDatePicker_Red
            ContextCompat.getColor(context, R.color.category_orange) -> R.style.Theme_GrowDaily_MaterialDatePicker_Orange
            ContextCompat.getColor(context, R.color.category_yellow) -> R.style.Theme_GrowDaily_MaterialDatePicker_Yellow
            ContextCompat.getColor(context, R.color.category_green) -> R.style.Theme_GrowDaily_MaterialDatePicker_Green
            ContextCompat.getColor(context, R.color.category_teal) -> R.style.Theme_GrowDaily_MaterialDatePicker_Teal
            ContextCompat.getColor(context, R.color.category_blue) -> R.style.Theme_GrowDaily_MaterialDatePicker_Blue
            ContextCompat.getColor(context, R.color.category_purple) -> R.style.Theme_GrowDaily_MaterialDatePicker_Purple
            else -> R.style.Theme_GrowDaily_MaterialDatePicker_DarkBlue
        }
    }

    private fun render(state: TaskUiState) {
        updateEmptyState(state)

        // empty / no task UI
        emptyStateAdapter?.setVisible(state.isEmpty)

        // task list
        taskAdapter?.updateList(state.tasks, state.date, mode = state.dateMode)

        // score section
        scoreSectionAdapter?.updateScores(
            dayScore = state.dayScore,
            weekScore = state.weekScore,
            monthScore = state.monthScore,
            dayText = getDayText(state.date),
            weekText = getWeekText(state.date),
            monthText = getMonthText(state.date)
        )

        // bar graph
        //selected list
        listAdapter?.setSelectedListById(state.selectedListId)
    }

    private fun renderBarTimeline(state: com.anitech.growdaily.data_class.BarTimelineState) {
        barAdapter?.updateData(
            newScores = state.scores,
            selectedDate = LocalDate.parse(state.selectedDate),
            isLoadingPast = state.isLoadingPast,
            isLoadingFuture = state.isLoadingFuture
        )

        if (pendingPastAnchorDate != null) {
            restorePastAnchorPosition()
            return
        }

        val scrollDate = pendingScrollToDate
        if (scrollDate != null) {
            scrollToDate(LocalDate.parse(scrollDate))
            pendingScrollToDate = null
            hasPositionedBarInitially = true
            pendingScrollToToday = false
            return
        }

        if (!hasPositionedBarInitially || pendingScrollToToday) {
            scrollBarWindowToTodayAnchor()
            hasPositionedBarInitially = true
            pendingScrollToToday = false
        }
    }

    private fun updateEmptyState(state: TaskUiState) {
        if (state.selectedListId != null) {
            emptyStateAdapter?.setContent(
                imageRes = R.drawable.add_task_ic,
                title = getString(R.string.empty_list_tasks_title),
                subtitle = getString(R.string.empty_list_tasks_subtitle)
            )
        } else {
            emptyStateAdapter?.setContent(
                imageRes = R.drawable.add_task_ic,
                title = getString(R.string.empty_day_tasks_title),
                subtitle = getString(R.string.empty_day_tasks_subtitle)
            )
        }
    }

    private fun setupTaskRecycler() {
        taskAdapter = TaskAdapter(object : TaskAdapter.OnItemClickListener {
            override fun moveToEditListener(task: TaskEntity) {
                val navController = findNavController()
                if (task.taskType == TaskType.DAILY) {
                    val bundle = Bundle().apply {
                        putString("taskId", task.id)
                        putParcelable("task", task)
                    }
                    navigateToAnalysis(bundle)
                } else {
                    val bundle = Bundle().apply { putParcelable("task", task) }
                    navController.navigate(R.id.nav_add_task, bundle)
                }
            }

            override fun onTaskCompleteClick(taskId: String, date: String) {
                val state = viewModel.taskUiState.value ?: return
                val uiItem = state.tasks.find { it.task.id == taskId && it.completionDate == date }
                    ?: return
                val task = uiItem.task

                val count  = uiItem.completionCount
                val target = uiItem.trackingSettings.dailyTargetCount.coerceAtLeast(1)

                when (task.trackingType) {
                    TrackingType.BINARY -> {
                        if (count >= 1) viewModel.resetTaskCompletion(taskId, date)
                        else viewModel.incrementTaskCompletion(taskId, date)
                    }
                    TrackingType.COUNT -> {
                        if (count >= target) {
                            val formattedDate = runCatching {
                                LocalDate.parse(date).format(DateTimeFormatter.ofPattern(getString(R.string.date_format_mmm_d), Locale.getDefault()))
                            }.getOrDefault(date)
                            TaskActionDialog(
                                context = requireContext(),
                                title = getString(R.string.reset_progress_title),
                                message = getString(R.string.reset_progress_message, formattedDate),
                                primaryLabel = getString(R.string.reset_button),
                                iconRes = R.drawable.ic_warning,
                                accentColor = requireContext().getColor(R.color.brand_blue),
                                iconBubbleColor = 0x332196F3,
                                onPrimaryAction = {
                                    viewModel.resetTaskCompletion(taskId, date)
                                }
                            ).show()
                        } else {
                            val existing = buildCompletionEntity(taskId, date, count = count)
                            if (isAdded && !isStateSaved) {
                                CompletionInputDialog.newInstance(
                                    task = task,
                                    date = date,
                                    currentCompletion = existing,
                                    trackingSettingsOverride = uiItem.trackingSettings
                                ).show(childFragmentManager, "completionDialog")
                            }
                        }
                    }
                    TrackingType.TIMER -> {
                        lifecycleScope.launch {
                            val existing = (requireActivity().application as MyApp)
                                .repository.completionDao
                                .isTaskCompletedOnDate(taskId, date)
                                ?: buildCompletionEntity(taskId, date)

                            if (isAdded && !isStateSaved) {
                                CompletionInputDialog.newInstance(
                                    task = task,
                                    date = date,
                                    currentCompletion = existing,
                                    trackingSettingsOverride = uiItem.trackingSettings
                                ).show(childFragmentManager, "completionDialog")
                            }
                        }
                    }
                    TrackingType.CHECKLIST -> {
                        lifecycleScope.launch {
                            val repository = (requireActivity().application as MyApp).repository
                            val existing = repository.completionDao
                                .isTaskCompletedOnDate(taskId, date)
                            val checklistItemsForDate =
                                uiItem.trackingSettings.checklistItemsJson ?: task.checklistItems
                            if (isAdded && !isStateSaved) {
                                CompletionInputDialog.newInstance(
                                    task = task,
                                    date = date,
                                    currentCompletion = existing,
                                    trackingSettingsOverride = uiItem.trackingSettings,
                                    checklistItemsOverride = checklistItemsForDate
                                ).show(childFragmentManager, "completionDialog")
                            }
                        }
                    }
                }
            }

            override fun onTaskCompleteLongClick(taskId: String, date: String) {
                viewModel.decrementTaskCompletion(taskId, date)
            }
        })
    }

    private fun setupListRecycler() {
        listAdapter = ListAdapter(emptyList(), object : ListAdapter.OnItemClickListener {
                override fun onItemClick(conditionItem: ListEntity, isSelected: Boolean) {
                    if (isSelected) viewModel.setSelectedList(null)
                    else viewModel.setSelectedList(conditionItem.id)
                }

                override fun onAllClick(isSelected: Boolean) {
                    if (isSelected) return
                    viewModel.setSelectedList(null)
                }

                override fun onLongPress(item: ListEntity) {
                    val bundle = Bundle().apply { putParcelable("ConditionEntity", item) }
                    findNavController().navigate(R.id.addList, bundle)
                }

                override fun onNewListClick() {
                    findNavController().navigate(R.id.addList)
                }

                override fun onMangeListClick() {
                    findNavController().navigate(R.id.manageListFragment)
                }
            })
    }

    private fun setupBarRecycler() {
        val bAdapter = BarAdapter(object : BarAdapter.OnBarInteractionListener {
            override fun onBarSelected(dailyScore: DailyScore) {
                viewModel.setDate(dailyScore.date)
                checkCurrentWeekStatus()
            }

            override fun onTodayBarOutOfView(isFuture: Boolean) {
                // We no longer trigger based on scroll visibility alone
            }

            override fun onTodayBarInView() {
                // We no longer trigger based on scroll visibility alone
            }
        }).also { barAdapter = it }

        binding.barGraph2.recyclerViewBar.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = bAdapter
            setHasFixedSize(true)
            itemAnimator = null
            
            // Use PagerSnapHelper for full-week paging
            val snapHelper = androidx.recyclerview.widget.PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)

            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                    return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val firstVisible = lm.findFirstVisibleItemPosition()
                    val lastVisible = lm.findLastVisibleItemPosition()

                    val currentBarAdapter = barAdapter ?: return
                    if (currentBarAdapter.shouldLoadMorePast(firstVisible)) {
                        capturePastAnchorIfNeeded(lm)
                        viewModel.loadMoreBarPast()
                    }
                    if (currentBarAdapter.shouldLoadMoreFuture(lastVisible)) {
                        viewModel.loadMoreBarFuture()
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                }
            })

            pendingScrollToToday = true
        }
    }

    private fun updateTodayButtonAction(isVisible: Boolean, isFuture: Boolean = false) {
        val activity = requireActivity() as? MainActivity ?: return
        
        // We set the base "Today" text. MainActivity will handle icon direction.
        val label = getString(R.string.today)
        val fullTextForDetection = if (isFuture) "< $label" else "$label >"
        
        if (activity.todayButtonState.value?.isVisible != isVisible || 
            (isVisible && activity.todayButtonState.value?.text != fullTextForDetection)) {
            activity.todayButtonState.value = MainActivity.TodayButtonState(isVisible, fullTextForDetection)
            activity.invalidateOptionsMenu()
        }
    }

    private fun checkCurrentWeekStatus() {
        val today = LocalDate.now()
        val selectedDateStr = viewModel.selectedDate.value ?: return
        val selectedDate = LocalDate.parse(selectedDateStr)

        val todayWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val todayWeekEnd = todayWeekStart.plusDays(6)

        val isSelectedInCurrentWeek = !selectedDate.isBefore(todayWeekStart) && !selectedDate.isAfter(todayWeekEnd)

        if (isSelectedInCurrentWeek) {
            updateTodayButtonAction(false)
        } else {
            // Determine direction based on selected date relative to today
            val isFuture = selectedDate.isAfter(today)
            updateTodayButtonAction(true, isFuture)
        }
    }

    private fun navigateToAnalysis(bundle: Bundle) {
        binding.root.post {
            if (_binding == null || !isAdded) return@post
            findNavController().navigate(R.id.taskDetailFragment, bundle)
        }
    }

    private fun hideNavigationLoading() {
        binding.navigationLoadingOverlay.visibility = View.GONE
    }

    fun scrollToToday() {
        val today = LocalDate.now()
        scrollToDate(today, smooth = true)
        viewModel.setDate(CommonMethods.getTodayDate())
        updateTodayButtonAction(false)
    }

    private fun scrollToDate(date: LocalDate, smooth: Boolean = false) {
        val recyclerView = binding.barGraph2.recyclerViewBar
        val todayWeekPosition = barAdapter?.getAdapterPositionForDate(date) ?: RecyclerView.NO_POSITION
        if (todayWeekPosition != RecyclerView.NO_POSITION) {
            if (smooth) {
                recyclerView.smoothScrollToPosition(todayWeekPosition)
                updateTodayButtonAction(false)
            } else {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(todayWeekPosition, 0)
            }
        } else {
            if (date == LocalDate.now()) {
                pendingScrollToToday = true
                viewModel.resetBarWindowToToday()
            } else {
                pendingScrollToDate = date.toString()
                viewModel.jumpToDate(date)
            }
        }
    }

    private fun scrollBarWindowToTodayAnchor() {
        val layoutManager = binding.barGraph2.recyclerViewBar.layoutManager as? LinearLayoutManager
            ?: return
        val todayWeekPosition = barAdapter?.getAdapterPositionForDate(LocalDate.now()) ?: RecyclerView.NO_POSITION
        if (todayWeekPosition == RecyclerView.NO_POSITION) return
        layoutManager.scrollToPositionWithOffset(todayWeekPosition, 0)
    }

    private fun capturePastAnchorIfNeeded(layoutManager: LinearLayoutManager) {
        if (pendingPastAnchorDate != null) return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val anchorDate = barAdapter?.getDateAtAdapterPosition(firstVisible) ?: return
        val anchorView = layoutManager.findViewByPosition(firstVisible) ?: return
        pendingPastAnchorDate = anchorDate
        pendingPastAnchorOffset = anchorView.left
    }

    private fun restorePastAnchorPosition() {
        val anchorDate = pendingPastAnchorDate ?: return
        val layoutManager = binding.barGraph2.recyclerViewBar.layoutManager as? LinearLayoutManager
            ?: return
        val anchorPosition = barAdapter?.getAdapterPositionForDate(LocalDate.parse(anchorDate)) ?: RecyclerView.NO_POSITION
        if (anchorPosition == RecyclerView.NO_POSITION) return
        layoutManager.scrollToPositionWithOffset(anchorPosition, pendingPastAnchorOffset)
        pendingPastAnchorDate = null
    }

    private fun startMidnightRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val delayMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)
                delay(delayMillis)

                if (_binding == null) break

                barAdapter?.refreshTodayHighlight()
                checkCurrentWeekStatus()
                scoreSectionAdapter?.updateScores(
                    dayScore = viewModel.taskUiState.value?.dayScore ?: 0f,
                    weekScore = viewModel.taskUiState.value?.weekScore ?: 0f,
                    monthScore = viewModel.taskUiState.value?.monthScore ?: 0f,
                    dayText = getDayText(viewModel.taskUiState.value?.date ?: CommonMethods.getTodayDate()),
                    weekText = getWeekText(viewModel.taskUiState.value?.date ?: CommonMethods.getTodayDate()),
                    monthText = getMonthText(viewModel.taskUiState.value?.date ?: CommonMethods.getTodayDate())
                )
            }
        }
    }

    private fun getDayText(date: String): String {
        return when {
            isTodayDate(date) -> getString(R.string.today)
            isTomorrowDate(date) -> getString(R.string.tomorrow)
            isYesterdayDate(date) -> getString(R.string.yesterday)
            else -> formatDate(date)
        }
    }

    private fun getWeekText(date: String): String {
        val selected = LocalDate.parse(date)
        val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val formatter = DateTimeFormatter.ofPattern(getString(R.string.date_format_d_mmm), Locale.getDefault())
        return getString(R.string.week_range_format, weekStart.format(formatter), weekEnd.format(formatter))
    }

    private fun getMonthText(date: String): String {
        val selected = LocalDate.parse(date)
        return selected.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    private fun buildCompletionEntity(
        taskId: String,
        date: String,
        count: Int = 0,
        durationSeconds: Long = 0L,
        checklistJson: String? = null
    ) = com.anitech.growdaily.data_class.TaskCompletionEntity(
        taskId          = taskId,
        date            = date,
        count           = count,
        durationSeconds = durationSeconds,
        checklistJson   = checklistJson
    )
    
    override fun onDestroyView() {
        if (_binding != null) {
            binding.mainRecyclerView.adapter = null
            binding.barGraph2.recyclerViewBar.adapter = null
        }
        super.onDestroyView()
        taskAdapter = null
        listAdapter = null
        barAdapter = null
        scoreSectionAdapter = null
        filterSectionAdapter = null
        emptyStateAdapter = null
        _binding = null
    }
}
