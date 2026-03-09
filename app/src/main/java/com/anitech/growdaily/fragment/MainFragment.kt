package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ViewPagerAdapter
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentMainBinding
import com.anitech.growdaily.dialog.TaskTypeDialog

class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ViewPagerAdapter(requireActivity())
        binding.viewPager.adapter = adapter

        binding.fab.setImageResource(R.drawable.ic_add) // Home icon
        binding.fab.setOnClickListener {
            TaskTypeDialog { selectedType ->

                val action = MainFragmentDirections
                    .actionMainToAddTask(
                        task = null,
                        taskType = selectedType.name
                    )

                findNavController().navigate(action)
            }.show(parentFragmentManager, "TaskTypeDialog")
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
            }
        })


        CommonMethods.getTodayDate()
        //val today ="2025-10-09"

//        viewModel.getTodaysMoodLive(today).observe(viewLifecycleOwner) { mood ->
//            if (mood != null) {
//                binding.moodFabBtn.visibility = View.GONE
//                binding.moodFabBtn.isClickable = false
//            } else {
//                binding.moodFabBtn.visibility = View.VISIBLE
//                binding.moodFabBtn.isClickable = true
//                val rotate = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_fab)
//                binding.moodFabBtn.startAnimation(rotate)
//                binding.moodFabBtn.setOnClickListener {
//                    val moodHistoryItem = MoodHistoryItem(
//                        id = 0,
//                        emoji = "😐",
//                        date = today
//                    )
//                    MoodDialog(
//                        requireContext(),
//                        moodHistoryItem,
//                        object : MoodDialog.OnMoodSelectedListener {
//                            override fun onMoodSelected(updatedMood: MoodHistoryItem) {
//                                viewModel.insertMood(updatedMood)
//                            }
//                        }).show()
//                }
//            }
//        }
    }


    fun getCurrentFragment(): Fragment? {
        val adapter = binding.viewPager.adapter as? ViewPagerAdapter ?: return null
        return adapter.getFragment(binding.viewPager.currentItem)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
