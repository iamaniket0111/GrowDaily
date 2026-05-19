package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.adapter.TaskForListAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.database.viewmodel.AppViewModel
import com.anitech.growdaily.databinding.FragmentEditListBinding
import com.anitech.growdaily.dialog.DeleteListDialog
import com.anitech.growdaily.dialog.TaskActionDialog
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
    private var initialNameSnapshot: String? = null
    private var initialSelectedTaskIdsSnapshot: Set<String>? = null

    private var currentAccentColor: Int? = null

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

            binding.defName.text = getString(R.string.new_list_default_title)
            binding.infoText.text =
                getString(R.string.list_message, getString(R.string.new_list_default_title))

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
                initialSelectedTaskIdsSnapshot = ids.toSet()
                updateSelectedCount()
                adapter.notifyDataSetChanged()
            }
        }

        setupRecycler()
        observeAllTasks()
        setupSaveButton()
        setupMenu()
        setupDiscardHandling()
        observeAccentColor()
        captureInitialSnapshotsIfNeeded()
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            currentAccentColor = color
            binding.defName.setTextColor(color)
            binding.txtTaskCount.setTextColor(color)
            binding.txtTaskCount.backgroundTintList = android.content.res.ColorStateList.valueOf(color.adjustAlpha(0.12f))
            binding.buttonSave.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
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
                return when (menuItem.itemId) {
                    MENU_DELETE_ID -> {
                        showDeleteListDialog()
                        true
                    }
                    android.R.id.home -> {
                        attemptClose()
                        true
                    }
                    else -> false
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
                Toast.makeText(requireContext(), getString(R.string.list_deleted_toast), Toast.LENGTH_SHORT).show()
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
                    updateSelectedCount()
                }

                override fun onTaskUnSelected(taskId: String) {
                    tempSelectedTaskIds.remove(taskId)
                    updateSelectedCount()
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun observeAllTasks() {
        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            binding.emptyTasksContainer.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
            adapter.submitList(tasks)
        }
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            val finalName = binding.edListName.text.toString().trim()

            if (finalName.isBlank()) {
                binding.edListName.error = getString(R.string.error_enter_list_name)
                binding.edListName.requestFocus()
                return@setOnClickListener
            }

            val hasDuplicateName = viewModel.allLists.value
                .orEmpty()
                .any { existing ->
                    existing.id != listId &&
                        existing.listTitle.equals(finalName, ignoreCase = true)
                }

            if (hasDuplicateName) {
                binding.edListName.error = getString(R.string.error_list_name_exists)
                binding.edListName.requestFocus()
                return@setOnClickListener
            }

            val listEntity = ListEntity(
                id = listId,
                listTitle = finalName,
                sortOrder = if (isEditMode) {
                    currentListEntity?.sortOrder ?: 0
                } else {
                    (viewModel.allLists.value?.maxOfOrNull { it.sortOrder } ?: -1) + 1
                }
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

            Toast.makeText(requireContext(), getString(R.string.saved_successfully_toast), Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun updateSelectedCount() {
        val count = tempSelectedTaskIds.size
        binding.txtTaskCount.text = resources.getQuantityString(R.plurals.selected_count_plural, count, count)
    }

    private fun setupDiscardHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    attemptClose()
                }
            }
        )
    }

    private fun attemptClose() {
        if (!hasUnsavedChanges()) {
            findNavController().popBackStack()
            return
        }

        TaskActionDialog(
            context = requireContext(),
            title = getString(R.string.discard_changes_title),
            message = if (isEditMode) {
                getString(R.string.discard_list_message_edit)
            } else {
                getString(R.string.discard_list_message_add)
            },
            primaryLabel = getString(R.string.discard_button),
            secondaryLabel = getString(R.string.keep_editing_button),
            iconRes = R.drawable.ic_warning,
            accentColor = currentAccentColor ?: ContextCompat.getColor(requireContext(), R.color.brand_blue),
            iconBubbleColor = (currentAccentColor ?: ContextCompat.getColor(requireContext(), R.color.brand_blue)).adjustAlpha(0.2f),
            onPrimaryAction = {
                findNavController().popBackStack()
            }
        ).show()
    }

    private fun hasUnsavedChanges(): Boolean {
        val initialName = initialNameSnapshot ?: return false
        val initialTaskIds = initialSelectedTaskIdsSnapshot ?: return false
        val currentName = binding.edListName.text?.toString()?.trim().orEmpty()
        val currentTaskIds = tempSelectedTaskIds.toSet()
        return currentName != initialName || currentTaskIds != initialTaskIds
    }

    private fun captureInitialSnapshotsIfNeeded() {
        if (initialNameSnapshot == null) {
            initialNameSnapshot = binding.edListName.text?.toString()?.trim().orEmpty()
        }
        if (!isEditMode && initialSelectedTaskIdsSnapshot == null) {
            initialSelectedTaskIdsSnapshot = emptySet()
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
