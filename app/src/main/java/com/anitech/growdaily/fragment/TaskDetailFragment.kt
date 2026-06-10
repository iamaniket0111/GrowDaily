package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.databinding.FragmentTaskDetailBinding
import com.google.android.material.tabs.TabLayoutMediator

class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!

    private val args: TaskDetailFragmentArgs by navArgs()

    private lateinit var repository: AppRepository
    private var loadedTask: TaskEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as MyApp).repository

        setupMenu()

        val taskId = args.taskId
        val taskFromArgs = args.task
        if (taskFromArgs != null) {
            loadedTask = taskFromArgs
            setupViewPager(taskId, taskFromArgs)
        } else {
            repository.getTaskById(taskId).observe(viewLifecycleOwner) { task ->
                if (task != null && loadedTask == null) {
                    loadedTask = task
                    setupViewPager(taskId, task)
                }
            }
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        val addTaskFragment = childFragmentManager.fragments
                            .firstOrNull { it is AddTaskFragment } as? AddTaskFragment
                        if (addTaskFragment != null) {
                            addTaskFragment.attemptClose()
                        } else {
                            findNavController().popBackStack()
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupViewPager(taskId: String, task: TaskEntity) {
        val adapter = TaskDetailPagerAdapter(this, taskId, task)
        binding.viewPagerTaskDetail.adapter = adapter

        TabLayoutMediator(binding.tabLayoutTaskDetail, binding.viewPagerTaskDetail) { tab, position ->
            tab.text = if (position == 0) "Analysis" else "Edit"
        }.attach()

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = task.title

        val accentColor = com.anitech.growdaily.enum_class.TaskColor.fromName(task.colorCode)
            ?.toColorInt(requireContext())
            ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)

        binding.tabLayoutTaskDetail.setSelectedTabIndicatorColor(accentColor)
        binding.tabLayoutTaskDetail.setTabTextColors(
            ContextCompat.getColor(requireContext(), R.color.task_text_secondary),
            accentColor
        )
    }

    fun switchToEditTab() {
        if (_binding != null) {
            binding.viewPagerTaskDetail.currentItem = 1
        }
    }

    fun isEditTabActive(): Boolean {
        return _binding != null && binding.viewPagerTaskDetail.currentItem == 1
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class TaskDetailPagerAdapter(
        fragment: Fragment,
        private val taskId: String,
        private val task: TaskEntity
    ) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                AnalysisRepeatTaskFragment().apply {
                    arguments = Bundle().apply {
                        putString("taskId", taskId)
                    }
                }
            } else {
                AddTaskFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable("task", task)
                        putString("taskType", task.taskType.name)
                    }
                }
            }
        }
    }
}
