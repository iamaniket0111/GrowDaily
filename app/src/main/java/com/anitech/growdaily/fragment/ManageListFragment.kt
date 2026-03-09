package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ListVerticalAdapter
import com.anitech.growdaily.data_class.ListEntity
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.database.TaskViewModel
import com.anitech.growdaily.databinding.FragmentManageListBinding

class ManageListFragment : Fragment() {

    private var _binding: FragmentManageListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var adapter: ListVerticalAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        observeLists()

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }



    private fun setupRecycler() {

        adapter = ListVerticalAdapter(
            mutableListOf(),
            listener = object : ListVerticalAdapter.OnItemClickListener {
                override fun onItemClick(item: ListEntity) {
                    val bundle = bundleOf("ConditionEntity" to item)
                    findNavController().navigate(R.id.editList, bundle)
                }
            },
            dragStart = { vh ->
                itemTouchHelper.startDrag(vh)
            }
        )

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
                    viewHolder.adapterPosition,
                    target.adapterPosition
                )

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                // drag finished → save order
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
            adapter.updateList(lists)
        }
    }

    private fun saveNewOrder() {

        val updatedLists = adapter.getCurrentList()

        val reordered = updatedLists.mapIndexed { index, list ->
            list.copy(sortOrder = index)
        }

        viewModel.updateListOrder(reordered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}