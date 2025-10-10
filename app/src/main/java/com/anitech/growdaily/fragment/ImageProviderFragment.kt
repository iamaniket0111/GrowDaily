package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.CategorySymbolAdapter
import com.anitech.growdaily.databinding.FragmentImageProviderBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon

class ImageProviderFragment : Fragment() {

    private var _binding: FragmentImageProviderBinding? = null
    private val binding get() = _binding!!

    private var selectedIcon: TaskIcon = TaskIcon.TROPHY
    private var selectedColor: TaskColor = TaskColor.DARK_BLUE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageProviderBinding.inflate(inflater, container, false)

        arguments?.getString("selectedIcon")?.let { iconName ->
            TaskIcon.fromName(iconName)?.let { selectedIcon = it }
        }
        arguments?.getString("selectedColor")?.let { colorName ->
            TaskColor.fromName(colorName)?.let { selectedColor = it }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSymbolRecycler()
        setupFrameClickListeners()

        binding.cancelBtn.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.addBtn.setOnClickListener {
            val bundle = Bundle().apply {
                putString("drawableResId", selectedIcon.name)
                putString("backgroundColor", selectedColor.name)
            }
            parentFragmentManager.setFragmentResult("iconPickerResult", bundle)
            findNavController().popBackStack()
        }
    }

    /** Setup RecyclerView for icons */
    private fun setupSymbolRecycler() {
        val symbolAdapter = CategorySymbolAdapter(
            TaskIcon.entries.map { it.resId },
            object : CategorySymbolAdapter.OnSymbolClickListener {
                override fun onSymbolClick(symbolResId: Int) {
                    selectedIcon =
                        TaskIcon.entries.find { it.resId == symbolResId } ?: TaskIcon.BELL
                }
            },
            preSelectedResId = selectedIcon.resId
        )
        binding.symbolRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 6)
            adapter = symbolAdapter
        }
    }

    /** Setup frame color selection */
    private fun setupFrameClickListeners() {
        val frames = listOf(
            binding.frame1, binding.frame2, binding.frame3, binding.frame4,
            binding.frame5, binding.frame6, binding.frame7, binding.frame8
        )

        val colors = TaskColor.entries
        val checkDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)

        // 🔹 Preselect color based on selectedColor
        val preIndex = colors.indexOf(selectedColor)
        if (preIndex in frames.indices) {
            val preImageView = frames[preIndex].getChildAt(0) as? ImageView
            preImageView?.setImageDrawable(checkDrawable)
        }

        // 🔹 Set click listeners
        frames.forEachIndexed { index, frame ->
            frame.setOnClickListener {
                frames.forEachIndexed { i, f ->
                    val imageView = f.getChildAt(0) as? ImageView
                    imageView?.setImageDrawable(if (i == index) checkDrawable else null)
                }
                selectedColor = colors.getOrElse(index) { TaskColor.BLUE }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
