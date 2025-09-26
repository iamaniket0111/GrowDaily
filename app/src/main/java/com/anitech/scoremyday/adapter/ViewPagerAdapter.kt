package com.anitech.scoremyday.adapter

import android.R.attr.fragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.anitech.scoremyday.fragment.DiaryFragment
import com.anitech.scoremyday.fragment.HomeFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()
    override fun getItemCount() = 2 // only 2 pages, no middle one
    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> HomeFragment()
            1 -> DiaryFragment()
            else -> Fragment()
        }
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? = fragments[position]
}
