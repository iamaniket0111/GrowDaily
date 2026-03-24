package com.anitech.growdaily.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ListCheckAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.BottomSheetTaskListBinding
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

        adapter = ListCheckAdapter(emptyList()) { _, _ -> }

        binding.rvLists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLists.adapter = adapter

        // Apply selections before the first emission so there is no flash of unchecked state
        adapter.setPreselectedIds(preselectedIds)

        allListsLiveData.observe(viewLifecycleOwner) { lists ->
            // Preserve whatever the user has already checked before replacing the data set
            val currentSelections = adapter.getSelectedIds()
            adapter.updateData(lists)
            adapter.setPreselectedIds(currentSelections)
        }

        // Show create-list input
        binding.txtAddNewList.setOnClickListener {
            binding.createListContainer.visibility = View.VISIBLE
            binding.etListName.requestFocus()
        }

        // Create, persist, and auto-select a new list
        binding.btnCreate.setOnClickListener {
            val name = binding.etListName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener

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

            binding.etListName.setText("")
            binding.createListContainer.visibility = View.GONE
        }

        binding.btnDone.setOnClickListener {
            onListsSelected(adapter.getSelectedIds())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}