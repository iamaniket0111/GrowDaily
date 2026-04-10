package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.RepeatTaskAdapter
import com.anitech.growdaily.data_class.TaskCompletionEntity
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.util.resolveTrackingSettings
import com.anitech.growdaily.database.viewmodel.RepeatTaskViewModel
import com.anitech.growdaily.database.viewmodel.RepeatTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentRepeatTaskBinding
import com.anitech.growdaily.dialog.CompletionInputDialog
import com.anitech.growdaily.enum_class.CompletionAction
import com.anitech.growdaily.enum_class.TrackingType
import kotlinx.coroutines.launch
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
                    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
                    val item = uiList.find {
                        it.task.id == taskId || it.taskIdByDate[parsedDate] == taskId
                    } ?: return

                    val task       = item.task
                    val settings   = resolveTrackingSettings(task, date, item.trackingVersions)
                    val count      = item.completionByDate[parsedDate]?.count ?: 0
                    val target     = settings.dailyTargetCount.coerceAtLeast(1)

                    when (task.trackingType) {

                        TrackingType.BINARY -> {
                            // Simple toggle — no dialog
                            viewModel.onHistoryCellClick(taskId, date)
                        }

                        TrackingType.COUNT -> {
                            if (count >= target) {
                                viewModel.onHistoryCellClick(taskId, date)
                            } else {
                                val existing = TaskCompletionEntity(taskId, date, count = count)
                                CompletionInputDialog(
                                    task = task,
                                    date = date,
                                    currentCompletion = existing,
                                    trackingSettingsOverride = settings
                                ) { action ->
                                    when (action) {
                                        is CompletionAction.CountDelta ->
                                            viewModel.changeTaskCompletionBy(taskId, date, action.delta)

                                        else -> Unit
                                    }
                                }.show(parentFragmentManager, "completionDialog")
                            }
                        }

                        TrackingType.TIMER -> {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val existing = (requireActivity().application as MyApp)
                                    .repository.completionDao
                                    .isTaskCompletedOnDate(taskId, date)
                                    ?: TaskCompletionEntity(taskId = taskId, date = date)

                                CompletionInputDialog(
                                    task = task,
                                    date = date,
                                    currentCompletion = existing,
                                    trackingSettingsOverride = settings
                                ) { action ->
                                    when (action) {
                                        is CompletionAction.TimerAdd ->
                                            viewModel.addTimerDuration(taskId, date, action.seconds)
                                        else -> Unit
                                    }
                                }.show(parentFragmentManager, "completionDialog")
                            }
                        }

                        TrackingType.CHECKLIST -> {
                            // Fetch existing checklist JSON from DB then open dialog
                            viewLifecycleOwner.lifecycleScope.launch {
                                val repository = (requireActivity().application as MyApp).repository
                                val existing = repository.completionDao
                                    .isTaskCompletedOnDate(taskId, date)
                                val checklistItemsForDate = settings.checklistItemsJson ?: task.checklistItems
                                CompletionInputDialog(
                                    task = task,
                                    date = date,
                                    currentCompletion = existing,
                                    trackingSettingsOverride = settings,
                                    checklistItemsOverride = checklistItemsForDate
                                ) { action ->
                                    when (action) {
                                        is CompletionAction.ChecklistUpdate ->
                                            viewModel.updateChecklist(taskId, date, action.json)
                                        else -> Unit
                                    }
                                }.show(parentFragmentManager, "completionDialog")
                            }
                        }
                    }
                }}
        )

        binding.analysisListRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = repeatTaskAdapter
            itemAnimator = null
        }

        binding.emptyState.findViewById<View>(R.id.ivEmptyStateImage)?.let { imageView ->
            (imageView as? android.widget.ImageView)?.setImageResource(R.drawable.ic_personal_goals)
        }
        binding.emptyState.findViewById<View>(R.id.tvEmptyStateTitle)?.let { titleView ->
            (titleView as? android.widget.TextView)?.text = "No repeat tasks yet"
        }
        binding.emptyState.findViewById<View>(R.id.tvEmptyStateSubtitle)?.let { subtitleView ->
            (subtitleView as? android.widget.TextView)?.text =
                "Create a repeating task to track it over time."
        }

        showLoading()   // spinner visible until first data arrives

        viewModel.heatmapUiList.observe(viewLifecycleOwner) { tasks ->
            when {
                tasks.isNullOrEmpty() -> {
                    repeatTaskAdapter.submitList(emptyList())
                    showEmpty()
                }
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
