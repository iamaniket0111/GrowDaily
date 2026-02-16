package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.TaskForConditionAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentManageConditionBinding
import java.util.UUID

class ManageConditionFragment : Fragment() {

    private var _binding: FragmentManageConditionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private val args: ManageConditionFragmentArgs by navArgs()

    private lateinit var adapter: TaskForConditionAdapter

    // temp selected state
    private val tempSelectedTaskIds = mutableSetOf<String>()

    private lateinit var listId: String
    private var isEditMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageConditionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val condition = args.ConditionEntity

        if (condition == null) {
            // ADD MODE
            isEditMode = false
            listId = UUID.randomUUID().toString()

            binding.defName.text = "New List"
            binding.infoText.text =
                getString(R.string.condition_message, "New List")

        } else {
            // EDIT MODE
            isEditMode = true
            listId = condition.id

            binding.defName.text = condition.listTitle
            binding.edListName.setText(condition.listTitle)
            binding.infoText.text =
                getString(R.string.condition_message, condition.listTitle)

            // load existing selected tasks
//            viewModel.getTaskIdsForList(listId) { ids ->
//                tempSelectedTaskIds.clear()
//                tempSelectedTaskIds.addAll(ids)
//                adapter.notifyDataSetChanged()
//            }

        }

        setupRecycler()
        observeAllTasks()
        setupSaveButton()
    }

    private fun setupRecycler() {
        adapter = TaskForConditionAdapter(
            allTasks = emptyList(),
            selectedTaskIds = tempSelectedTaskIds,
            listener = object : TaskForConditionAdapter.OnItemClickListener {

                override fun onTaskSelected(taskId: String) {
                    tempSelectedTaskIds.add(taskId)
                }

                override fun onTaskUnSelected(taskId: String) {
                    tempSelectedTaskIds.remove(taskId)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun observeAllTasks() {
//        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
//            adapter.submitList(tasks)
//        }
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {

            val finalName =
                binding.edListName.text.toString().trim()
                    .ifEmpty { binding.defName.text.toString() }

            val listEntity = ListEntity(
                id = listId,
                listTitle = finalName,
                sortOrder = 0
            )

//            if (isEditMode) {
//                viewModel.updateList(listEntity)
//            } else {
//                viewModel.insertList(listEntity)
//            }

            // save task relations
//            viewModel.saveTasksForList(
//                listId = listId,
//                taskIds = tempSelectedTaskIds.toList()
//            )


            Toast.makeText(requireContext(), "Saved successfully", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
