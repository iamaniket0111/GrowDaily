package com.anitech.growdaily.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class TaskFragment : Fragment() {
    private var _binding: FragmentTaskBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var listAdapter: ListAdapter
    private lateinit var barAdapter: BarAdapter

    // New section adapters
    private lateinit var scoreSectionAdapter: ScoreSectionAdapter
    private lateinit var filterSectionAdapter: FilterSectionAdapter
    private lateinit var emptyStateAdapter: EmptyStateAdapter
    
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
        setupRecyclerViews()
        observeUiState()
        startMidnightRefresh()
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
        
        scoreSectionAdapter = ScoreSectionAdapter()
        filterSectionAdapter = FilterSectionAdapter(listAdapter)
        emptyStateAdapter = EmptyStateAdapter()

        // Combine all into TaskFragmentConcatAdapter
        val taskFragmentConcatAdapter = ConcatAdapter(
            scoreSectionAdapter,
            filterSectionAdapter,
            taskAdapter,
            emptyStateAdapter
        )

        binding.mainRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskFragmentConcatAdapter
        }
    }

    private fun observeUiState() {
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            // Date labels are now updated within the score adapter via render()
        }

        viewModel.taskUiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }

        viewModel.allLists.observe(viewLifecycleOwner) {
            listAdapter.setData(it)
            val currentId = viewModel.selectedListId.value
            if (currentId != null && it.none { list -> list.id == currentId }) {
                viewModel.setSelectedList(null)
            }
        }
    }

    private fun render(state: TaskUiState) {
        updateEmptyState(state)

        // empty / no task UI
        emptyStateAdapter.setVisible(state.isEmpty)

        // task list
        taskAdapter.updateList(state.tasks, state.date, mode = state.dateMode)

        // score section
        scoreSectionAdapter.updateScores(
            dayScore = state.dayScore,
            weekScore = state.weekScore,
            monthScore = state.monthScore,
            dayText = getDayText(state.date),
            weekText = getWeekText(state.date),
            monthText = getMonthText(state.date)
        )

        // bar graph
        barAdapter.updateData(
            newScores = state.barScores,
            selectedDate = LocalDate.parse(state.date)
        )

        //selected list
        listAdapter.setSelectedListById(state.selectedListId)
    }

    private fun updateEmptyState(state: TaskUiState) {
        if (state.selectedListId != null) {
            emptyStateAdapter.setContent(
                imageRes = R.drawable.add_task_ic,
                title = "No tasks in this list",
                subtitle = "Try another list or clear the filter to see everything scheduled for this day."
            )
        } else {
            emptyStateAdapter.setContent(
                imageRes = R.drawable.add_task_ic,
                title = "Nothing scheduled for this day",
                subtitle = "Tap + to add a task for this date."
            )
        }
    }

    private fun setupTaskRecycler() {
        taskAdapter = TaskAdapter(object : TaskAdapter.OnItemClickListener {
            override fun moveToEditListener(task: TaskEntity) {
                val navController = findNavController()
                if (task.taskType == TaskType.DAILY) {
                    val bundle = bundleOf("taskId" to task.id)
                    navigateToAnalysis(bundle)
                } else {
                    val bundle = bundleOf("task" to task)
                    navController.navigate(R.id.nav_add_task, bundle)
                }
            }

            override fun onTaskCompleteClick(taskId: String, date: String) {
                val state = viewModel.taskUiState.value ?: return
                val uiItem = state.tasks.find { it.task.id == taskId } ?: return
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
                                LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                            }.getOrDefault(date)
                            TaskActionDialog(
                                context = requireContext(),
                                title = "Reset progress?",
                                message = "This will clear the completion progress for $formattedDate.",
                                primaryLabel = "Reset",
                                iconRes = R.drawable.ic_warning,
                                accentColor = requireContext().getColor(R.color.brand_blue),
                                iconBubbleColor = 0x332196F3,
                                onPrimaryAction = {
                                    viewModel.resetTaskCompletion(taskId, date)
                                }
                            ).show()
                        } else {
                            val existing = buildCompletionEntity(taskId, date, count = count)
                            CompletionInputDialog(
                                task = task,
                                date = date,
                                currentCompletion = existing,
                                trackingSettingsOverride = uiItem.trackingSettings
                            ) { action ->
                                when (action) {
                                    is CompletionAction.CountDelta ->
                                        viewModel.changeTaskCompletionBy(taskId, date, action.delta)
                                    else -> Unit
                                }
                            }.show(parentFragmentManager, "completionDialog")
                        }
                    }
                    TrackingType.TIMER -> {
                        lifecycleScope.launch {
                            val existing = (requireActivity().application as MyApp)
                                .repository.completionDao
                                .isTaskCompletedOnDate(taskId, date)
                                ?: buildCompletionEntity(taskId, date)

                            CompletionInputDialog(
                                task = task,
                                date = date,
                                currentCompletion = existing,
                                trackingSettingsOverride = uiItem.trackingSettings
                            ) { action ->
                                when (action) {
                                    is CompletionAction.TimerAdd ->
                                        viewModel.addTimerDuration(taskId, date, action.seconds)
                                    else -> Unit
                                }
                            }.show(parentFragmentManager, "completionDialog")
                        }
                    }
                    TrackingType.CHECKLIST -> {
                        lifecycleScope.launch {
                            val repository = (requireActivity().application as MyApp).repository
                            val existing = repository.completionDao
                                .isTaskCompletedOnDate(taskId, date)
                            val checklistItemsForDate =
                                uiItem.trackingSettings.checklistItemsJson ?: task.checklistItems
                            CompletionInputDialog(
                                task = task,
                                date = date,
                                currentCompletion = existing,
                                trackingSettingsOverride = uiItem.trackingSettings,
                                checklistItemsOverride = checklistItemsForDate
                            ) { action ->
                                when (action) {
                                    is CompletionAction.ChecklistUpdate ->
                                        viewModel.updateChecklist(taskId, date, action.json)
                                    else -> Unit
                                }
                            }.show(parentFragmentManager, "completionDialog")
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
                    val bundle = bundleOf("ConditionEntity" to item)
                    findNavController().navigate(R.id.editList, bundle)
                }

                override fun onNewListClick() {
                    findNavController().navigate(R.id.editList)
                }

                override fun onMangeListClick() {
                    findNavController().navigate(R.id.manageListFragment)
                }
            })
    }

    private fun setupBarRecycler() {
        barAdapter = BarAdapter(object : BarAdapter.OnBarInteractionListener {
            override fun onBarSelected(score: DailyScore) {
                viewModel.setDate(score.date)
            }

            override fun onTodayBarOutOfView() {
                binding.barGraph2.goToTodayButton.visibility = View.VISIBLE
            }
        })

        binding.barGraph2.goToTodayButton.setOnClickListener {
            scrollToToday()
            binding.barGraph2.goToTodayButton.visibility = View.GONE
        }

        binding.barGraph2.recyclerViewBar.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = barAdapter
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                    return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        barAdapter.checkIfTodayVisible(recyclerView.layoutManager!!)
                    }
                }
            })

            scrollToToday()
        }
    }

    private fun navigateToAnalysis(bundle: Bundle) {
        binding.root.post {
            if (_binding == null || !isAdded) return@post
            findNavController().navigate(R.id.analysisRepeatTaskFragment, bundle)
        }
    }

    private fun hideNavigationLoading() {
        binding.navigationLoadingOverlay.visibility = View.GONE
    }

    fun scrollToToday() {
        val today = LocalDate.now()
        barAdapter.setCenterDate(today)
        val layoutManager = binding.barGraph2.recyclerViewBar.layoutManager as? LinearLayoutManager
            ?: return
        layoutManager.scrollToPositionWithOffset(40, 0)
        viewModel.setDate(CommonMethods.getTodayDate())
    }

    private fun startMidnightRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val delayMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)
                delay(delayMillis)

                if (_binding == null) break

                barAdapter.refreshTodayHighlight()
                barAdapter.checkIfTodayVisible(binding.barGraph2.recyclerViewBar.layoutManager ?: return@launch)
                scoreSectionAdapter.updateScores(
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
        val formatter = DateTimeFormatter.ofPattern(getString(R.string.date_format_d_mmm), Locale.ENGLISH)
        return getString(R.string.week_range_format, weekStart.format(formatter), weekEnd.format(formatter))
    }

    private fun getMonthText(date: String): String {
        val selected = LocalDate.parse(date)
        return selected.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
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
        super.onDestroyView()
        _binding = null
    }
}
