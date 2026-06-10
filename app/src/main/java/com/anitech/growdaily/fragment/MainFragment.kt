package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ViewPagerAdapter
import com.anitech.growdaily.databinding.FragmentMainBinding
import com.anitech.growdaily.dialog.TaskTypeDialog
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskType

class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        observeAccentColor()
        setupTaskTypeDialogResult()

        binding.fab.setImageResource(R.drawable.ic_add) // Home icon
        binding.fab.setOnClickListener {
            TaskTypeDialog().show(parentFragmentManager, "TaskTypeDialog")
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    binding.viewPager.currentItem = 0
                    true
                }

                R.id.repeatTaskFragment -> {
                    binding.viewPager.currentItem = 1
                    true
                }

                R.id.empty_space -> false
                else -> false
            }
        }

        // Sync ViewPager with BottomNav + handle FAB
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {

                    0 -> {
                        binding.bottomNav.selectedItemId = R.id.homeFragment
                    }

                    1 -> {
                        binding.bottomNav.selectedItemId = R.id.repeatTaskFragment
                    }
                }
                // Notify Activity to refresh menu visibility based on active page
                requireActivity().invalidateOptionsMenu()
            }
        })


        CommonMethods.getTodayDate()
    }

    fun getCurrentFragment(): Fragment? {
        val adapter = binding.viewPager.adapter as? ViewPagerAdapter ?: return null
        return adapter.getFragment(binding.viewPager.currentItem)
    }

    fun isTaskPageActive(): Boolean {
        return _binding != null && binding.viewPager.currentItem == 0
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            updateUiWithAccentColor(color)
        }
    }

    private fun updateUiWithAccentColor(color: Int) = with(binding) {
        fab.backgroundTintList = ColorStateList.valueOf(color)
        
        val navItemColor = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(color, ContextCompat.getColor(requireContext(), R.color.main_bottom_bar_icon))
        )
        bottomNav.itemIconTintList = navItemColor
        bottomNav.itemBackground = createBottomNavItemBackground(color)
    }

    private fun createBottomNavItemBackground(color: Int): StateListDrawable {
        val radius = resources.displayMetrics.density * 16f
        val selectedBg = ColorUtils.setAlphaComponent(color, (255 * 0.26f).toInt())
        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(selectedBg)
        }
        val selected = LayerDrawable(arrayOf(pill)).apply {
            val width = (resources.displayMetrics.density * 56).toInt()
            val height = (resources.displayMetrics.density * 32).toInt()
            setLayerGravity(0, Gravity.CENTER)
            setLayerWidth(0, width)
            setLayerHeight(0, height)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), selected)
            addState(intArrayOf(), ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    private fun setupTaskTypeDialogResult() {
        parentFragmentManager.setFragmentResultListener(
            TaskTypeDialog.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val selectedType = bundle.getString(TaskTypeDialog.TASK_TYPE)
                ?.let { runCatching { TaskType.valueOf(it) }.getOrNull() }
                ?: return@setFragmentResultListener

            val action = MainFragmentDirections
                .actionMainToAddTask(task = null, taskType = selectedType.name)
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        if (_binding != null) {
            binding.viewPager.adapter = null
        }
        super.onDestroyView()
        _binding = null
    }
}
