package com.anitech.growdaily.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ListCheckAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.databinding.BottomSheetTaskListBinding
import com.anitech.growdaily.database.AppViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.UUID

class TaskListBottomSheet(
    private val preselectedIds: List<String>,
    private val onListsSelected: (List<String>) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTaskListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var adapter: ListCheckAdapter
    private var isPreselectDone = false

    override fun getTheme(): Int {
        return R.style.TaskBottomSheetDialogTheme
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

        adapter = ListCheckAdapter(emptyList()) { _, _ -> }

        binding.rvLists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLists.adapter = adapter

//        viewModel.allLists.observe(viewLifecycleOwner) { lists ->
//            adapter.updateData(lists)
//
//            if (!isPreselectDone) {
//                adapter.setPreselectedIds(preselectedIds)
//                isPreselectDone = true
//            }
//        }

        // Show create list input
        binding.txtAddNewList.setOnClickListener {
            binding.createListContainer.visibility = View.VISIBLE
            binding.etListName.requestFocus()
        }

        // Create new list
        binding.btnCreate.setOnClickListener {
            val name = binding.etListName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener

            val newList = ListEntity(
                id = UUID.randomUUID().toString(),
                listTitle = name,
                sortOrder = 0
            )

            //viewModel.insertList(newList)

            // Auto select newly created list
            adapter.setPreselectedIds(
                adapter.getSelectedIds() + newList.id
            )

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

