package com.anitech.growdaily.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.DailyTaskAdapter
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.RepeatTaskViewModel
import com.anitech.growdaily.database.RepeatTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentRepeatTaskBinding

class RepeatTaskFragment : Fragment() {

    private var _binding: FragmentRepeatTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RepeatTaskViewModel by viewModels {
        RepeatTaskViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    private lateinit var dailyTaskAdapter: DailyTaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dailyTaskAdapter = DailyTaskAdapter(
            emptyList(),
            object : DailyTaskAdapter.OnItemClickListener {
                override fun moveToEditListener(task: TaskEntity) {
                    val bundle = bundleOf("taskId" to task.id)
                    findNavController().navigate(R.id.analysisRepeatTaskFragment, bundle)
                }

                override fun onTaskCompleteClick(taskId: String, date: String) {
                    // handle toggle if needed
                }
            }
        )

        binding.analysisListRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = dailyTaskAdapter
        }

        viewModel.heatmapUiList.observe(viewLifecycleOwner) { tasks ->
            dailyTaskAdapter.updateList(tasks)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}