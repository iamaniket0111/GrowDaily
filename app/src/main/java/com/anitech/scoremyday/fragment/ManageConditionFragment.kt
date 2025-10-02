package com.anitech.scoremyday.fragment

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
import com.anitech.scoremyday.R
import com.anitech.scoremyday.adapter.TaskForConditionAdapter
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.database.AppViewModel
import com.anitech.scoremyday.databinding.FragmentManageConditionBinding
import kotlin.getValue

class ManageConditionFragment : Fragment() {
    private var _binding: FragmentManageConditionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private val args: ManageConditionFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageConditionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argCondition = args.ConditionEntity
        val conditionName = argCondition.conditionTitle
        binding.defName.text = conditionName
        val message = getString(R.string.condition_message, conditionName)
        binding.infoText.text = message


        val taskForConditionAdapter = TaskForConditionAdapter(
            emptyList(),
            conditionId = argCondition.id,
            listener = object : TaskForConditionAdapter.OnItemClickListener {
                override fun onTaskCompleteClick(task: DailyTask) {
                    viewModel.updateTask(task)
                }
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskForConditionAdapter
        }

        viewModel.getAllDailyTasks().observe(viewLifecycleOwner) { tasks ->
            taskForConditionAdapter.updateList(tasks)
        }
        viewModel.getTasksByCondition(argCondition.id).observe(viewLifecycleOwner) { tasks ->
            taskForConditionAdapter.updateConditionList(tasks)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
