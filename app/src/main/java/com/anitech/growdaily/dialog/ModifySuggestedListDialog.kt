package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.DialogModifySuggestedListBinding
import com.anitech.growdaily.util.ListNameValidator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModifySuggestedListDialog(
    private val currentListName: String,
    private val existingLists: List<ListEntity>,
    private val accentColor: Int,
    private val onSaveListName: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogModifySuggestedListBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.TaskBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogModifySuggestedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etListName.setText(currentListName)
        binding.etListName.setSelection(currentListName.length)

        val updateCharCount = { text: CharSequence? ->
            val count = text?.length ?: 0
            binding.txtCharCount.text = getString(R.string.char_count_limit, count)
            if (count >= ListNameValidator.MAX_LENGTH) {
                binding.txtCharCount.setTextColor(Color.RED)
            } else {
                if (accentColor != 0) binding.txtCharCount.setTextColor(accentColor)
            }
            binding.btnSave.alpha = if (text?.trim().isNullOrEmpty()) 0.5f else 1f
        }

        binding.etListName.doAfterTextChanged { text ->
            binding.etListName.error = null
            updateCharCount(text)
        }
        updateCharCount(currentListName)

        if (accentColor != 0) {
            binding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            val inputName = binding.etListName.text.toString()
            when (ListNameValidator.validate(inputName, existingLists)) {
                ListNameValidator.Error.BLANK -> {
                    binding.etListName.error = getString(R.string.error_enter_list_name)
                    return@setOnClickListener
                }
                ListNameValidator.Error.DUPLICATE -> {
                    binding.etListName.error = getString(R.string.error_list_name_exists)
                    return@setOnClickListener
                }
                null -> Unit
            }
            val trimmed = inputName.trim()
            onSaveListName(trimmed)
            dismiss()
        }

        binding.etListName.requestFocus()
        showKeyboard(binding.etListName)
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
