package com.anitech.growdaily.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.growdaily.R
import com.anitech.growdaily.databinding.DialogManageTaskCalendarBinding
import com.anitech.growdaily.databinding.ItemCalendarDayBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class ManageTaskCalendarDialog : DialogFragment() {

    interface Callback {
        fun onToggle(date: String, shouldAdd: Boolean)
    }

    private var callback: Callback? = null
    private var _binding: DialogManageTaskCalendarBinding? = null
    private val binding get() = _binding!!

    fun setCallback(cb: Callback) {
        callback = cb
    }

    private val originalDate: LocalDate by lazy {
        LocalDate.parse(requireArguments().getString(ARG_ORIGINAL_DATE))
    }

    private val accentColor: Int by lazy {
        requireArguments().getInt(ARG_ACCENT_COLOR)
    }

    private val activeDates: MutableSet<String> by lazy {
        requireArguments().getStringArrayList(ARG_ACTIVE_DATES)?.toMutableSet() ?: mutableSetOf()
    }

    private var currentMonth: YearMonth = YearMonth.now()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogManageTaskCalendarBinding.inflate(LayoutInflater.from(requireContext()))
        currentMonth = YearMonth.from(originalDate)

        val daysAdapter = DaysAdapter(accentColor) { day ->
            if (day != null && day != originalDate) {
                val key = day.toString()
                val currentlyActive = activeDates.contains(key)
                val shouldAdd = !currentlyActive
                callback?.onToggle(key, shouldAdd)
                if (shouldAdd) activeDates.add(key) else activeDates.remove(key)
                renderMonth()
            }
        }

        binding.recyclerDays.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = daysAdapter
            itemAnimator = null
        }

        binding.btnPrevMonth.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            renderMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            renderMonth()
        }

        binding.indicatorOriginal.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.indicatorActive.backgroundTintList = ColorStateList.valueOf(accentColor)

        renderMonth()

        return MaterialAlertDialogBuilder(requireContext(), R.style.Theme_GrowDaily_Picker_Base)
            .setView(binding.root)
            .setPositiveButton(R.string.picker_set_date, null)
            .create()
    }

    private fun renderMonth() {
        val monthYearText = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}"
        binding.txtMonth.text = monthYearText
        (binding.recyclerDays.adapter as? DaysAdapter)?.submit(currentMonth, originalDate, activeDates)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        callback = null
        _binding = null
    }

    private class DaysAdapter(
        private val accentColor: Int,
        private val onClick: (LocalDate?) -> Unit
    ) : RecyclerView.Adapter<DaysAdapter.VH>() {

        private var items: List<LocalDate?> = emptyList()
        private var original: LocalDate = LocalDate.now()
        private var active: Set<String> = emptySet()
        private var month: YearMonth = YearMonth.now()

        fun submit(month: YearMonth, original: LocalDate, activeDates: Set<String>) {
            this.month = month
            this.original = original
            this.active = activeDates
            this.items = buildMonthCells(month)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], original, active, accentColor, month)
        }

        override fun getItemCount(): Int = items.size

        class VH(
            private val binding: ItemCalendarDayBinding,
            private val onClick: (LocalDate?) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(
                day: LocalDate?,
                original: LocalDate,
                active: Set<String>,
                accentColor: Int,
                month: YearMonth
            ) {
                if (day == null) {
                    binding.txtDay.text = ""
                    binding.txtDay.background = null
                    binding.root.setOnClickListener(null)
                    return
                }

                binding.txtDay.text = day.dayOfMonth.toString()
                val isOutside = day.month != month.month
                binding.root.alpha = if (isOutside) 0.3f else 1f

                val key = day.toString()
                val isOriginal = day == original
                val isActive = active.contains(key)
                val isToday = day == LocalDate.now()

                when {
                    isOriginal -> {
                        binding.txtDay.background = solidCircle(accentColor)
                        binding.txtDay.setTextColor(Color.WHITE)
                        binding.txtDay.setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    isActive -> {
                        binding.txtDay.background = strokeCircle(accentColor)
                        binding.txtDay.setTextColor(accentColor)
                        binding.txtDay.setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    isToday -> {
                        binding.txtDay.background = todayCircle(accentColor)
                        binding.txtDay.setTextColor(accentColor)
                        binding.txtDay.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                    else -> {
                        binding.txtDay.background = null
                        binding.txtDay.setTextColor(ContextCompat.getColor(binding.root.context, R.color.task_text_primary))
                        binding.txtDay.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                }

                binding.root.setOnClickListener { onClick(day) }
            }

            private fun solidCircle(color: Int) = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            private fun strokeCircle(color: Int) = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(color, 25))
                setStroke((binding.root.resources.displayMetrics.density * 2).toInt(), color)
            }

            private fun todayCircle(color: Int) = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((binding.root.resources.displayMetrics.density * 1).toInt(), ColorUtils.setAlphaComponent(color, 120))
            }
        }

        private fun buildMonthCells(month: YearMonth): List<LocalDate?> {
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value % 7 // Sunday=0
            val start = first.minusDays(offset.toLong())
            return (0 until 42).map { i -> start.plusDays(i.toLong()) }
        }
    }

    companion object {
        private const val ARG_ORIGINAL_DATE = "originalDate"
        private const val ARG_ACTIVE_DATES = "activeDates"
        private const val ARG_ACCENT_COLOR = "accentColor"

        fun newInstance(
            originalDate: String,
            activeDates: Set<String>,
            accentColor: Int
        ): ManageTaskCalendarDialog {
            return ManageTaskCalendarDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORIGINAL_DATE, originalDate)
                    putStringArrayList(ARG_ACTIVE_DATES, ArrayList(activeDates))
                    putInt(ARG_ACCENT_COLOR, accentColor)
                }
            }
        }
    }
}
