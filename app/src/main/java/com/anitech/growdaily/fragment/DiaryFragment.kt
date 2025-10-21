package com.anitech.growdaily.fragment

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.MoodDialog
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.DateAdapter
import com.anitech.growdaily.adapter.DiaryAdapter
import com.anitech.growdaily.adapter.MoodAdapter
import com.anitech.growdaily.adapter.WeekMoodAdapter
import com.anitech.growdaily.data_class.DayLogEntity
import com.anitech.growdaily.data_class.MoodHistoryItem
import com.anitech.growdaily.data_class.MoodItem
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentDiaryBinding
import java.time.LocalDate

class DiaryFragment : Fragment() {
    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var diaryAdapter: DiaryAdapter
    private lateinit var diaryLayoutManager: LinearLayoutManager
    private var positionsByDate: Map<String, Int> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //dates
        val today = LocalDate.now()
        val datesAdapter = DateAdapter(today, 365, object : DateAdapter.OnItemClickListener {

            override fun addIntoDiary(date: LocalDate) {
                val bundle = bundleOf("date" to date.toString())
                requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.nav_add_diary, bundle)
            }

            override fun scrollToDiary(date: LocalDate) {
                Toast.makeText(requireContext(), "$date", Toast.LENGTH_SHORT).show()
                scrollDiaryToDate(date)
            }
        })

        binding.calenderRv.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, true)

            adapter = datesAdapter

            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    rv.parent.requestDisallowInterceptTouchEvent(true) // Parent (ViewPager2) ko rok deta hai
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }

        //diary
        diaryAdapter = DiaryAdapter(emptyList(), object : DiaryAdapter.OnItemClickListener {
            override fun moveToEditListener(diaryEntry: DayLogEntity) {
                if (diaryEntry.diaryId != null) {
                    val bundle = bundleOf("diary" to diaryEntry)
                    requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                        .navigate(R.id.nav_add_diary, bundle)
                } else {
                    requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                        .navigate(R.id.nav_add_diary)
                }
            }
        })

        diaryLayoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDiary.apply {
            layoutManager = diaryLayoutManager
            isNestedScrollingEnabled = false
            adapter = diaryAdapter
        }

        viewModel.dayLogs.observe(viewLifecycleOwner) { entries ->
            // Empty state handle karo
            binding.noDayTaskLayoutContainer.visibility =
                if (entries.isEmpty()) View.VISIBLE else View.GONE

            diaryAdapter.updateData(entries)

            val uniqueDates = entries.map { it.date }.distinct().sortedDescending()
            datesAdapter.updateData(uniqueDates)

            positionsByDate = entries.mapIndexed { index, entry ->
                entry.date to index
            }.toMap()

            Log.d("DiaryFragment", "DayLogs size: ${entries.size}, Dates: ${uniqueDates.size}")
        }

        //mood
        val moodAdapter =
            WeekMoodAdapter(emptyList(), object : WeekMoodAdapter.OnItemClickListener {
                override fun onMoodItemClick(moodHistoryItem: MoodHistoryItem) {
                    Log.d(
                        "WeekMoodAdapter",
                        "onMoodItemClick: ${moodHistoryItem.date} : ${moodHistoryItem.emoji}"
                    )

                    MoodDialog(
                        requireContext(),
                        moodHistoryItem,
                        object : MoodDialog.OnMoodSelectedListener {
                            override fun onMoodSelected(updatedMood: MoodHistoryItem) {
                                if (updatedMood.id == 0) {
                                    viewModel.insertMood(updatedMood)
                                } else {
                                    viewModel.updateMood(updatedMood)
                                }
                            }
                        }).show()

                }
            })

        viewModel.allMoods.observe(viewLifecycleOwner) { moodData ->
            moodAdapter.updateData(moodData)
        }

        binding.moodLayout.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = moodAdapter

            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    rv.parent.requestDisallowInterceptTouchEvent(true)
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }
    }


    private fun scrollDiaryToDate(date: LocalDate) {
        val dateStr = date.toString()
        val pos = positionsByDate[dateStr]
        Log.e("DiaryFragmentPos", "scrollDiaryToDate: $pos")
        if (pos != null) {
            binding.recyclerViewDiary.post {
                val viewHolder =
                    binding.recyclerViewDiary.findViewHolderForAdapterPosition(pos)
                if (viewHolder != null) {
                    highlightWithRipple(viewHolder.itemView)
                } else {
                    binding.recyclerViewDiary.scrollToPosition(pos)
                    binding.recyclerViewDiary.post {
                        val vh = binding.recyclerViewDiary.findViewHolderForAdapterPosition(pos)
                        vh?.let {
                            highlightWithRipple(
                                it.itemView
                            )
                        }
                    }
                }
            }
        }
    }

    private fun highlightWithRipple(view: View) {
        // Ripple ko force trigger karna
        binding.nestedScrollView.smoothScrollTo(0, view.top)

        view.isPressed = true
        view.postDelayed({
            view.isPressed = false
        }, 200)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showMoodDialog(moodHistoryItem: MoodHistoryItem) {
        val dialog = Dialog(requireContext())
        val moodView = layoutInflater.inflate(R.layout.dialog_mood_track, null)
        val recyclerView = moodView.findViewById<RecyclerView>(R.id.moodTrackerRv)
        val moodList = listOf(
            MoodItem("😀", "Happy"),
            MoodItem("😢", "Sad"),
            MoodItem("😡", "Angry"),
            MoodItem("😴", "Sleepy"),
            MoodItem("🤩", "Excited"),
            MoodItem("😐", "Neutral"),
            MoodItem("😭", "Crying"),
            MoodItem("😍", "Love")
        )

        val moodAdapter = MoodAdapter(moodList, object : MoodAdapter.OnItemClickListener {
            override fun onMoodItemClick(moodItem: MoodItem) {

                Log.d("MoodAdapterLol", "onMoodItemClick: ${moodItem.emoji}")
                if (moodHistoryItem.id == 0) {
                    val newItem = moodHistoryItem.copy(
                        emoji = moodItem.emoji
                    )
                    viewModel.insertMood(newItem)
                } else {
                    val updatedItem = moodHistoryItem.copy(
                        emoji = moodItem.emoji
                    )
                    viewModel.updateMood(updatedItem)
                }
                dialog.dismiss()
            }
        })

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        recyclerView.adapter = moodAdapter

        val selectedIndex = moodList.indexOfFirst { it.emoji == moodHistoryItem?.emoji }
        if (selectedIndex != -1) {
            moodAdapter.setSelectedPosition(selectedIndex)
        }

        dialog.setContentView(moodView)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())//ColorDrawable(Color.TRANSPARENT)
        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}