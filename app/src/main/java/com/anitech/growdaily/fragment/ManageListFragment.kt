package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import androidx.transition.AutoTransition
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.adapter.ListVerticalAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.database.viewmodel.AppViewModel
import com.anitech.growdaily.databinding.FragmentManageListBinding

class ManageListFragment : Fragment() {

    private var _binding: FragmentManageListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var adapter: ListVerticalAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var lastListCount: Int = -1

    companion object {
        private const val TAG = "ManageListFragment"
        private const val RECYCLER_LAYOUT_STATE_KEY = "recycler_layout_state"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentManageListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize adapter FIRST to prevent race conditions with observers
        setupRecycler()

        // Now it's safe to observe, since adapter is initialized
        observeLists()
        observeAccentColor()

        binding.btnAddList.setOnClickListener {
            findNavController().navigate(R.id.addList)
        }

        binding.buttonSave.setOnClickListener {
            findNavController().popBackStack()
        }

        // Restore RecyclerView scroll state after configuration changes
        savedInstanceState?.let { bundle ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val layoutManagerState = bundle.getParcelable(RECYCLER_LAYOUT_STATE_KEY, android.os.Parcelable::class.java)
                layoutManagerState?.let {
                    binding.RvCondition.layoutManager?.onRestoreInstanceState(it)
                }
            } else {
                @Suppress("DEPRECATION")
                val layoutManagerState = bundle.getParcelable<android.os.Parcelable>(RECYCLER_LAYOUT_STATE_KEY)
                layoutManagerState?.let {
                    binding.RvCondition.layoutManager?.onRestoreInstanceState(it)
                }
            }
        }
    }

    private fun observeAccentColor() {
        val mainActivity = (requireActivity() as? MainActivity)
        if (mainActivity == null) {
            android.util.Log.w(TAG, "MainActivity is null, accent color observer not set up")
            return
        }

        mainActivity.accentColor.observe(viewLifecycleOwner) { color ->
            binding.iconBg.backgroundTintList = ColorStateList.valueOf(color.adjustAlpha(0.12f))
            binding.ivTitleIcon.imageTintList = ColorStateList.valueOf(color)
            
            binding.txtCount.setTextColor(color)
            binding.txtCount.backgroundTintList = ColorStateList.valueOf(color.adjustAlpha(0.12f))

            binding.btnAddList.backgroundTintList = ColorStateList.valueOf(color)
            binding.buttonSave.backgroundTintList = ColorStateList.valueOf(color)

            if (::adapter.isInitialized) {
                adapter.setAccentColor(color)
            }
        }
    }

    private fun setupRecycler() {
        adapter = ListVerticalAdapter(
            mutableListOf(),
            listener = object : ListVerticalAdapter.OnItemClickListener {
                override fun onItemClick(item: ListEntity) {
                    val bundle = Bundle().apply {
                        putParcelable("ConditionEntity", item)
                    }
                    findNavController().navigate(R.id.addList, bundle)
                }
            },
        ) { vh ->
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            itemTouchHelper.startDrag(vh)
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                )
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    (viewHolder as? ListVerticalAdapter.ListViewHolder)?.onItemSelected()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                (viewHolder as? ListVerticalAdapter.ListViewHolder)?.onItemClear()
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                saveNewOrder()
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.RvCondition)
        binding.RvCondition.layoutManager = LinearLayoutManager(requireContext())
        binding.RvCondition.adapter = adapter
    }

    private fun observeLists() {
        viewModel.allLists.observe(viewLifecycleOwner) { lists ->
            try {
                val listCount = lists?.size ?: 0

                val countChanged = lastListCount != listCount
                lastListCount = listCount
                android.util.Log.d(TAG, "Lists observer triggered: $listCount items")

                if (countChanged) {
                    TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
                    binding.emptyStateContainer.visibility = if (listCount == 0) View.VISIBLE else View.GONE
                    binding.RvCondition.visibility = if (listCount == 0) View.GONE else View.VISIBLE
                }

                binding.txtCount.text = resources.getQuantityString(
                    R.plurals.list_count_plural,
                    listCount,
                    listCount
                )

                if (lists != null) {
                    adapter.updateList(lists)
                    android.util.Log.d(TAG, "Lists updated: $listCount items")
                } else {
                    android.util.Log.w(TAG, "Lists observer received null data")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error updating lists", e)
            }
        }
    }

    private fun saveNewOrder() {
        try {
            val updatedLists = adapter.getCurrentList()
            if (updatedLists.isEmpty()) {
                android.util.Log.d(TAG, "No lists to reorder")
                return
            }

            val reordered = updatedLists.mapIndexed { index, list ->
                list.copy(sortOrder = index)
            }

            viewModel.updateListOrder(reordered)
            android.util.Log.d(TAG, "List order saved successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error saving list order", e)
            // Show error feedback to user
            android.widget.Toast.makeText(
                requireContext(),
                "Failed to save list order. Please try again.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onDestroyView() {
        if (_binding != null) {
            binding.RvCondition.adapter = null
        }
        super.onDestroyView()
        // Properly clean up ItemTouchHelper to prevent memory leaks
        if (::itemTouchHelper.isInitialized) {
            itemTouchHelper.attachToRecyclerView(null)
        }
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save RecyclerView scroll position for configuration changes
        if (::itemTouchHelper.isInitialized) {
            binding.RvCondition.layoutManager?.onSaveInstanceState()?.let { layoutState ->
                outState.putParcelable(RECYCLER_LAYOUT_STATE_KEY, layoutState)
            }
        }
    }
}
