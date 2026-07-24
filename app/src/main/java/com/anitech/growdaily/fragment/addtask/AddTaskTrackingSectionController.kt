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
import android.graphics.drawable.ColorDrawable
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
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
        setupTrackingTypeSelectionListeners()
        setupCountEditTextListener()
        host.binding.taskTrackingType.timerExtraContainer.setOnClickListener { showTimerPickerDialog() }
        host.binding.taskTrackingType.checklistExtraContainer.setOnClickListener { showChecklistManagerDialog() }
    }

    fun render(state: AddTaskUiState) {
        val binding = host.binding.taskTrackingType
        binding.root.isVisible = !(host.editingTask != null && state.trackingType == TrackingType.BINARY)
        binding.typeSelectorContainer.isVisible = host.editingTask == null

        highlightSelectedType(state.trackingType)
        showExtraFieldsFor(state.trackingType)

        // Update count display text if user is not typing
        if (!binding.txtCountValue.hasFocus()) {
            binding.txtCountValue.setText(state.dailyTargetCount.toString())
        }

        // Update timer summary row
        val totalSecs = state.targetDurationSeconds.coerceAtLeast(60L)
        val displayHours = (totalSecs / 3600).toInt()
        val displayMinutes = ((totalSecs % 3600) / 60).toInt().coerceAtLeast(if (displayHours == 0) 1 else 0)
        binding.txtTimerValue.text = String.format("%02d:%02d", displayHours, displayMinutes)

        // Update checklist summary row
        val itemCount = state.checklistItems.size
        binding.txtChecklistCountValue.text = if (itemCount == 1) "1 item" else "$itemCount items"
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

    private fun setupTrackingTypeSelectionListeners() {
        with(host.binding.taskTrackingType) {
            binaryType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.BINARY) }
            countType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.COUNT) }
            timerType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.TIMER) }
            checklistType.setOnClickListener { host.viewModel.updateTrackingType(TrackingType.CHECKLIST) }
        }
    }


    private fun setupCountEditTextListener() {
        with(host.binding.taskTrackingType) {
            txtCountValue.doAfterTextChanged { editable ->
                val rawStr = editable?.toString() ?: ""
                val parsed = rawStr.toIntOrNull()
                if (parsed != null) {
                    val coerced = parsed.coerceAtLeast(1)
                    host.viewModel.updateDailyTargetCount(coerced)
                } else {
                    host.viewModel.updateDailyTargetCount(1)
                }
            }
            txtCountValue.setOnEditorActionListener { v, _, _ ->
                v.clearFocus()
                val imm = host.hostContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            }
            txtCountValue.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    val rawStr = txtCountValue.text.toString()
                    val parsed = rawStr.toIntOrNull()
                    if (parsed == null || parsed <= 0) {
                        txtCountValue.setText("1")
                    } else {
                        // Strip leading zeros by setting the parsed clean value (e.g. "05" -> "5")
                        txtCountValue.setText(parsed.toString())
                    }
                }
            }
        }
    }

    private fun showTimerPickerDialog() {
        val currentSecs = host.viewModel.uiState.value.targetDurationSeconds.coerceAtLeast(60L)
        val initHours = (currentSecs / 3600).toInt()
        val initMinutes = ((currentSecs % 3600) / 60).toInt().coerceAtLeast(if (initHours == 0) 1 else 0)

        val dialogView = LayoutInflater.from(host.hostContext())
            .inflate(R.layout.dialog_timer_picker, null)

        val pickerHours = dialogView.findViewById<android.widget.NumberPicker>(R.id.pickerHours)
        val pickerMinutes = dialogView.findViewById<android.widget.NumberPicker>(R.id.pickerMinutes)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnTimerOk)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnTimerCancel)

        // Configure pickers
        pickerHours.minValue = 0
        pickerHours.maxValue = 23
        pickerMinutes.minValue = 0
        pickerMinutes.maxValue = 59
        pickerHours.setFormatter { String.format("%02d", it) }
        pickerMinutes.setFormatter { String.format("%02d", it) }
        pickerHours.value = initHours
        pickerMinutes.value = initMinutes

        // Tint picker dividers and OK button to accent color
        val accent = host.accentColor
        listOf(pickerHours, pickerMinutes).forEach { picker ->
            runCatching {
                val field = android.widget.NumberPicker::class.java
                    .getDeclaredField("mSelectionDivider")
                field.isAccessible = true
                field.set(picker, ColorDrawable(accent))
            }
            picker.invalidate()
        }
        btnOk.backgroundTintList = ColorStateList.valueOf(accent)
        btnCancel.setTextColor(accent)

        val dialog = android.app.Dialog(host.hostContext())
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            (host.hostResources().displayMetrics.widthPixels * 0.88f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Enforce minimum 1 minute when hours is 0
        pickerHours.setOnValueChangedListener { _, _, newHours ->
            if (newHours == 0 && pickerMinutes.value == 0) pickerMinutes.value = 1
        }

        btnOk.setOnClickListener {
            val h = pickerHours.value
            val m = pickerMinutes.value.coerceAtLeast(if (h == 0) 1 else 0)
            host.viewModel.updateTargetDurationSeconds(h * 3600L + m * 60L)
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }


    private fun showChecklistManagerDialog() {
        val accent = host.accentColor
        val dialogView = LayoutInflater.from(host.hostContext())
            .inflate(R.layout.dialog_manage_checklist, null)

        val dialogRecyclerView = dialogView.findViewById<RecyclerView>(R.id.dialogChecklistRecyclerView)
        val dialogInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.dialogChecklistInputLayout)
        val etItem = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDialogChecklistItem)
        val btnAdd = dialogView.findViewById<ImageButton>(R.id.btnDialogAddChecklistItem)
        val btnDone = dialogView.findViewById<android.widget.Button>(R.id.btnDialogChecklistDone)

        // Tint components to accent color
        dialogInputLayout.setBoxStrokeColor(accent)
        dialogInputLayout.setHintTextColor(ColorStateList.valueOf(accent))
        btnAdd.backgroundTintList = ColorStateList.valueOf(accent)


        // Tint cursor and highlight color to match task accent color
        dialogInputLayout.cursorColor = ColorStateList.valueOf(accent)
        etItem.textCursorDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accent)
            setSize(host.hostDpToPx(2), 1)
        }
        etItem.highlightColor = ColorUtils.setAlphaComponent(accent, 48)

        var editingIndex: Int? = null

        // Wire Adapter and touch helper inside the Dialog
        var itemTouchHelper: ItemTouchHelper? = null
        val adapter = ChecklistAdapter(
            onRemoveItem = { index ->
                if (editingIndex == index) {
                    editingIndex = null
                    etItem.setText("")
                    btnAdd.setImageResource(R.drawable.ic_add)
                }
                host.viewModel.removeChecklistItem(index)
            },
            onEditItem = { index, text ->
                editingIndex = index
                etItem.setText(text)
                etItem.setSelection(text.length)
                etItem.requestFocus()
                btnAdd.setImageResource(R.drawable.ic_check)
                val imm = host.hostContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etItem, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            },
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) }
        )
        dialogRecyclerView.layoutManager = LinearLayoutManager(host.hostContext())
        dialogRecyclerView.adapter = adapter

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
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                
                // Adjust editing index if the item currently being edited is moved
                val currentEditing = editingIndex
                if (currentEditing != null) {
                    if (fromPos == currentEditing) {
                        editingIndex = toPos
                    } else if (toPos == currentEditing) {
                        editingIndex = fromPos
                    }
                }
                
                host.viewModel.moveChecklistItem(fromPos, toPos)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
        itemTouchHelper.attachToRecyclerView(dialogRecyclerView)

        // Observe items list to keep dialog adapter updated
        var lastItemCount = 0
        val observerJob = host.hostLifecycleOwner().lifecycleScope.launch {
            host.viewModel.uiState.collect { state ->
                val items = state.checklistItems
                adapter.submitList(items.toList()) {
                    // Auto scroll to the end when a new item is added
                    if (items.size > lastItemCount) {
                        dialogRecyclerView.post {
                            dialogRecyclerView.scrollToPosition(items.size - 1)
                        }
                    }
                    lastItemCount = items.size
                }
            }
        }

        // Add/Edit item handlers
        btnAdd.isEnabled = false
        etItem.doAfterTextChanged {
            btnAdd.isEnabled = !it.isNullOrBlank()
            if (it.isNullOrBlank() && editingIndex != null) {
                editingIndex = null
                btnAdd.setImageResource(R.drawable.ic_add)
            }
        }
        fun commitItem() {
            val text = etItem.text.toString().trim()
            if (text.isNotBlank()) {
                val currentEditing = editingIndex
                if (currentEditing != null) {
                    host.viewModel.updateChecklistItem(currentEditing, text)
                    editingIndex = null
                    btnAdd.setImageResource(R.drawable.ic_add)
                } else {
                    host.viewModel.addChecklistItem(text)
                }
                etItem.setText("")
            }
        }
        btnAdd.setOnClickListener { commitItem() }
        etItem.setOnEditorActionListener { _, _, _ -> commitItem(); true }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(
            host.hostContext(),
            R.style.TaskBottomSheetDialogTheme
        )
        dialog.setContentView(dialogView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setOnDismissListener { observerJob.cancel() }

        // Configure parent container background to transparent to prevent double rounded corners / border peeks
        val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // Automatically pan the entire window above the keyboard
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        // Prevent dismissal via swipe gestures and force full expanded state
        dialog.behavior.isHideable = false
        dialog.behavior.isDraggable = false
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        btnDone.setOnClickListener { dialog.dismiss() }
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

            // Update custom underline accent color
            countUnderline.setBackgroundColor(activeColor)
        }
    }

    private fun showExtraFieldsFor(type: TrackingType) {
        with(host.binding.taskTrackingType) {
            countExtraContainer.isVisible = type == TrackingType.COUNT
            timerExtraContainer.isVisible = type == TrackingType.TIMER
            checklistExtraContainer.isVisible = type == TrackingType.CHECKLIST
        }
    }
}
