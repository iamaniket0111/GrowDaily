package com.anitech.growdaily.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.data_class.ListEntity

class ListAdapter(
    private var conditionList: List<ListEntity>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedListId: String? = null
    var isSelectingMode = false

    interface OnItemClickListener {
        fun onItemClick(conditionItem: ListEntity, isSelected: Boolean)
        fun onAllClick(isSelected: Boolean)
        fun onLongPress(item: ListEntity)
        fun onNewListClick()
        fun onMangeListClick()
    }

    companion object {
        private const val VIEW_NONE = 0
        private const val VIEW_ITEM = 1
        private const val VIEW_NEW_LIST = 2
        private const val VIEW_MANAGE_LIST = 3
    }

    // ---------------- ViewHolders ----------------

    class ConditionItemVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.conditionTv)
    }

    class NewListVH(view: View) : RecyclerView.ViewHolder(view)
    class ManageListVH(view: View) : RecyclerView.ViewHolder(view)

    // ---------------- Adapter ----------------

    override fun getItemCount(): Int = conditionList.size + 3

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> VIEW_NONE
            position == itemCount - 2 -> VIEW_NEW_LIST
            position == itemCount - 1 -> VIEW_MANAGE_LIST
            else -> VIEW_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_NONE, VIEW_ITEM ->
                ConditionItemVH(inflater.inflate(R.layout.rv_condition_item, parent, false))

            VIEW_NEW_LIST ->
                NewListVH(inflater.inflate(R.layout.rv_new_list, parent, false))

            VIEW_MANAGE_LIST ->
                ManageListVH(inflater.inflate(R.layout.rv_manage_list, parent, false))

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context

        holder.itemView.backgroundTintList =
            ContextCompat.getColorStateList(context, R.color.white)

        when (holder) {

            is ConditionItemVH -> {

                // ---------- NONE ----------
                if (getItemViewType(position) == VIEW_NONE) {
                    holder.text.text = "None"

                    val isSelected = selectedListId == null
                    if (isSelected) {
                        holder.itemView.backgroundTintList =
                            ContextCompat.getColorStateList(
                                context,
                                R.color.category_dark_blue_25
                            )
                    }

                    holder.itemView.setOnClickListener {
                        listener.onAllClick(isSelected)
                    }
                }
                // ---------- NORMAL ITEM ----------
                else {
                    val item = conditionList[position - 1]
                    holder.text.text = item.listTitle

                    val isSelected = selectedListId == item.id
                    if (isSelected) {
                        holder.itemView.backgroundTintList =
                            ContextCompat.getColorStateList(
                                context,
                                R.color.category_dark_blue_25
                            )
                    }

                    holder.itemView.setOnClickListener {
                        if (!isSelectingMode) {
                            listener.onItemClick(item, isSelected)
                        }
                    }

                    holder.itemView.setOnLongClickListener {
                        if (!isSelectingMode) {
                            listener.onLongPress(item)
                            true
                        } else false
                    }
                }
            }

            is NewListVH -> holder.itemView.setOnClickListener {
                listener.onNewListClick()
            }

            is ManageListVH -> holder.itemView.setOnClickListener {
                listener.onMangeListClick()
            }
        }
    }

    // ---------------- Public API ----------------

    fun setSelectedListById(id: String?) {
        selectedListId = id
        notifyDataSetChanged()
    }


    fun setData(newList: List<ListEntity>) {
        conditionList = newList
        notifyDataSetChanged()
    }
}
