package com.anitech.growdaily.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.anitech.growdaily.fragment.DiaryFragment
import com.anitech.growdaily.fragment.RepeatTaskFragment
import com.anitech.growdaily.fragment.TaskFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()
    override fun getItemCount() = 2 // only 2 pages, no middle one
    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> TaskFragment()
            1 -> RepeatTaskFragment()
            else -> Fragment()
        }
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? = fragments[position]
}