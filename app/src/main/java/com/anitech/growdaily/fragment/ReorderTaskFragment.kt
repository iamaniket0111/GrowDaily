package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.graphics.Color
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.TaskReorderAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.data_class.ListTaskCrossRef
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.viewmodel.AppViewModel
import com.anitech.growdaily.databinding.FragmentReorderTaskBinding
import com.anitech.growdaily.dialog.TaskActionDialog
import com.anitech.growdaily.enum_class.TaskInactiveReason
import com.anitech.growdaily.enum_class.TaskType
import java.util.EnumSet

class ReorderTaskFragment : Fragment() {

    private var _binding: FragmentReorderTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()

    private var adapter: TaskReorderAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    private enum class TaskFilter { DAILY, DAY, PAUSED, ENDED }

    private val excludedStaticFilters = EnumSet.of(TaskFilter.DAY, TaskFilter.PAUSED, TaskFilter.ENDED)
    private val excludedListIds = mutableSetOf<String>()

    private var allTasksList: List<TaskEntity> = emptyList()
    private var allLists: List<ListEntity> = emptyList()
    private var allCrossRefs: List<ListTaskCrossRef> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReorderTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        setupEmptyState()
        setupStaticFilterChips()
        setupActionBarMenu()
        observeData()
        observeAccentColor()
        setupClickListeners()
    }

    private fun setupStaticFilterChips() {
        val chips = mapOf(
            binding.chipDaily to TaskFilter.DAILY,
            binding.chipDay to TaskFilter.DAY,
            binding.chipPaused to TaskFilter.PAUSED,
            binding.chipEnded to TaskFilter.ENDED,
        )

        chips.forEach { (view, filter) ->
            view.setOnClickListener {
                if (excludedStaticFilters.contains(filter)) {
                    excludedStaticFilters.remove(filter)
                } else {
                    excludedStaticFilters.add(filter)
                }
                updateStaticFilterUi()
                applyFilter()
            }
        }
    }

    private fun updateStaticFilterUi() {
        val accent = (requireActivity() as? MainActivity)?.accentColor?.value ?: Color.BLUE
        val surface = ContextCompat.getColor(requireContext(), R.color.task_chip_surface)
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.task_text_secondary)

        val chips = listOf(
            binding.chipDaily to TaskFilter.DAILY,
            binding.chipDay to TaskFilter.DAY,
            binding.chipPaused to TaskFilter.PAUSED,
            binding.chipEnded to TaskFilter.ENDED,
        )

        chips.forEach { (view, filter) ->
            val isExcluded = excludedStaticFilters.contains(filter)
            view.backgroundTintList = ColorStateList.valueOf(
                if (isExcluded) ColorUtils.setAlphaComponent(accent, 40) else surface
            )
            view.setTextColor(if (isExcluded) accent else textSecondary)
        }
    }

    private fun updateDynamicListChips() {
        val accent = (requireActivity() as? MainActivity)?.accentColor?.value ?: Color.BLUE
        val surface = ContextCompat.getColor(requireContext(), R.color.task_chip_surface)
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.task_text_secondary)

        val container = binding.filterScroll.getChildAt(0) as ViewGroup
        val staticChipIds = setOf(
            R.id.chipScheduled, R.id.chipDaily, R.id.chipDay,
            R.id.chipPaused, R.id.chipEnded
        )

        for (i in (container.childCount - 1) downTo 0) {
            val view = container.getChildAt(i)
            if (view.id !in staticChipIds) {
                container.removeViewAt(i)
            }
        }

        allLists.forEach { list ->
            val chip = LayoutInflater.from(requireContext()).inflate(
                R.layout.layout_filter_chip_item,
                container,
                false
            ) as TextView
            chip.text = list.listTitle

            val isExcluded = excludedListIds.contains(list.id)
            chip.backgroundTintList = ColorStateList.valueOf(
                if (isExcluded) ColorUtils.setAlphaComponent(accent, 40) else surface
            )
            chip.setTextColor(if (isExcluded) accent else textSecondary)

            chip.setOnClickListener {
                if (excludedListIds.contains(list.id)) {
                    excludedListIds.remove(list.id)
                } else {
                    excludedListIds.add(list.id)
                }
                updateDynamicListChips()
                applyFilter()
            }

            container.addView(chip)
        }
    }

    private fun setupClickListeners() {
        binding.btnDone.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupActionBarMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.add(Menu.NONE, R.id.menu_reset_order, Menu.NONE, R.string.reset_button).apply {
                        setIcon(R.drawable.ic_restore)
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.menu_reset_order -> {
                            resetToTimeOrder()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun resetToTimeOrder() {
        if (allTasksList.isEmpty()) return

        val accent = (requireActivity() as? MainActivity)?.accentColor?.value ?: Color.BLUE

        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.reset_order_title),
            message = getString(R.string.reset_order_message),
            primaryLabel = getString(R.string.reset_confirm_button),
            secondaryLabel = getString(R.string.cancel_button),
            iconRes = R.drawable.ic_restore,
            accentColor = accent,
            iconBubbleColor = ColorUtils.setAlphaComponent(accent, 40),
            onPrimaryAction = {
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

                val tasksWithScheduledMinutes = allTasksList.filter { it.scheduledMinutes != null }
                    .sortedBy { it.scheduledMinutes }

                val tasksWithoutScheduledMinutes = allTasksList.filter { it.scheduledMinutes == null }
                    .sortedBy { it.manualOrder }

                val reordered = tasksWithScheduledMinutes + tasksWithoutScheduledMinutes
                val orderedIds = reordered.map { it.id }

                viewModel.updateManualOrder(orderedIds)
            }
        ).show()
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            binding.btnDone.backgroundTintList = ColorStateList.valueOf(color)
            updateStaticFilterUi()
            updateDynamicListChips()
        }
    }

    private fun setupRecycler() {
        adapter = TaskReorderAdapter(
            mutableListOf(),
            { vh ->
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                itemTouchHelper?.startDrag(vh)
            },
            object : TaskReorderAdapter.OnReorderCompleteListener {
                override fun onReorderComplete(orderedTaskIds: List<String>) {
                    viewModel.updateManualOrder(orderedTaskIds)
                }
            }
        )

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter?.moveItem(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                )
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    (viewHolder as? TaskReorderAdapter.ViewHolder)?.onItemSelected()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                (viewHolder as? TaskReorderAdapter.ViewHolder)?.onItemClear()
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                adapter?.notifyReorderFinished()
            }
        }

        val touchHelper = ItemTouchHelper(callback)
        itemTouchHelper = touchHelper
        touchHelper.attachToRecyclerView(binding.taskReorderRv)
        binding.taskReorderRv.layoutManager = LinearLayoutManager(requireContext())
        binding.taskReorderRv.adapter = adapter
    }

    private fun observeData() {
        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            allTasksList = tasks
            applyFilter()
        }
        viewModel.allLists.observe(viewLifecycleOwner) { lists ->
            allLists = lists
            updateDynamicListChips()
            applyFilter()
        }
        viewModel.allListTaskCrossRefs.observe(viewLifecycleOwner) { refs ->
            allCrossRefs = refs
            applyFilter()
        }
    }

    private fun applyFilter() {
        val excludedTaskIdsByList = allCrossRefs
            .filter { excludedListIds.contains(it.listId) }
            .map { it.taskId }
            .toSet()

        val filtered = allTasksList.filter { task ->
            if (task.isScheduled) return@filter true

            if (excludedTaskIdsByList.contains(task.id)) return@filter false

            val isExcluded = when {
                task.taskType == TaskType.DAILY && task.inactiveReason == null ->
                    excludedStaticFilters.contains(TaskFilter.DAILY)
                task.taskType == TaskType.DAY || task.taskType == TaskType.UNTIL_COMPLETE ->
                    excludedStaticFilters.contains(TaskFilter.DAY)
                task.inactiveReason == TaskInactiveReason.PAUSED ->
                    excludedStaticFilters.contains(TaskFilter.PAUSED)
                task.inactiveReason == TaskInactiveReason.ENDED ->
                    excludedStaticFilters.contains(TaskFilter.ENDED)
                else -> false
            }
            !isExcluded
        }

        TransitionManager.beginDelayedTransition(binding.root, AutoTransition())

        if (filtered.isEmpty()) {
            adapter?.updateList(emptyList())
            binding.taskReorderRv.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            val orderedTasks = CommonMethods.applySmartTimeOrder(filtered)
            adapter?.updateList(orderedTasks)
            binding.taskReorderRv.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }

    private fun setupEmptyState() {
        binding.ivEmptyStateImage.setImageResource(R.drawable.thinking_mode)
        binding.tvEmptyStateTitle.setText(R.string.reorder_empty_title)
        binding.tvEmptyStateSubtitle.setText(R.string.reorder_empty_subtitle)
    }

    override fun onDestroyView() {
        if (_binding != null) {
            binding.taskReorderRv.adapter = null
        }
        itemTouchHelper?.attachToRecyclerView(null)
        super.onDestroyView()
        _binding = null
        adapter = null
        itemTouchHelper = null
    }
}
