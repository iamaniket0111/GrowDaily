package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import com.anitech.growdaily.adapter.WeekMoodAdapter
import com.anitech.growdaily.data_class.DayLogEntity
import com.anitech.growdaily.data_class.MoodHistoryItem
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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ==================== Calendar Dates RV ====================
        val today = LocalDate.now()
        val datesAdapter = DateAdapter(today, 365, object : DateAdapter.OnItemClickListener {
            override fun addIntoDiary(date: LocalDate) {
                val bundle = bundleOf("date" to date.toString())
                requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.nav_add_diary, bundle)
            }

            override fun scrollToDiary(date: LocalDate) {
                scrollDiaryToDate(date)
            }
        })

        binding.calenderRv.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, true)
            adapter = datesAdapter
            disallowParentIntercept()
        }

        // ==================== Diary Entries RV ====================
        diaryAdapter = DiaryAdapter(emptyList(), object : DiaryAdapter.OnItemClickListener {
            override fun moveToEditListener(diaryEntry: DayLogEntity) {
                val bundle = if (diaryEntry.diaryId != null) {
                    bundleOf("diary" to diaryEntry)
                } else {
                    null
                }
                requireActivity().findNavController(R.id.nav_host_fragment_content_main)
                    .navigate(R.id.nav_add_diary, bundle ?: Bundle())
            }
        })

        diaryLayoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDiary.apply {
            layoutManager = diaryLayoutManager
            isNestedScrollingEnabled = false
            adapter = diaryAdapter
        }

        // Observe day logs & update everything
//        viewModel.dayLogs.observe(viewLifecycleOwner) { entries ->
//            binding.noDayTaskLayoutContainer.visibility =
//                if (entries.isEmpty()) View.VISIBLE else View.GONE
//
//            diaryAdapter.updateData(entries)
//
//            val uniqueDates = entries.map { it.date }.distinct().sortedDescending()
//            datesAdapter.updateData(uniqueDates)
//
//            positionsByDate = entries.withIndex().associate { it.value.date to it.index }
//
//            Log.d(
//                "DiaryFragment",
//                "DayLogs size: ${entries.size}, Unique dates: ${uniqueDates.size}"
//            )
//        }

        // ==================== Weekly Mood Grid ====================
        val moodAdapter =
            WeekMoodAdapter(emptyList(), object : WeekMoodAdapter.OnItemClickListener {
                override fun onMoodItemClick(moodHistoryItem: MoodHistoryItem) {
                    MoodDialog(
                        requireContext(),
                        moodHistoryItem,
                        object : MoodDialog.OnMoodSelectedListener {
                            override fun onMoodSelected(updatedMood: MoodHistoryItem) {
//                                if (updatedMood.id == 0) {
//                                    viewModel.insertMood(updatedMood)
//                                } else {
//                                    viewModel.updateMood(updatedMood)
//                                }
                            }
                        }
                    ).show()
                }
            })

//        viewModel.allMoods.observe(viewLifecycleOwner) { moodData ->
//            moodAdapter.updateData(moodData)
//        }

        binding.moodLayout.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = moodAdapter
            disallowParentIntercept()
        }
    }

    // Common touch listener to prevent parent (probably ViewPager2/NestedScrollView) from intercepting horizontal scroll
    private fun RecyclerView.disallowParentIntercept() {
        addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                rv.parent.requestDisallowInterceptTouchEvent(true)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun scrollDiaryToDate(date: LocalDate) {
        val dateStr = date.toString()
        val pos = positionsByDate[dateStr] ?: return

        binding.recyclerViewDiary.post {
            val viewHolder = binding.recyclerViewDiary.findViewHolderForAdapterPosition(pos)
            if (viewHolder != null) {
                highlightWithRipple(viewHolder.itemView)
            } else {
                // Instant scroll to bring item into layout so we can measure top
                binding.recyclerViewDiary.scrollToPosition(pos)
                binding.recyclerViewDiary.post {
                    val vh = binding.recyclerViewDiary.findViewHolderForAdapterPosition(pos)
                    vh?.let { highlightWithRipple(it.itemView) }
                }
            }
        }
    }

    private fun highlightWithRipple(view: View) {
        // Smooth scroll NestedScrollView to bring item near top of RecyclerView area
        binding.nestedScrollView.smoothScrollTo(0, view.top)

        // Trigger ripple effect
        view.isPressed = true
        view.postDelayed({ view.isPressed = false }, 200)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}