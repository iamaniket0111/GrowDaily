package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.TaskForListAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentEditListBinding
import com.anitech.growdaily.dialog.DeleteListDialog
import java.util.UUID

class EditListFragment : Fragment() {

    private var _binding: FragmentEditListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private val args: EditListFragmentArgs by navArgs()

    private lateinit var adapter: TaskForListAdapter

    // temp selected state
    private val tempSelectedTaskIds = mutableSetOf<String>()

    private lateinit var listId: String
    private var isEditMode = false

    // Keep a reference so the delete menu item can use it
    private var currentListEntity: ListEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val condition = args.ConditionEntity

        if (condition == null) {
            // ADD MODE
            isEditMode = false
            listId = UUID.randomUUID().toString()

            binding.defName.text = "New List"
            binding.infoText.text =
                getString(R.string.list_message, "New List")

        } else {
            // EDIT MODE
            isEditMode = true
            listId = condition.id
            currentListEntity = condition

            binding.defName.text = condition.listTitle
            binding.edListName.setText(condition.listTitle)
            binding.infoText.text =
                getString(R.string.list_message, condition.listTitle)

            // load existing selected tasks
            viewModel.getTaskIdsForList(listId) { ids ->
                tempSelectedTaskIds.clear()
                tempSelectedTaskIds.addAll(ids)
                adapter.notifyDataSetChanged()
            }
        }

        setupRecycler()
        observeAllTasks()
        setupSaveButton()
        setupMenu()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Only show delete in edit mode
                if (isEditMode) {
                    menu.add(Menu.NONE, MENU_DELETE_ID, Menu.NONE, "Delete List").apply {
                        setIcon(R.drawable.ic_delete) // use any delete icon you have
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == MENU_DELETE_ID) {
                    showDeleteListDialog()
                    true
                } else {
                    false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showDeleteListDialog() {
        val listToDelete = currentListEntity ?: return
        DeleteListDialog(
            context = requireContext(),
            list = listToDelete,
            onDeleteList = { list ->
                viewModel.deleteList(list)
                Toast.makeText(requireContext(), "List deleted", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        ).show()
    }

    private fun setupRecycler() {
        adapter = TaskForListAdapter(
            allTasks = emptyList(),
            selectedTaskIds = tempSelectedTaskIds,
            listener = object : TaskForListAdapter.OnItemClickListener {

                override fun onTaskSelected(taskId: String) {
                    tempSelectedTaskIds.add(taskId)
                }

                override fun onTaskUnSelected(taskId: String) {
                    tempSelectedTaskIds.remove(taskId)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun observeAllTasks() {
        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            adapter.submitList(tasks)
        }
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {

            val finalName =
                binding.edListName.text.toString().trim()
                    .ifEmpty { binding.defName.text.toString() }

            val listEntity = ListEntity(
                id = listId,
                listTitle = finalName,
                sortOrder = 0
            )

            if (isEditMode) {
                viewModel.updateList(listEntity)
            } else {
                viewModel.insertList(listEntity)
            }

            // save task relations
            viewModel.saveTasksForList(
                listId = listId,
                taskIds = tempSelectedTaskIds.toList()
            )

            Toast.makeText(requireContext(), "Saved successfully", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MENU_DELETE_ID = 1001
    }
}