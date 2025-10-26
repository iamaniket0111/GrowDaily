package com.anitech.growdaily.fragment

import android.os.Bundle
import android.util.Log  // 👇 Debug ke liye add kar if not there
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.adapter.TaskReorderAdapter
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentReorderTaskBinding

class ReorderTaskFragment : Fragment() {
    private var _binding: FragmentReorderTaskBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    var currentTodoDate: String = CommonMethods.Companion.getTodayDate()
    lateinit var adapter: TaskReorderAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentReorderTaskBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false

            // 👇 After user stops dragging, auto-fix time order
            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                adapter.autoReorderByTime()  // Ye chalega, phir listener fire hoga adapter me
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.taskReorderRv)

        adapter = TaskReorderAdapter(
            mutableListOf(),
            { vh -> itemTouchHelper.startDrag(vh) },
            object : TaskReorderAdapter.OnReorderCompleteListener {  // 👇 Naya: Logging callback
                override fun onReorderComplete(orderedTaskIds: List<String>) {
                    Log.d("FragmentDebug", "Reorder complete, logging ${orderedTaskIds.size} IDs")
                    // Effective date current ya future – abhi current use kar
                    val effectiveDate = currentTodoDate  // Ya dialog se le agar chahiye
                    viewModel.logTaskReorder(effectiveDate, orderedTaskIds)
                }
            }
        )
        binding.taskReorderRv.layoutManager = LinearLayoutManager(requireContext())
        binding.taskReorderRv.adapter = adapter
        viewModel.setDate(currentTodoDate)

        // 👇 Observe me debug logs add kiye – data flow check ke liye
        viewModel.filteredTasksByCondition.observe(viewLifecycleOwner) { tasks ->
            Log.d("FragmentDebug", "Observed tasks: ${tasks.size}")
            if (tasks.isNotEmpty()) {
                adapter.updateList(tasks)
                // Optional: Initial auto-reorder if ViewModel ordering enough nahi
                // adapter.autoReorderByTime()  // Comment out kar de if not needed
            } else {
                Log.w("FragmentDebug", "Empty tasks observed – check DB/filter!")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}