package com.anitech.growdaily.fragment

import android.os.Bundle
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
import com.anitech.growdaily.adapter.RepeatTaskAdapter
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.RepeatTaskViewModel
import com.anitech.growdaily.database.RepeatTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentRepeatTaskBinding
import com.anitech.growdaily.dialog.ResetCompletionBottomSheet
import java.time.LocalDate

class RepeatTaskFragment : Fragment() {

    private var _binding: FragmentRepeatTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RepeatTaskViewModel by viewModels {
        RepeatTaskViewModelFactory(
            (requireActivity().application as MyApp).repository
        )
    }

    private lateinit var repeatTaskAdapter: RepeatTaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repeatTaskAdapter = RepeatTaskAdapter(
            object : RepeatTaskAdapter.OnItemClickListener {
                override fun moveToEditListener(task: TaskEntity) {
                    val bundle = bundleOf("taskId" to task.id)
                    findNavController().navigate(R.id.analysisRepeatTaskFragment, bundle)
                }
                override fun onTaskCompleteClick(taskId: String, date: String) {
                    val uiList = viewModel.heatmapUiList.value ?: return
                    val item = uiList.find { it.task.id == taskId } ?: return
                    val target = item.task.dailyTargetCount.coerceAtLeast(1)

                    val parsedDate = LocalDate.parse(date)
                    val count = item.completedDates[parsedDate] ?: 0

                    if (target > 1 && count >= target) {
                        // same bottom sheet you use in TaskFragment
                        ResetCompletionBottomSheet {
                            viewModel.onHistoryCellClick(taskId, date)
                        }.show(parentFragmentManager, "resetSheet")
                    } else {
                        viewModel.onHistoryCellClick(taskId, date)
                    }
                }
            }
        )

        binding.analysisListRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = repeatTaskAdapter
            itemAnimator = null
        }

        showLoading()   // spinner visible until first data arrives

        viewModel.heatmapUiList.observe(viewLifecycleOwner) { tasks ->
            when {
                tasks.isNullOrEmpty() -> showEmpty()
                else -> {
                    repeatTaskAdapter.submitList(tasks)
                    showList()
                }
            }
        }
    }

    // ── state helpers ─────────────────────────────────────────────────────────

    private fun showLoading() = with(binding) {
        loadingState.visibility   = View.VISIBLE
        analysisListRv.visibility = View.GONE
        emptyState.visibility     = View.GONE
    }

    private fun showEmpty() = with(binding) {
        loadingState.visibility   = View.GONE
        analysisListRv.visibility = View.GONE
        emptyState.visibility     = View.VISIBLE
    }

    private fun showList() = with(binding) {
        loadingState.visibility   = View.GONE
        analysisListRv.visibility = View.VISIBLE
        emptyState.visibility     = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}