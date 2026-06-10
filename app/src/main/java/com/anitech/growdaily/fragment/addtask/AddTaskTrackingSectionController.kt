package com.anitech.growdaily.fragment.addtask

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.ChecklistAdapter
import com.anitech.growdaily.data_class.AddTaskUiState
import com.anitech.growdaily.enum_class.TrackingType

internal class AddTaskTrackingSectionController(
    private val host: AddTaskSectionHost,
    private val accentCoordinator: AddTaskAccentCoordinator,
) {
    private lateinit var checklistAdapter: ChecklistAdapter

    fun bind() {
        setupChecklistRecyclerView()
        setupTrackingTypeSelectionListeners()
        setupTrackingStepperListeners()
        setupChecklistInputListeners()
    }

    fun render(state: AddTaskUiState) {
        val binding = host.binding.taskTrackingType
        binding.root.isVisible = !(host.editingTask != null && state.trackingType == TrackingType.BINARY)
        binding.typeSelectorContainer.isVisible = host.editingTask == null

        highlightSelectedType(state.trackingType)
        showExtraFieldsFor(state.trackingType)
        binding.txtCountValue.text = state.dailyTargetCount.toString()
        binding.txtMinutesValue.text = (state.targetDurationSeconds / 60).toString()
        submitChecklistItems(state.checklistItems)
    }

    fun refreshHighlight(type: TrackingType) {
        highlightSelectedType(type)
    }

    fun setupAccessibility() {
        with(host.binding.taskTrackingType) {
            listOf(binaryType, countType, timerType, checklistType).forEach { chip ->
                chip.accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        hostView: View,
                        info: AccessibilityNodeInfo
                    ) {
                        super.onInitializeAccessibilityNodeInfo(hostView, info)
                        info.isSelected = hostView.backgroundTintList?.defaultColor == host.accentColor
                    }
                }
            }
        }
    }

    private fun setupChecklistRecyclerView() {
        val binding = host.binding.taskTrackingType
        var itemTouchHelper: ItemTouchHelper? = null

        checklistAdapter = ChecklistAdapter(
            onRemoveItem = { index -> host.viewModel.removeChecklistItem(index) },
            onEditItem = { index, text -> showEditChecklistItemDialog(index, text) },
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) }
        )
        binding.checklistRecyclerView.adapter = checklistAdapter

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }
                host.viewModel.moveChecklistItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS
                    )
                    viewHolder?.itemView?.animate()
                        ?.scaleX(1.02f)?.scaleY(1.02f)
                        ?.translationZ(host.hostDpToPx(4).toFloat())?.duration = 100
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0f).duration = 100
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.checklistRecyclerView)
    }

    private fun setupTrackingTypeSelectionListeners() {
        with(host.binding.taskTrackingType) {
            binaryType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.BINARY) }
            countType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.COUNT) }
            timerType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.TIMER) }
            checklistType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.CHECKLIST) }
        }
    }

    private fun setupTrackingStepperListeners() {
        with(host.binding.taskTrackingType) {
            btnCountMinus.setOnClickListener {
                val current = host.viewModel.uiState.value.dailyTargetCount
                host.viewModel.updateDailyTargetCount(current - 1)
            }
            btnCountPlus.setOnClickListener {
                val current = host.viewModel.uiState.value.dailyTargetCount
                host.viewModel.updateDailyTargetCount(current + 1)
            }
            btnMinutesMinus.setOnClickListener {
                val currentSec = host.viewModel.uiState.value.targetDurationSeconds
                host.viewModel.updateTargetDurationSeconds(currentSec - 300L)
            }
            btnMinutesPlus.setOnClickListener {
                val currentSec = host.viewModel.uiState.value.targetDurationSeconds
                host.viewModel.updateTargetDurationSeconds(currentSec + 300L)
            }
        }
    }

    private fun setupChecklistInputListeners() {
        with(host.binding.taskTrackingType) {
            btnAddChecklistItem.isEnabled = false
            etChecklistItem.doAfterTextChanged {
                btnAddChecklistItem.isEnabled = !it.isNullOrBlank()
            }
            btnAddChecklistItem.setOnClickListener { addChecklistItemFromInput() }
            etChecklistItem.setOnEditorActionListener { _, _, _ ->
                addChecklistItemFromInput()
                true
            }
        }
    }

    private fun addChecklistItemFromInput() {
        with(host.binding.taskTrackingType) {
            val text = etChecklistItem.text.toString()
            if (text.isNotBlank()) {
                host.viewModel.addChecklistItem(text)
                etChecklistItem.setText("")
            }
        }
    }

    private fun showEditChecklistItemDialog(index: Int, currentText: String) {
        val dialogView = LayoutInflater.from(host.hostContext())
            .inflate(R.layout.dialog_edit_checklist_item, null)
        val etItem = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.editItemEditText
        )
        val layoutItem = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(
            R.id.editItemLayout
        )
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        etItem.setText(currentText)
        etItem.setSelection(currentText.length)

        layoutItem.setBoxStrokeColor(host.accentColor)
        layoutItem.setHintTextColor(ColorStateList.valueOf(host.accentColor))
        btnSave.backgroundTintList = ColorStateList.valueOf(host.accentColor)
        btnCancel.setTextColor(host.accentColor)

        val dialog = AlertDialog.Builder(host.hostContext(), R.style.Theme_GrowDaily_Dialog)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val newText = etItem.text.toString().trim()
            if (newText.isNotBlank()) {
                host.viewModel.updateChecklistItem(index, newText)
                dialog.dismiss()
            } else {
                layoutItem.error = host.getHostString(R.string.error_checklist_item_empty)
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener {
            etItem.requestFocus()
            val imm = host.hostContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etItem, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    private fun highlightSelectedType(type: TrackingType) {
        val activeColor = host.accentColor
        val inactiveColor = ContextCompat.getColor(host.hostContext(), R.color.add_form_muted_surface)
        val inactiveTextColor = ContextCompat.getColor(host.hostContext(), R.color.add_form_text_secondary)

        with(host.binding.taskTrackingType) {
            val buttons = listOf(binaryType, countType, timerType, checklistType)
            buttons.forEach { btn ->
                btn.backgroundTintList = ColorStateList.valueOf(inactiveColor)
                btn.setTextColor(inactiveTextColor)
            }
            val activeBtn = when (type) {
                TrackingType.BINARY -> binaryType
                TrackingType.COUNT -> countType
                TrackingType.TIMER -> timerType
                TrackingType.CHECKLIST -> checklistType
            }
            activeBtn.backgroundTintList = ColorStateList.valueOf(activeColor)
            activeBtn.setTextColor(accentCoordinator.onAccentTextColor())
        }
    }

    private fun showExtraFieldsFor(type: TrackingType) {
        with(host.binding.taskTrackingType) {
            countExtraContainer.isVisible = type == TrackingType.COUNT
            timerExtraContainer.isVisible = type == TrackingType.TIMER
            checklistExtraContainer.isVisible = type == TrackingType.CHECKLIST
        }
    }

    private fun submitChecklistItems(items: List<String>) {
        if (!::checklistAdapter.isInitialized) return
        val previousSize = checklistAdapter.itemCount
        checklistAdapter.submitList(items) {
            if (items.size > previousSize) {
                host.binding.taskTrackingType.checklistRecyclerView
                    .scrollToPosition(items.size - 1)
            }
        }
    }
}
