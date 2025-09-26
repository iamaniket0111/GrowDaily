package com.anitech.scoremyday.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.viewpager2.widget.ViewPager2
import com.anitech.scoremyday.R
import com.anitech.scoremyday.adapter.ViewPagerAdapter
import com.anitech.scoremyday.databinding.FragmentDiaryBinding
import com.anitech.scoremyday.databinding.FragmentHomeBinding
import com.anitech.scoremyday.databinding.FragmentMainBinding

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

        val adapter = ViewPagerAdapter(requireActivity())
        binding.viewPager.adapter = adapter

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.diaryFragment -> {
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

                //fixme: is this below clock of code irrilevant
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.nav_main, inclusive = false) // ya true depending on behavior
                    .build()
                //todo: remove above code

                if (position != 0) {
                    val adapter = binding.viewPager.adapter
                    if (adapter is ViewPagerAdapter) {
                        val homeFragment = adapter.getFragment(0)
                        if (homeFragment is HomeFragment) {
                            if (homeFragment.adapter.selectionCount() > 0) {
                                homeFragment.adapter.clearSelection()
                            }
                        }
                    }
                }

                when (position) {

                    0 -> {
                        binding.bottomNav.selectedItemId = R.id.homeFragment
                        binding.fab.setImageResource(R.drawable.ic_add) // Home icon
                        binding.fab.setOnClickListener {
                            findNavController().navigate(R.id.nav_add_task)
                        }

                    }
                    1 -> {
                        binding.bottomNav.selectedItemId = R.id.diaryFragment
                        binding.fab.setImageResource(R.drawable.ic_write_diary) // Diary icon
                        binding.fab.setOnClickListener {
                            findNavController().navigate(R.id.nav_add_diary)
                        }

                    }
                }
            }
        })

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )

    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.viewPager.currentItem == 0) {
                val homeFragment = (binding.viewPager.adapter as ViewPagerAdapter)
                    .getFragment(0) as? HomeFragment
                if ((homeFragment?.adapter?.selectionCount() ?: 0) > 0) {
                    homeFragment?.adapter?.clearSelection()
                } else {
                    requireActivity().finish()
                }
            } else {
                if (!findNavController().popBackStack()) {
                    requireActivity().finish()
                }
            }

        }
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
