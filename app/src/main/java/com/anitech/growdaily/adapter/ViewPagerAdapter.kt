package com.anitech.growdaily.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.anitech.growdaily.fragment.RepeatTaskFragment
import com.anitech.growdaily.fragment.TaskFragment

class ViewPagerAdapter(private val fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = 2 // only 2 pages, no middle one
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TaskFragment()
            1 -> RepeatTaskFragment()
            else -> Fragment()
        }
    }

    /**
     * Retrieves the fragment at the given position.
     * FragmentStateAdapter uses the tag "f" + position internally.
     */
    fun getFragment(position: Int): Fragment? {
        return fragment.childFragmentManager.findFragmentByTag("f$position")
    }
}
