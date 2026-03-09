package com.anitech.growdaily.fragment

import android.os.Bundle
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

    private lateinit var adapter: TaskReorderAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReorderTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        observeTasks()
    }

    private fun setupRecycler() {

        adapter = TaskReorderAdapter(
            mutableListOf(),
            { vh -> itemTouchHelper.startDrag(vh) },
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
                adapter.moveItem(
                    viewHolder.adapterPosition,
                    target.adapterPosition
                )
                return true
            }

            override fun onSwiped(
                viewHolder: RecyclerView.ViewHolder,
                direction: Int
            ) {
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)


                adapter.notifyReorderFinished()
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.taskReorderRv)

        binding.taskReorderRv.layoutManager =
            LinearLayoutManager(requireContext())

        binding.taskReorderRv.adapter = adapter
    }

    private fun observeTasks() {

        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            // Reorder screen me sirf manual order follow kare
            val orderedTasks =
                CommonMethods.applySmartTimeOrder(tasks)

            adapter.updateList(orderedTasks)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
