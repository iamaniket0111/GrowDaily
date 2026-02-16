package com.anitech.growdaily.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.DailyTaskAdapter
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.AppViewModel
 import com.anitech.growdaily.databinding.FragmentRepeatTaskBinding
import kotlin.getValue

class RepeatTaskFragment : Fragment() {
    private var _binding: FragmentRepeatTaskBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var dailyTaskAdapter: DailyTaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentRepeatTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dailyTaskAdapter= DailyTaskAdapter(emptyList(),object :DailyTaskAdapter.OnItemClickListener{
            override fun moveToEditListener(task: TaskEntity) {
                val bundle = bundleOf("task" to task)
                requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.nav_add_task, bundle)
            }

            override fun onTaskCompleteClick(taskId: String, date: String) {
               //viewModel.toggleTaskCompletion(taskId,date)
             }
        })

        binding.analysisListRv.apply {
            layoutManager=LinearLayoutManager(requireContext())
            adapter = dailyTaskAdapter
        }

//        viewModel.heatmapUiList.observe(viewLifecycleOwner) { tasks ->
//            dailyTaskAdapter.updateList(tasks)
//        }
//
//        viewModel.getAllCompletionsTaskData().observe(viewLifecycleOwner) { tasks ->
//            Log.e("completedDatesSize", "fragment size:"+tasks.size.toString())
//
//        }


    }


}