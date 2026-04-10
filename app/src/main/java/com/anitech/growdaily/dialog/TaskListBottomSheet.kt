package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ListCheckAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.BottomSheetTaskListBinding
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

        adapter = ListCheckAdapter(emptyList()) { _, _ ->
            updateSheetState()
        }

        binding.rvLists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLists.adapter = adapter

        // Apply selections before the first emission so there is no flash of unchecked state
        adapter.setPreselectedIds(preselectedIds)
        updateSheetState()

        allListsLiveData.observe(viewLifecycleOwner) { lists ->
            // Preserve whatever the user has already checked before replacing the data set
            val currentSelections = adapter.getSelectedIds()
            adapter.updateData(lists)
            adapter.setPreselectedIds(currentSelections)
            updateSheetState()
        }

        // Show create-list input
        binding.txtCreateChip.setOnClickListener {
            binding.createListContainer.visibility = View.VISIBLE
            binding.etListName.requestFocus()
            showKeyboard(binding.etListName)
            updateSheetState()
        }
        binding.btnCloseCreate.setOnClickListener {
            binding.createListContainer.visibility = View.GONE
            binding.etListName.setText("")
            hideKeyboard(binding.etListName)
            updateSheetState()
        }

        // Create, persist, and auto-select a new list
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

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun createListIfValid() {
            val name = binding.etListName.text.toString().trim()
            if (name.isEmpty()) return

            val newList = ListEntity(
                id = UUID.randomUUID().toString(),
                listTitle = name,
                sortOrder = 0
            )

            // Persist through the ViewModel — the LiveData observer will update the adapter
            // with the real DB row once the insert completes
            onInsertList(newList)

            // Speculatively mark the new id as selected; the observer re-applies
            // selections on the next emission (which will include the new list row)
            adapter.setPreselectedIds(adapter.getSelectedIds() + newList.id)
            updateSheetState()

            binding.etListName.setText("")
            binding.createListContainer.visibility = View.GONE
            hideKeyboard(binding.etListName)
            updateSheetState()
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
