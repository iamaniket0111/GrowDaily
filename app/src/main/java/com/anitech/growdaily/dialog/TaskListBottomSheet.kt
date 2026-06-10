package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ListCheckAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.BottomSheetTaskListBinding
import com.anitech.growdaily.util.ListNameValidator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.UUID

/**
 * @param allListsLiveData  LiveData<List<ListEntity>> from the host ViewModel (AddTaskViewModel.allLists).
 *                          Passed in so the sheet observes the same source as the fragment,
 *                          with no dependency on activityViewModels or AppViewModel.
 * @param preselectedIds    IDs that should be checked when the sheet opens.
 * @param onInsertList      Callback to persist a newly created list.
 *                          Callers delegate to viewModel.insertList(list).
 * @param onListsSelected   Returns the final selection when the user taps Done.
 */
class TaskListBottomSheet(
    private val allListsLiveData: LiveData<List<ListEntity>>,
    private val preselectedIds: List<String>,
    private val accentColor: Int,
    private val onInsertList: (ListEntity) -> Unit,
    private val onListsSelected: (List<String>) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTaskListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ListCheckAdapter

    override fun getTheme(): Int = R.style.TaskBottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ListCheckAdapter(
            lists = emptyList(),
            accentColor = accentColor
        ) { _, _ ->
            updateSheetState()
        }

        binding.rvLists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLists.adapter = adapter
        applyAccentColor()
        setupListNameInput()

        adapter.setPreselectedIds(preselectedIds)
        updateSheetState()

        allListsLiveData.observe(viewLifecycleOwner) { lists ->
            val currentSelections = adapter.getSelectedIds()
            adapter.updateData(lists)
            adapter.setPreselectedIds(currentSelections)
            updateSheetState()
        }

        binding.txtCreateChip.setOnClickListener {
            binding.createListContainer.visibility = View.VISIBLE
            binding.etListName.requestFocus()
            showKeyboard(binding.etListName)
            updateSheetState()
        }
        binding.btnCloseCreate.setOnClickListener {
            closeCreateListPanel()
        }

        binding.btnCreate.setOnClickListener {
            createListIfValid()
        }
        binding.etListName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createListIfValid()
                true
            } else {
                false
            }
        }

        binding.btnDone.setOnClickListener {
            onListsSelected(adapter.getSelectedIds())
            dismiss()
        }
    }

    private fun setupListNameInput() {
        val updateCharCount = { text: CharSequence? ->
            val count = text?.length ?: 0
            binding.txtCharCount.text = getString(R.string.char_count_limit, count)
            binding.txtCharCount.setTextColor(
                if (count >= ListNameValidator.MAX_LENGTH) {
                    Color.RED
                } else {
                    accentColor
                }
            )
            binding.btnCreate.alpha = if (text?.trim().isNullOrEmpty()) 0.5f else 1f
        }

        binding.etListName.doAfterTextChanged { text ->
            binding.etListName.error = null
            updateCharCount(text)
        }
        updateCharCount(binding.etListName.text)
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun closeCreateListPanel() {
        binding.createListContainer.visibility = View.GONE
        binding.etListName.setText("")
        binding.etListName.error = null
        hideKeyboard(binding.etListName)
        updateSheetState()
    }

    private fun createListIfValid() {
        val name = binding.etListName.text.toString()
        val existingLists = allListsLiveData.value.orEmpty()
        when (ListNameValidator.validate(name, existingLists)) {
            ListNameValidator.Error.BLANK -> {
                binding.etListName.error = getString(R.string.error_enter_list_name)
                binding.etListName.requestFocus()
                return
            }
            ListNameValidator.Error.DUPLICATE -> {
                binding.etListName.error = getString(R.string.error_list_name_exists)
                binding.etListName.requestFocus()
                return
            }
            null -> Unit
        }

        val trimmedName = name.trim()
        val sortOrder = (existingLists.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newList = ListEntity(
            id = UUID.randomUUID().toString(),
            listTitle = trimmedName,
            sortOrder = sortOrder
        )

        onInsertList(newList)

        adapter.setPreselectedIds(adapter.getSelectedIds() + newList.id)
        closeCreateListPanel()
    }

    private fun applyAccentColor() {
        binding.txtSelectionBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.txtCreateChip.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, 36))
        binding.txtCreateChip.setTextColor(accentColor)
        binding.btnCreate.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.btnDone.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.btnCloseCreate.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.task_text_secondary)
        )
        val count = binding.etListName.text?.length ?: 0
        if (count < ListNameValidator.MAX_LENGTH) {
            binding.txtCharCount.setTextColor(accentColor)
        }
    }

    private fun updateSheetState() {
        val count = adapter.getSelectedIds().size
        val listCount = adapter.itemCount
        binding.txtSelectionBadge.text = count.toString()
        binding.txtCreateChip.text = if (binding.createListContainer.visibility == View.VISIBLE) {
            getString(R.string.creating_status)
        } else {
            getString(R.string.add_new_list)
        }
        binding.btnDone.text = when (count) {
            0 -> getString(R.string.apply_lists)
            1 -> getString(R.string.apply_one_list)
            else -> getString(R.string.apply_multiple_lists, count)
        }
        binding.txtEmptyHint.text = when {
            listCount == 0 -> getString(R.string.first_list_hint)
            count == 0 -> getString(R.string.organize_task_hint)
            count == 1 -> getString(R.string.one_list_added_hint)
            else -> getString(R.string.multiple_lists_added_hint, count)
        }
        binding.txtEmptyState.visibility = if (listCount == 0) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
