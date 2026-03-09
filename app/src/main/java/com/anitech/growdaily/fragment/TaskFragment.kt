package com.anitech.growdaily.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
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
import com.anitech.growdaily.adapter.ListAdapter
import com.anitech.growdaily.adapter.TaskAdapter
import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.data_class.TaskUiState
import com.anitech.growdaily.database.TaskViewModel
import com.anitech.growdaily.database.TaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentTaskBinding
import com.anitech.growdaily.dialog.ResetCompletionBottomSheet
import com.anitech.growdaily.enum_class.TaskType
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

class TaskFragment : Fragment() {
    private var _binding: FragmentTaskBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var listAdapter: ListAdapter
    private lateinit var barAdapter: BarAdapter
    private val viewModel: TaskViewModel by viewModels {
        TaskViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    companion object {
        private const val TAG = "TaskFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskBinding.inflate(inflater, container, false)
        viewModel.setDate(CommonMethods.getTodayDate())
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress = 0
        binding.scoreLayout.cpi.max = 10        // maximum value
        binding.scoreLayout.cpi.setProgress(progress, true)

        binding.scoreLayout.cpiWeek.max = 10       // maximum value
        binding.scoreLayout.cpiWeek.setProgress(progress, true)

        binding.scoreLayout.cpiMonth.max = 10       // maximum value
        binding.scoreLayout.cpiMonth.setProgress(progress, true)

        setupRecyclerViews()
        observeUiState()

    }

    private fun setupRecyclerViews() {
        setupTaskRecycler()
        setupListRecycler()
        setupBarRecycler()
    }

    private fun observeUiState() {
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            updateDayText(date)
            updateWeekText(date)
            updateMonthText(date)
        }

        viewModel.taskUiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }

        viewModel.allLists.observe(viewLifecycleOwner) {
            listAdapter.setData(it)
        }
    }

    private fun render(state: TaskUiState) {
        // empty / no task UI
        binding.noDayTaskLayoutContainer.visibility =
            if (state.isEmpty) View.VISIBLE else View.GONE

        binding.emptySpaceLayoutContainer.visibility =
            if (state.isEmpty) View.GONE else View.VISIBLE

        // task list
        taskAdapter.updateList(state.tasks, state.date, mode = state.dateMode)

        // score UI
        updateScore(binding.scoreLayout.doneWeight, binding.scoreLayout.cpi, state.dayScore)
        updateScore(
            binding.scoreLayout.doneWeekWeight,
            binding.scoreLayout.cpiWeek,
            state.weekScore
        )
        updateScore(
            binding.scoreLayout.doneMonthWeight,
            binding.scoreLayout.cpiMonth,
            state.monthScore
        )

        // bar graph
        barAdapter.updateData(state.barScores)

        //selected list
        listAdapter.setSelectedListById(state.selectedListId)
    }

    private fun setupTaskRecycler() {
        taskAdapter = TaskAdapter(object : TaskAdapter.OnItemClickListener {
            override fun moveToEditListener(task: TaskEntity) {
                val navController = findNavController()
                val bundle = bundleOf("task" to task)

                if (task.taskType == TaskType.DAILY) {

                    val bundle = bundleOf("taskId" to task.id)

                    navController.navigate(
                        R.id.analysisRepeatTaskFragment,
                        bundle
                    )

                } else {
                    navController.navigate(R.id.nav_add_task, bundle)
                }
            }

            override fun onTaskCompleteClick(taskId: String, date: String) {
                val state = viewModel.taskUiState.value ?: return
                val task = state.tasks.find { it.task.id == taskId }?.task ?: return

                val count = state.completionForDate[taskId] ?: 0
                val target = task.dailyTargetCount.coerceAtLeast(1)

                if (target == 1) {
                    if (count >= 1) {
                        viewModel.resetTaskCompletion(taskId, date)
                    } else {
                        viewModel.incrementTaskCompletion(taskId, date)
                    }
                } else {
                    if (count >= target) {
                        ResetCompletionBottomSheet {
                            viewModel.resetTaskCompletion(taskId, date)
                        }.show(parentFragmentManager, "resetSheet")
                    } else {
                        viewModel.incrementTaskCompletion(taskId, date)
                    }
                }
            }

            override fun onTaskCompleteLongClick(taskId: String, date: String) {
                viewModel.decrementTaskCompletion(taskId, date)
            }
        })

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            adapter = taskAdapter
        }
    }

    private fun setupListRecycler() {
        listAdapter =
            ListAdapter(emptyList(), object : ListAdapter.OnItemClickListener {

                override fun onItemClick(conditionItem: ListEntity, isSelected: Boolean) {

                    if (isSelected) {
                        // same list tapped → back to ALL
                        viewModel.setSelectedList(null)
                    } else {
                        viewModel.setSelectedList(conditionItem.id)
                    }
                }

                override fun onAllClick(isSelected: Boolean) {
                    // ALL already selected → ignore
                    if (isSelected) return
                    viewModel.setSelectedList(null)
                }

                override fun onLongPress(conditionList: List<ListEntity>) {
                    // TODO: have to show manage list option, better bottom shit dialog
                    Toast.makeText(requireContext(), "manage this list item", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onNewListClick() {
                    findNavController().navigate(R.id.editList)

                }

                override fun onMangeListClick() {
                    findNavController().navigate(R.id.manageListFragment)

                }
            })

        binding.conditionLayout.recyclerView.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = listAdapter
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }
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
                    rv.parent.requestDisallowInterceptTouchEvent(true) // Parent (ViewPager2) ko rok deta hai
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        // Scroll ruk gaya
                        barAdapter.checkIfTodayVisible(recyclerView.layoutManager!!)
                    }
                }
            })

            scrollToToday()
        }
    }

    fun scrollToToday() {
        val today = LocalDate.now()
        barAdapter.setCenterDate(today)

        val layoutManager = binding.barGraph2.recyclerViewBar.layoutManager as? LinearLayoutManager
            ?: return

        layoutManager.scrollToPositionWithOffset(40, 0)
        viewModel.setDate(CommonMethods.getTodayDate())
    }

    private fun updateDayText(date: String) {
        val text = when {
            isTodayDate(date) -> getString(R.string.today)
            isTomorrowDate(date) -> getString(R.string.tomorrow)
            isYesterdayDate(date) -> getString(R.string.yesterday)
            else -> formatDate(date)
        }
        binding.scoreLayout.dayText.text = text
    }

    private fun updateWeekText(date: String) {
        val selected = LocalDate.parse(date)

        val weekStart =
            selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val formatter =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

        val text =
            "${weekStart.format(formatter)}–${weekEnd.format(formatter)}"

        binding.scoreLayout.weekText.text = text
    }


    private fun updateMonthText(date: String) {
        val selected = LocalDate.parse(date)

        val monthName =
            selected.month.getDisplayName(
                TextStyle.FULL,
                Locale.ENGLISH
            )

        binding.scoreLayout.monthText.text = monthName
    }

    private fun formatScore(value: Float): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    private fun updateScore(textView: TextView, progress: CircularProgressIndicator, value: Float) {
        textView.text = formatScore(value)
        progress.setProgress(value.roundToInt(), true)
    }
}