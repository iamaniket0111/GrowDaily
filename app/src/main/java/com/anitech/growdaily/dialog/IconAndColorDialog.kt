package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Window
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.anitech.growdaily.setSolidBackgroundColorCompat
import com.anitech.growdaily.adapter.TaskIconAdapter
import com.anitech.growdaily.databinding.DialogIconPickerBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon

class IconAndColorDialog : DialogFragment() {

    private var _binding: DialogIconPickerBinding? = null
    private val binding get() = _binding!!

    private var selectedIcon: TaskIcon = TaskIcon.TROPHY
    private var selectedColor: TaskColor = TaskColor.DARK_BLUE
    private var onImageSelectedListener: ((iconName: String, colorName: String) -> Unit)? = null
    private var taskIconAdapter: TaskIconAdapter? = null

    companion object {
        private const val ARG_SELECTED_ICON = "selected_icon"
        private const val ARG_SELECTED_COLOR = "selected_color"

        fun newInstance(
            selectedIcon: String? = null,
            selectedColor: String? = null
        ): IconAndColorDialog {
            val fragment = IconAndColorDialog()
            val args = Bundle()
            args.putString(ARG_SELECTED_ICON, selectedIcon)
            args.putString(ARG_SELECTED_COLOR, selectedColor)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // inflate binding first
        _binding = DialogIconPickerBinding.inflate(layoutInflater)

        // read preselected args
        arguments?.getString(ARG_SELECTED_ICON)?.let { iconName ->
            TaskIcon.fromName(iconName)?.let { selectedIcon = it }
        }
        arguments?.getString(ARG_SELECTED_COLOR)?.let { colorName ->
            TaskColor.fromName(colorName)?.let { selectedColor = it }
        }

        // create dialog
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)

        // optional: transparent background so your layout's corners show if rounded
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        // set up UI and listeners AFTER content view is set
        setupViews()
        setupSymbolRecycler()
        setupFrameClickListeners()
        setupClickListeners()

        // allow cancel on outside touch and back press
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setLayout(
            ((resources.displayMetrics.widthPixels) * 0.9f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private fun setupViews() {
        applyAccent()
        updatePreview()
    }

    private fun updatePreview() {
        applyAccent()
        binding.imagePreview.setImageResource(selectedIcon.resId)
        binding.previewBackground.setSolidBackgroundColorCompat(
            ContextCompat.getColor(requireContext(), selectedColor.resId)
        )
        taskIconAdapter?.updateSelectedBubbleColor(
            ContextCompat.getColor(requireContext(), selectedColor.resId)
        )
    }

    private fun applyAccent() {
        val color = ContextCompat.getColor(requireContext(), selectedColor.resId)
        binding.viewAccent.setSolidBackgroundColorCompat(color)
        binding.doneBtn.backgroundTintList = ColorStateList.valueOf(color)
        tintStroke(binding.viewAccent, color)
    }

    private fun setupSymbolRecycler() {
        val symbolAdapter = TaskIconAdapter(
            TaskIcon.entries.map { it.resId },
            object : TaskIconAdapter.OnSymbolClickListener {
                override fun onSymbolClick(symbolResId: Int) {
                    selectedIcon =
                        TaskIcon.entries.find { it.resId == symbolResId } ?: TaskIcon.BELL
                    updatePreview()
                }
            },
            preSelectedResId = selectedIcon.resId,
            selectedBubbleColor = ContextCompat.getColor(requireContext(), selectedColor.resId)
        )
        taskIconAdapter = symbolAdapter
        binding.symbolRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 6)
            adapter = symbolAdapter
        }
    }

    private fun setupFrameClickListeners() {
        val frames = listOf(
            binding.frame1, binding.frame2, binding.frame3, binding.frame4,
            binding.frame5, binding.frame6, binding.frame7, binding.frame8
        )

        val colors = TaskColor.entries
        // Preselect color based on selectedColor
        val preIndex = colors.indexOf(selectedColor)
        if (preIndex in frames.indices) {
            updateColorSelectionUi(frames, preIndex)
        }

        // Set click listeners
        frames.forEachIndexed { index, frame ->
            frame.setOnClickListener {
                selectedColor = colors.getOrElse(index) { TaskColor.BLUE }
                updateColorSelectionUi(frames, index)
                updatePreview()
            }
        }
    }

    private fun updateColorSelectionUi(frames: List<ViewGroup>, selectedIndex: Int) {
        val selectedColorInt = ContextCompat.getColor(requireContext(), selectedColor.resId)
        frames.forEachIndexed { index, frame ->
            val strokeView = frame.getChildAt(1)
            val checkView = frame.getChildAt(2) as? ImageView
            strokeView.alpha = if (index == selectedIndex) 1f else 0f
            tintStroke(strokeView, selectedColorInt)
            checkView?.visibility = if (index == selectedIndex) View.VISIBLE else View.GONE
        }
    }

    private fun tintStroke(view: View?, color: Int) {
        val drawable = view?.background?.mutate()
        if (drawable is GradientDrawable) {
            drawable.setStroke(
                (resources.displayMetrics.density * 1.5f).toInt().coerceAtLeast(1),
                color
            )
        }
    }

    private fun setupClickListeners() {
        binding.cancelBtn.setOnClickListener {
            dismiss()
        }

        binding.doneBtn.setOnClickListener {
            onImageSelectedListener?.invoke(selectedIcon.name, selectedColor.name)
            dismiss()
        }
    }

    fun setOnImageSelectedListener(listener: (iconName: String, colorName: String) -> Unit) {
        this.onImageSelectedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
