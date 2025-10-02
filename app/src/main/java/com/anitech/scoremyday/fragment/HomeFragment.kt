package com.anitech.scoremyday.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.CommonMethods
import com.anitech.scoremyday.CommonMethods.Companion.filterTasks
import com.anitech.scoremyday.CommonMethods.Companion.isTodayDate
import com.anitech.scoremyday.CommonMethods.Companion.isTomorrowDate
import com.anitech.scoremyday.CommonMethods.Companion.isYesterdayDate
import com.anitech.scoremyday.MainActivity
import com.anitech.scoremyday.R
import com.anitech.scoremyday.ReorderTouchHelperCallback
import com.anitech.scoremyday.adapter.BarAdapter
import com.anitech.scoremyday.adapter.ConditionAdapter
import com.anitech.scoremyday.adapter.ConditionReorderAdapter
import com.anitech.scoremyday.adapter.TaskAdapter
import com.anitech.scoremyday.adapter.TouchHelperProvider
import com.anitech.scoremyday.data_class.ConditionEntity
import com.anitech.scoremyday.data_class.DailyScore
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.data_class.DateDataEntity
import com.anitech.scoremyday.data_class.DateItemEntity
import com.anitech.scoremyday.database.AppViewModel
import com.anitech.scoremyday.databinding.FragmentHomeBinding
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    lateinit var adapter: TaskAdapter
    var currentTodoDate: String = CommonMethods.Companion.getTodayDate()
    lateinit var conditionAdapter: ConditionAdapter

    // private lateinit var backPressedCallback: OnBackPressedCallback
    val fragmentTag = "HomeFragmentTag"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        adapter = TaskAdapter(emptyList(), object : TaskAdapter.OnItemClickListener {

            override fun moveToEditListener(task: DailyTask) {
                val bundle = bundleOf("task" to task)
                requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.nav_add_task, bundle)
            }

            override fun onItemSelectedCountChanged(count: Int) {
                val isSelectionMode = count > 0
                (activity as? MainActivity)?.setSelectionMode(isSelectionMode)

                // FIXME:  this part of code is not working cause of the  onFutureDateChangeBg is overriding
//                if (!isSelectionMode) binding.container.setBackgroundColor(Color.TRANSPARENT)
//                else binding.container.setBackgroundResource(R.color.background_color2)

                val title = if (count > 0) "$count selected" else getString(R.string.app_name)
                (activity as? AppCompatActivity)?.supportActionBar?.title = title
            }

            override fun onTaskCompleteClick(task: DailyTask) {
                viewModel.updateTask(task)
            }

            override fun onFutureDateChangeBg(isFuture: Boolean) {
                if (!isFuture) binding.container.setBackgroundColor(Color.TRANSPARENT)
                else binding.container.setBackgroundResource(R.color.background_color2)
            }
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.isNestedScrollingEnabled = false
        binding.recyclerView.adapter = adapter
        viewModel.setDate(currentTodoDate)

        conditionAdapter =
            ConditionAdapter(emptyList(), object : ConditionAdapter.OnItemClickListener {
                override fun onItemClick(conditionItem: ConditionEntity, isSelected: Boolean) {
                    if (isSelected) {
                        //have to remove the DateDataEntity item of the conditionItem.conditionTitle type
                        //if DateItemEntity data list becomes empty then remove the DateItemEntity
                        viewModel.removeConditionFromDate(
                            currentTodoDate,
                            conditionItem.conditionTitle
                        )
                    } else {
                        lifecycleScope.launch {
                            val tasks = withContext(Dispatchers.IO) {
                                viewModel.getTasksByConditionDirect(conditionItem.id)
                            }
                            val taskIds: List<String> = tasks.map { it.id }

                            val dummyDateItem = DateItemEntity(
                                date = currentTodoDate,
                                data = listOf(
                                    DateDataEntity(
                                        type = conditionItem.conditionTitle,
                                        itemIds = taskIds
                                    )
                                )
                            )
                            viewModel.upsertDateItem(dummyDateItem)
                        }
                    }
                }

                override fun onNoneClick() {
                    viewModel.deleteDateItem(currentTodoDate)
                }

                override fun onLongPress(conditionList: List<ConditionEntity>) {
                    binding.conditionLayoutContainer.visibility = View.GONE
                    binding.conditionReorderLayoutContainer.visibility = View.VISIBLE
                    // Step 1: late init variables
                    lateinit var touchHelper: ItemTouchHelper

                    val reorderAdapter: ConditionReorderAdapter =
                        ConditionReorderAdapter(mutableListOf(), object :
                            TouchHelperProvider {
                            override fun startDrag(viewHolder: RecyclerView.ViewHolder) {
                                touchHelper.startDrag(viewHolder)
                            }
                        })

                    val callback = ReorderTouchHelperCallback(reorderAdapter)
                    touchHelper = ItemTouchHelper(callback)

                    binding.conditionReorderLayout.reorderConditionRv.apply {
                        layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        adapter = reorderAdapter
                    }
                    touchHelper.attachToRecyclerView(binding.conditionReorderLayout.reorderConditionRv)

                    reorderAdapter.setData(conditionList)

                    binding.conditionReorderLayout.btnDone.setOnClickListener {
                        val reorderedList = reorderAdapter.getCurrentList()

                        // order/position set karo
                        reorderedList.forEachIndexed { index, condition ->
                            condition.sortOrder = index
                        }

                        viewModel.updateConditions(reorderedList)

                        binding.conditionLayoutContainer.visibility = View.VISIBLE
                        binding.conditionReorderLayoutContainer.visibility = View.GONE
                    }

                }
            })

        binding.conditionLayout.recyclerView.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = conditionAdapter
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }

        viewModel.allConditionsLiv.observe(viewLifecycleOwner) { conditions ->
            conditionAdapter.setData(conditions)
        }

        viewModel.dateEntity.observe(viewLifecycleOwner) { dateItem ->
            if (dateItem != null) {
                conditionAdapter.updateDate(dateItem.data)
            } else {
                conditionAdapter.updateDate(emptyList())
            }
        }

        viewModel.filteredTasksByCondition.observe(viewLifecycleOwner) { tasks ->
            if (tasks.isEmpty()) {
                binding.noDayTaskLayoutContainer.visibility = View.VISIBLE
                binding.emptySpaceLayoutContainer.visibility = View.GONE
            } else {
                binding.noDayTaskLayoutContainer.visibility = View.GONE
                binding.emptySpaceLayoutContainer.visibility = View.VISIBLE
            }

            adapter.updateList(tasks.reversed(), currentTodoDate)
            updateScoreUI(tasks, binding.scoreLayout.doneWeight, binding.scoreLayout.cpi)
        }
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dateTv.text = dateTextWork(currentTodoDate)
        binding.nextDate.setOnClickListener { v ->
            currentTodoDate = CommonMethods.Companion.getNextDate(currentTodoDate)
            binding.dateTv.text = dateTextWork(currentTodoDate)
            viewModel.setDate(currentTodoDate)
        }

        binding.prevDate.setOnClickListener { v ->
            currentTodoDate = CommonMethods.Companion.getPrevDate(currentTodoDate)
            binding.dateTv.text = dateTextWork(currentTodoDate)
            viewModel.setDate(currentTodoDate)
        }

        binding.dateTv.setOnClickListener { v ->
            if (currentTodoDate == CommonMethods.Companion.getTodayDate()) {
                // TODO: should provide options to select date from the calender??
                Toast.makeText(requireContext(), fragmentTag, Toast.LENGTH_SHORT).show()
            } else {
                currentTodoDate = CommonMethods.Companion.getTodayDate()
                binding.dateTv.text = dateTextWork(currentTodoDate)
                viewModel.setDate(currentTodoDate)
            }
        }

        val progress = 0
        binding.scoreLayout.cpi.max = 10        // maximum value
        binding.scoreLayout.cpi.setProgress(progress, true)

        binding.scoreLayout.cpiWeek.max = 10       // maximum value
        binding.scoreLayout.cpiWeek.setProgress(progress, true)

        binding.scoreLayout.cpiMonth.max = 10       // maximum value
        binding.scoreLayout.cpiMonth.setProgress(progress, true)


        val barAdapter = BarAdapter(emptyList()) { score ->
            if (currentTodoDate != score.date) {
                currentTodoDate = score.date
                binding.dateTv.text = dateTextWork(currentTodoDate)
                viewModel.setDate(currentTodoDate)
                updateDayText(score.date)
                //  conditionWork()
            }
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

            val todayIndex = 500-4
           scrollToPosition(todayIndex)
        }

        viewModel.allTasks.observe(viewLifecycleOwner) { taskList ->
            val scores = calculateDailyScoresThisWeek(taskList)
            barAdapter.updateData(taskList)
            binding.barGraph2.scoreBackground.setData(scores)


            // 📅 WEEK
            val today = LocalDate.now()
            val startSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val endSaturday = startSunday.plusDays(6)
            val weekEnd = if (today.isBefore(endSaturday)) today else endSaturday

            updateScoreUI(
                taskList,
                binding.scoreLayout.doneWeekWeight,
                binding.scoreLayout.cpiWeek,
                startSunday,
                weekEnd
            )

            // 📅 MONTH
            val firstDay = today.withDayOfMonth(1)
            val lastDay = today.withDayOfMonth(today.lengthOfMonth())
            val monthEnd = if (today.isBefore(lastDay)) today else lastDay

            updateScoreUI(
                taskList,
                binding.scoreLayout.doneMonthWeight,
                binding.scoreLayout.cpiMonth,
                firstDay,
                monthEnd
            )
        }
    }

//    private fun conditionWork() {
//        lifecycleScope.launch {
//            val dateEntity = viewModel.getDateItem(currentTodoDate)
//            if (dateEntity != null) {
//                conditionAdapter.updateDate(dateEntity.data)
//            } else {
//                conditionAdapter.updateDate(emptyList())
//            }
//        }
//    }

    private fun updateDayText(date: String) {
        val text = when {
            isTodayDate(date) -> getString(R.string.today)
            isTomorrowDate(date) -> getString(R.string.tomorrow)
            isYesterdayDate(date) -> getString(R.string.yesterday)
            else -> formatDate(date)
        }
        binding.scoreLayout.dayText.text = text
    }

    private fun observeViewModel() {

    }

//    override fun onDestroyView() {
//        super.onDestroyView()
//        backPressedCallback.remove()
//        _binding = null
//    }


    fun handleDeleteSelected() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isNotEmpty()) {
            val map = getDailyTaskMap(selectedItems)
            showDeleteDialog(map)
            adapter.clearSelection()
        }
    }

    fun getDailyTaskMap(selectedItems: Set<DailyTask>): Map<Boolean, List<DailyTask>> {
        return selectedItems.groupBy { it.isDaily }
    }

    private fun dateTextWork(currentTodoDate: String): String {
        return when (currentTodoDate) {
            CommonMethods.Companion.getTodayDate() -> {
                binding.nextDate.isEnabled = false
                binding.nextDateImage.visibility = View.INVISIBLE
                binding.dateTv.setBackgroundResource(R.drawable.circular_stroke_bg1)
                "Today"
            }

            CommonMethods.Companion.getYesterdayDate() -> {
                binding.nextDate.isEnabled = true
                binding.nextDateImage.visibility = View.VISIBLE
                binding.dateTv.setBackgroundResource(R.drawable.circular_stroke_bg2)
                "Yesterday"
            }

            else -> {
                binding.nextDate.isEnabled = true
                binding.nextDateImage.visibility = View.VISIBLE
                binding.dateTv.setBackgroundResource(R.drawable.circular_stroke_bg2)
                currentTodoDate
            }
        }
    }

    private fun calculateScore(tasks: List<DailyTask>): Float {
        if (tasks.isEmpty()) return 0f

        var totalWeight = 0
        var completedWeight = 0

        for (task in tasks) {
            totalWeight += task.weight.weight
            if (task.completedDates.contains(currentTodoDate)) {
                completedWeight += task.weight.weight
            }
        }

        if (totalWeight == 0) return 0f

        val rawScore = (completedWeight.toFloat() / totalWeight.toFloat()) * 10f
        return ((rawScore * 10).roundToInt()) / 10f
    }


    private fun updateScoreUI(
        tasks: List<DailyTask>,
        textView: TextView,
        progressBar: CircularProgressIndicator,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ) {
        val score = if (startDate != null && endDate != null) {
            // week/month ke liye
            calculateAggregateScore(tasks, startDate, endDate)
        } else {
            // sirf current date ke liye
            calculateScoreForDate(tasks, currentTodoDate)
        }

        textView.text = formatScore(score)
        progressBar.setProgress(score.roundToInt(), true)
    }


    private fun formatScore(value: Float): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()  // e.g. 7.0 -> "7"
        } else {
            String.format("%.1f", value)  // e.g. 7.36 -> "7.4"
        }
    }


    fun calculateDailyScoresThisWeek(tasks: List<DailyTask>): List<DailyScore> {
        val dailyScores = mutableListOf<DailyScore>()

        val today = LocalDate.now()
        // Start of the week (Sunday)
        val startSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        // End of the week (Saturday) — total 7 days (Sun to Sat)
        val endSaturday = startSunday.plusDays(6)

        var currentDate = startSunday
        while (!currentDate.isAfter(endSaturday)) {
            val dateString = currentDate.toString()
            val tasksUpToDate = filterTasks(tasks, dateString)

            var totalWeight = 0f
            var completedWeight = 0f
            for (task in tasksUpToDate) {
                totalWeight += task.weight.weight
                if (task.completedDates.contains(dateString)) {
                    completedWeight += task.weight.weight
                }
            }

            val score: Float = if (totalWeight > 0f) {
                ((completedWeight / totalWeight) * 10f * 10).roundToInt() / 10f
            } else {
                0f
            }

            // Get weekday short name: Sun, Mon, Tue...
            val weekDay = currentDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

            // Pass both dateString and weekDay
            dailyScores.add(DailyScore(dateString, weekDay, "",score, taskCount = tasksUpToDate.size))

            currentDate = currentDate.plusDays(1)
        }

        return dailyScores
    }

    fun getTasksForThisMonth(tasks: List<DailyTask>): List<DailyTask> {
        val today = LocalDate.now()
        val firstDay = today.withDayOfMonth(1)
        val lastDay = today.withDayOfMonth(today.lengthOfMonth())

        // ✅ Ensure loop ends at today (not after)
        val endDate = if (today.isBefore(lastDay)) today else lastDay

        val monthTasks = mutableSetOf<DailyTask>()
        var currentDate = firstDay
        while (!currentDate.isAfter(endDate)) {
            monthTasks.addAll(filterTasks(tasks, currentDate.toString()))
            currentDate = currentDate.plusDays(1)
        }

        return monthTasks.toList()
    }

    private fun calculateScoreForDate(tasks: List<DailyTask>, date: String): Float {
        if (tasks.isEmpty()) return 0f

        var totalWeight = 0
        var completedWeight = 0

        for (task in tasks) {
            totalWeight += task.weight.weight
            // ✅ Agar task daily hai toh uske completedDates check karo
            if (task.isDaily) {
                if (task.completedDates.contains(date)) {
                    completedWeight += task.weight.weight
                }
            } else {
                // Normal task ke liye single date check karo
                if (task.completedDates.contains(date)) {
                    completedWeight += task.weight.weight
                }
            }
        }

        if (totalWeight == 0) return 0f
        val rawScore = (completedWeight.toFloat() / totalWeight.toFloat()) * 10f
        return ((rawScore * 10).roundToInt()) / 10f
    }


    private fun calculateAggregateScore(
        tasks: List<DailyTask>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Float {
        val dailyScores = mutableListOf<Float>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dateStr = currentDate.toString()
            val filteredTasks = filterTasks(tasks, dateStr)
            val score = calculateScoreForDate(filteredTasks, dateStr)

            // ✅ Zero score ko ignore karna hai (jaise tumne bola tha)
            if (score > 0f) {
                dailyScores.add(score)
            }

            currentDate = currentDate.plusDays(1)
        }

        if (dailyScores.isEmpty()) return 0f
        return (dailyScores.sum() / dailyScores.size).let {
            ((it * 10).roundToInt()) / 10f
        }
    }


    fun formatDate(inputDate: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        return try {
            val date = inputFormat.parse(inputDate)
            if (date != null) outputFormat.format(date) else ""
        } catch (e: Exception) {
            ""
        }
    }

    operator fun LocalDate.rangeTo(other: LocalDate) = generateSequence(this) { it.plusDays(1) }
        .takeWhile { !it.isAfter(other) }

    fun showDeleteDialog(
        map: Map<Boolean, List<DailyTask>>
    ) {
        val map = map
        val dailyTasks = map[true] ?: emptyList()
        val nonDailyTasks = map[false] ?: emptyList()
        val hasDailyTasks = dailyTasks.isNotEmpty()
        var isRemoveCompletely = false
        val dialog = Dialog(requireContext())
        val continueDeleteView = layoutInflater.inflate(R.layout.dialog_delete_continue, null)
        val deleteWarningView = layoutInflater.inflate(R.layout.dialog_delete_warning, null)
        val continueBtn = continueDeleteView.findViewById<View>(R.id.btn_continue)
        val radioGroup = continueDeleteView.findViewById<RadioGroup>(R.id.radio_group)
        val deleteButton = deleteWarningView.findViewById<View>(R.id.deleteButton)
        val cancelButton = deleteWarningView.findViewById<View>(R.id.cancelButton)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            isRemoveCompletely = checkedId == R.id.radio_remove
        }
        deleteButton.setOnClickListener {
            if (hasDailyTasks) {
                if (isRemoveCompletely) {
                    Log.d(fragmentTag, "DailyTask: removing completely")
                    viewModel.deleteTasks(dailyTasks)
                } else {
                    Log.d(fragmentTag, "DailyTask: updating removedDate")
                    val updatedTasks = dailyTasks.map {
                        it.copy(taskRemovedDate = currentTodoDate)
                    }
                    viewModel.updateTasks(updatedTasks)
                }
            }

            if (nonDailyTasks.isNotEmpty()) {
                Log.d(fragmentTag, "NonDailyTask: removing completely")
                viewModel.deleteTasks(nonDailyTasks)
            }

            dialog.dismiss()
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        if (hasDailyTasks) {
            dialog.setContentView(continueDeleteView)
        } else {
            dialog.setContentView(deleteWarningView)
        }

        continueBtn.setOnClickListener { dialog.setContentView(deleteWarningView) }

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())//ColorDrawable(Color.TRANSPARENT)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }


}


