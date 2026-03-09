package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.CategorySymbolAdapter
import com.anitech.growdaily.databinding.DialogIconPickerBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.enum_class.TaskIcon

class ImagePickerDialog : DialogFragment() {

    private var _binding: DialogIconPickerBinding? = null
    private val binding get() = _binding!!

    private var selectedIcon: TaskIcon = TaskIcon.TROPHY
    private var selectedColor: TaskColor = TaskColor.DARK_BLUE
    private var onImageSelectedListener: ((iconName: String, colorName: String) -> Unit)? = null

    companion object {
        private const val ARG_SELECTED_ICON = "selected_icon"
        private const val ARG_SELECTED_COLOR = "selected_color"

        fun newInstance(
            selectedIcon: String? = null,
            selectedColor: String? = null
        ): ImagePickerDialog {
            val fragment = ImagePickerDialog()
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
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // set up UI and listeners AFTER content view is set
        setupViews()
        setupSymbolRecycler()
        setupFrameClickListeners()
        setupClickListeners()

        // allow cancel on outside touch and back press
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        return dialog
    }

    private fun setupViews() {
        updatePreview()
    }

    private fun updatePreview() {
        binding.imagePreview.setImageResource(selectedIcon.resId)
        binding.previewBackground.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), selectedColor.resId)
        )
    }

    private fun setupSymbolRecycler() {
        val symbolAdapter = CategorySymbolAdapter(
            TaskIcon.entries.map { it.resId },
            object : CategorySymbolAdapter.OnSymbolClickListener {
                override fun onSymbolClick(symbolResId: Int) {
                    selectedIcon =
                        TaskIcon.entries.find { it.resId == symbolResId } ?: TaskIcon.BELL
                    updatePreview()
                }
            },
            preSelectedResId = selectedIcon.resId
        )
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
        val checkDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)

        // Preselect color based on selectedColor
        val preIndex = colors.indexOf(selectedColor)
        if (preIndex in frames.indices) {
            val preImageView = frames[preIndex].getChildAt(0) as? ImageView
            preImageView?.setImageDrawable(checkDrawable)
        }

        // Set click listeners
        frames.forEachIndexed { index, frame ->
            frame.setOnClickListener {
                frames.forEachIndexed { i, f ->
                    val imageView = f.getChildAt(0) as? ImageView
                    imageView?.setImageDrawable(if (i == index) checkDrawable else null)
                }
                selectedColor = colors.getOrElse(index) { TaskColor.BLUE }
                updatePreview()
            }
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
