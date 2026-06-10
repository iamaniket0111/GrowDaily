package com.anitech.growdaily.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.data_class.ListEntity

class ListAdapter(
    private var conditionList: List<ListEntity>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedListId: String? = null
    var isSelectingMode = false
    private var accentColor: Int? = null

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

    init {
        setHasStableIds(true)
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

    override fun getItemId(position: Int): Long {
        return when (getItemViewType(position)) {
            VIEW_NONE -> Long.MIN_VALUE + 1
            VIEW_NEW_LIST -> Long.MIN_VALUE + 2
            VIEW_MANAGE_LIST -> Long.MIN_VALUE + 3
            else -> conditionList[position - 1].id.hashCode().toLong()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context

        holder.itemView.backgroundTintList =
            ContextCompat.getColorStateList(context, R.color.task_filter_surface)

        when (holder) {

            is ConditionItemVH -> {
                val isSelected: Boolean
                
                // ---------- NONE ----------
                if (getItemViewType(position) == VIEW_NONE) {
                    holder.text.text = context.getString(R.string.list_none)
                    isSelected = selectedListId == null
                    
                    holder.itemView.setOnClickListener {
                        listener.onAllClick(isSelected)
                    }
                }
                // ---------- NORMAL ITEM ----------
                else {
                    val item = conditionList[position - 1]
                    holder.text.text = item.listTitle
                    isSelected = selectedListId == item.id

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

                // Update Visuals based on selection
                if (isSelected) {
                    val color = accentColor ?: ContextCompat.getColor(context, R.color.category_dark_blue)
                    holder.itemView.backgroundTintList = ColorStateList.valueOf(color.adjustAlpha(0.15f))
                    holder.text.setTextColor(color)
                    holder.text.paint.isFakeBoldText = true
                } else {
                    holder.itemView.backgroundTintList = ContextCompat.getColorStateList(context, R.color.task_filter_surface)
                    holder.text.setTextColor(ContextCompat.getColor(context, R.color.task_text_primary))
                    holder.text.paint.isFakeBoldText = false
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

    fun setAccentColor(color: Int) {
        if (this.accentColor == color) return
        this.accentColor = color
        notifyItemRangeChanged(0, itemCount)
    }

    fun setSelectedListById(id: String?) {
        if (selectedListId == id) return
        val previousSelectedListId = selectedListId
        selectedListId = id
        notifyItemChanged(0)
        previousSelectedListId?.let(::findAdapterPositionForListId)?.let(::notifyItemChanged)
        id?.let(::findAdapterPositionForListId)?.let(::notifyItemChanged)
    }


    fun setData(newList: List<ListEntity>) {
        conditionList = newList
        notifyDataSetChanged()
    }

    private fun findAdapterPositionForListId(listId: String): Int? {
        val index = conditionList.indexOfFirst { it.id == listId }
        return if (index >= 0) index + 1 else null
    }
}
