package com.anitech.scoremyday.fragment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.scoremyday.CommonMethods
import com.anitech.scoremyday.R
import com.anitech.scoremyday.adapter.ConditionCheckAdapter
import com.anitech.scoremyday.data_class.DailyTask
import com.anitech.scoremyday.data_class.DayNoteEntity
import com.anitech.scoremyday.database.AppViewModel
import com.anitech.scoremyday.databinding.FragmentAddTaskBinding
import com.anitech.scoremyday.enum_class.TaskColor
import com.anitech.scoremyday.enum_class.TaskIcon
import com.anitech.scoremyday.enum_class.TaskWeight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID


class AddTaskFragment : Fragment() {
    private var _binding: FragmentAddTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private val args: AddTaskFragmentArgs by navArgs()

    var selectedDrawableResId: String = "TROPHY"
    var selectedBackgroundColor: String = "DARK_BLUE"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argTask = args.task
        if (argTask != null) {
            binding.editTextTitle.setText(argTask.title)
            if (argTask.note != null) {
                binding.editTextNote.setText(argTask.note)
            }
            setSelectedTaskWeight(argTask.weight)
            val icon = TaskIcon.valueOf(argTask.iconResId)
            val color = TaskColor.valueOf(argTask.colorCode)

            binding.imageProfile.setImageResource(icon.resId)
            binding.imageProfile.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color.resId))

            selectedDrawableResId = argTask.iconResId
            selectedBackgroundColor = argTask.colorCode

            if (argTask.isDaily) {
                binding.isDaily.isChecked = true
                binding.isDaily.isEnabled = false
            } else {
                binding.isDaily.isChecked = false
            }// FIXME: should we make it editable if its added today??taki previous datapar vaise bhi kuchh farak nhi pdega

            //day note
            viewModel.noteForDate.observe(viewLifecycleOwner) { noteEntity ->
                if (noteEntity != null) {
                    // Agar note mila hai
                    Log.d("AddTaskFragment", "Note Text: ${noteEntity.note}")
                    binding.editTextDayNote.setText(noteEntity.note)  // maan lo content field hai
                    Log.d("AddTaskFragment", "Note Text: ${noteEntity.note}")

                } else {
                    Log.d("AddTaskFragment", "Note Text: should be empty")
                    binding.editTextDayNote.setText("")
                }
            }

            val taskId = argTask.id
            val date = CommonMethods.Companion.getTodayDate()
            viewModel.getNoteForDate(taskId, date)
        }

        val conditionCheckAdapter = ConditionCheckAdapter(emptyList(),argTask?.isDaily ?: true) { id, isChecked ->
            Log.d("Condition", "Condition $id selected = $isChecked")
        }

        binding.rvCheckCondition.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvCheckCondition.adapter = conditionCheckAdapter

        val preselectedConditionIds = argTask?.conditionIds ?: emptyList()

        viewModel.getAllConditions().observe(viewLifecycleOwner) { conditions ->
            conditionCheckAdapter.updateData(conditions)
            if (preselectedConditionIds.isNotEmpty()) {
                conditionCheckAdapter.setPreselectedIds(preselectedConditionIds)
            }
        }


        binding.imageProfile.setOnClickListener {
            val bundle = Bundle().apply {
                putString("selectedIcon", selectedDrawableResId)   // e.g. "GRADUATION_CAP"
                putString("selectedColor", selectedBackgroundColor)  // e.g. "TEAL"
            }
            findNavController().navigate(
                R.id.imageProviderFragment,
                bundle
            )

        }

        binding.dateBtn.setOnClickListener {
            showDatePickerDialog()
        }

        binding.timeBtn.setOnClickListener {
            showTimePickerDialog()
        }

        binding.isDaily.setOnCheckedChangeListener { _, isChecked ->
            conditionCheckAdapter.setCheckBoxEnabled(isChecked)
        }


        binding.buttonSave.setOnClickListener {
            val taskId = UUID.randomUUID().toString()
            val title = binding.editTextTitle.text.toString().trim()
            val note = binding.editTextNote.text.toString().trim()
            //  val scheduledTime = binding.editTextTime.text.toString().trim()
            val reminderOn = binding.switchReminder.isChecked
            val isDaily = binding.isDaily.isChecked
            val todayDate = CommonMethods.Companion.getTodayDate()

            val selectedConditionIds = conditionCheckAdapter.getSelectedIds()

            // Validation
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val userNote = binding.editTextDayNote.text.toString().trim()
            val iconEnum = TaskIcon.valueOf(selectedDrawableResId)
            val colorEnum = TaskColor.valueOf(selectedBackgroundColor)
            if (argTask == null) {
                val task = DailyTask(
                    id = taskId,
                    title = title,
                    note = note.ifEmpty { null },
                    isCompleted = false,
                    weight = getSelectedTaskWeight(),
                    scheduledTime = null,
                    completedTime = null,
                    taskAddedDate = todayDate,
                    null,
                    reminderEnabled = reminderOn,
                    completedDates = emptyList(),
                    conditionIds = selectedConditionIds,
                    iconResId = selectedDrawableResId,
                    colorCode = selectedBackgroundColor,
                    isDaily
                )

                viewModel.insertTask(task)
                //day note
                val noteEntity = DayNoteEntity(
                    id = 0, taskOwnerId = taskId, date = todayDate, note = userNote
                )

                if (userNote.isNotEmpty()) {
                    viewModel.insertDayNote(noteEntity)
                }

                Toast.makeText(requireContext(), "Task saved", Toast.LENGTH_SHORT).show()
                Log.d("AddTaskFragment", "insert section")
            } else {
                val task = DailyTask(
                    id = argTask.id,
                    title = title,
                    note = note.ifEmpty { null },
                    isCompleted = false,
                    weight = getSelectedTaskWeight(),
                    scheduledTime = null,
                    completedTime = null,
                    taskAddedDate = argTask.taskAddedDate,
                    taskRemovedDate = argTask.taskRemovedDate,
                    reminderEnabled = reminderOn,
                    completedDates = emptyList(),
                    conditionIds = selectedConditionIds,
                    iconResId = selectedDrawableResId,
                    colorCode = selectedBackgroundColor,
                    isDaily
                )

                viewModel.updateTask(task)
                //day note
                Log.d("AddTaskFragment", "update section")

                viewModel.getNoteForDateOnce(argTask.id, todayDate) { note ->
                    if (note != null) {
                        Log.d("AddTaskFragment", "note  exists")
                        if (userNote.isNotEmpty()) {
                            Log.d("AddTaskFragment", " not empty, updating it")
                            note.note = userNote
                            viewModel.updateDayNote(note)
                        } else {
                            Log.d("AddTaskFragment", "empty, deleting it")
                            viewModel.deleteDayNote(note)
                        }
                    } else {
                        // TODO: Note not found, insert a new one
                        Log.d("AddTaskFragment", "not found, inserting")
                        val noteEntity = DayNoteEntity(
                            id = 0, taskOwnerId = argTask.id, date = todayDate, note = userNote
                        )
                        if (userNote.isNotEmpty()) {
                            viewModel.insertDayNote(noteEntity)
                        }
                    }
                }
                Toast.makeText(requireContext(), "Task Updated", Toast.LENGTH_SHORT).show()
            }

            requireActivity().onBackPressedDispatcher.onBackPressed() // go back or you can navigate
        }

        parentFragmentManager.setFragmentResultListener(
            "iconPickerResult", viewLifecycleOwner
        ) { _, bundle ->
            val iconName = bundle.getString("drawableResId")
            val colorName = bundle.getString("backgroundColor")

            val selectedIcon = iconName?.let { TaskIcon.valueOf(it) }
            val selectedColor = colorName?.let { TaskColor.valueOf(it) }

            if (selectedIcon != null && selectedColor != null) {
                binding.imageProfile.setImageResource(selectedIcon.resId)
                binding.imageProfile.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        selectedColor.resId
                    )
                )
                selectedDrawableResId = iconName
                selectedBackgroundColor = colorName
                Log.d("AddTaskFragment", "Icon: $iconName \t Color: $colorName")
            }
        }
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                // Calendar set with selected time
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, selectedHour)
                cal.set(Calendar.MINUTE, selectedMinute)

                // Format time in 12-hour with AM/PM
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.txtTime.text = sdf.format(cal.time)
            },
            hourOfDay, minute, false // false = 12-hour format dialog
        )

        timePickerDialog.setOnShowListener {
            val positive = timePickerDialog.getButton(TimePickerDialog.BUTTON_POSITIVE)
            val negative = timePickerDialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)

            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)

            positive?.setTextColor(color)
            negative?.setTextColor(color)
        }
        timePickerDialog.show()
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(), // agar Fragment me ho
            { _, selectedYear, selectedMonth, selectedDay ->
                val date = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, selectedDay)
                binding.txtDate.text = sdf.format(cal.time)
            },
            year, month, day
        )

        datePickerDialog.setOnShowListener {
            val positive = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)
            val negative = datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)
            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
            positive?.setTextColor(color)
            negative?.setTextColor(color)
        }

        datePickerDialog.show()
    }

    private fun getSelectedTaskWeight(): TaskWeight {
        return when (binding.radioGroupScore.checkedRadioButtonId) {
            R.id.radioVeryLow -> TaskWeight.VERY_LOW
            R.id.radioLow -> TaskWeight.LOW
            R.id.radioHigh -> TaskWeight.HIGH
            R.id.radioVeryHigh -> TaskWeight.VERY_HIGH
            else -> TaskWeight.VERY_LOW
        }
    }

    private fun setSelectedTaskWeight(weight: TaskWeight) {
        when (weight) {
            TaskWeight.VERY_LOW -> binding.radioGroupScore.check(R.id.radioVeryLow)
            TaskWeight.LOW -> binding.radioGroupScore.check(R.id.radioLow)
            TaskWeight.HIGH -> binding.radioGroupScore.check(R.id.radioHigh)
            TaskWeight.VERY_HIGH -> binding.radioGroupScore.check(R.id.radioVeryHigh)
        }
    }
}