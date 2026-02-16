package com.anitech.growdaily

import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.TaskType
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class CommonMethods {
    companion object {
        object CalculateDailyScoreForDate {
        }

        const val DATE_FORMATE: String = "yyyy-MM-dd"

        fun getTodayDate(): String {
            return SimpleDateFormat(DATE_FORMATE, Locale.getDefault()).format(Date())
        }

        fun getPrevDate(currentDate: String): String {
            val date = LocalDate.parse(currentDate)
            val previousDate = date.minusDays(1)
            return previousDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // Format as "YYYY-MM-DD"
        }

        // Method to get the next date based on the given date
        fun getNextDate(currentDate: String): String {
            val date = LocalDate.parse(currentDate)
            val nextDate = date.plusDays(1)
            return nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE) // Format as "YYYY-MM-DD"
        }

        fun getTomorrowDate(): String {
            val tomorrow = LocalDate.now().plusDays(1)
            return tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE) // "YYYY-MM-DD"
        }

        fun getYesterdayDate(): String {
            val yesterday = LocalDate.now().minusDays(1)
            return yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE) // "YYYY-MM-DD"
        }

        fun isTomorrowDate(currentDate: String): Boolean {
            val sdf = DateTimeFormatter.ofPattern(DATE_FORMATE)
            val date = LocalDate.parse(currentDate, sdf)
            val tomorrow = LocalDate.now().plusDays(1)
            return date.isEqual(tomorrow)
        }

        fun isTodayDate(currentDate: String): Boolean {
            val sdf = DateTimeFormatter.ofPattern(DATE_FORMATE)
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()
            return date.isEqual(today)
        }

        fun isYesterdayDate(currentDate: String): Boolean {
            val sdf = DateTimeFormatter.ofPattern(DATE_FORMATE)
            val date = LocalDate.parse(currentDate, sdf)
            val yesterday = LocalDate.now().minusDays(1)
            return date.isEqual(yesterday)
        }

        fun isPastDate(currentDate: String): Boolean {
            val sdf = DateTimeFormatter.ofPattern(DATE_FORMATE)
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()
            return date.isBefore(today)
        }

        fun isFutureDate(currentDate: String): Boolean {
            val sdf = DateTimeFormatter.ofPattern(DATE_FORMATE)
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()
            return date.isAfter(today)
        }


        fun filterTasksForDate(
            tasks: List<TaskEntity>,
            dateString: String
        ): List<TaskEntity> {
            return tasks.filter { task ->
                when (task.taskType) {

                    TaskType.DAILY ->
                        task.taskAddedDate <= dateString &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= dateString)

                    TaskType.DAY ->
                        task.taskAddedDate == dateString

                    TaskType.UNTIL_COMPLETE ->
                        false   // week / month / bar me ignore
                }
            }
        }

        fun calculateDailyScoresThisWeek(
            tasks: List<TaskEntity>,
            currentTodoDate: String,
            completionMap: Map<String, Map<String, Int>>
        ): List<DailyScore> {

            val dailyScores = mutableListOf<DailyScore>()

            val selected = LocalDate.parse(currentTodoDate)

            val startMonday =
                selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val endSunday = startMonday.plusDays(6)

            var currentDate = startMonday
            while (!currentDate.isAfter(endSunday)) {

                val dateString = currentDate.toString()
                val tasksForDate = filterTasksForDate(tasks, dateString)

                val completionForDate = completionMap[dateString] ?: emptyMap()

                var totalWeight = 0f
                var completedWeight = 0f

                for (task in tasksForDate) {

                    totalWeight += task.weight.weight

                    val count = completionForDate[task.id] ?: 0
                    val target = task.dailyTargetCount.coerceAtLeast(1)

                    if (count >= target) {
                        completedWeight += task.weight.weight
                    }
                }

                val score =
                    if (totalWeight > 0f) {
                        ((completedWeight / totalWeight) * 100)
                            .roundToInt() / 10f
                    } else 0f

                val weekDay =
                    currentDate.dayOfWeek.getDisplayName(
                        TextStyle.SHORT,
                        Locale.ENGLISH
                    )

                dailyScores.add(
                    DailyScore(
                        date = dateString,
                        dayText = weekDay,
                        monthDayText = "",
                        score = score,
                        taskCount = tasksForDate.size
                    )
                )

                currentDate = currentDate.plusDays(1)
            }

            return dailyScores
        }


        fun filterTasks( //moved here from repository
            tasks: List<TaskEntity>,
            date: String
        ): List<TaskEntity> {

            return tasks.filter { task ->
                when (task.taskType) {

                    TaskType.DAILY ->
                        task.taskAddedDate <= date &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date)

                    TaskType.DAY ->
                        task.taskAddedDate == date

                    TaskType.UNTIL_COMPLETE ->
                        task.taskAddedDate <= date &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date)
                }
            }
        }



        fun calculateScoreForDate(
            tasks: List<TaskEntity>,
            date: String,
            completionMap: Map<String, Map<String, Int>>
        ): Float {

            val tasksForDate = filterTasksForDate(tasks, date)
            if (tasksForDate.isEmpty()) return 0f

            val completionForDate = completionMap[date] ?: emptyMap()

            var totalWeight = 0
            var completedWeight = 0

            for (task in tasksForDate) {

                totalWeight += task.weight.weight

                val count = completionForDate[task.id] ?: 0
                val target = task.dailyTargetCount.coerceAtLeast(1)

                if (count >= target) {
                    completedWeight += task.weight.weight
                }
            }

            if (totalWeight == 0) return 0f

            val rawScore =
                (completedWeight.toFloat() / totalWeight.toFloat()) * 10f

            return ((rawScore * 10).roundToInt()) / 10f
        }


        fun calculateAggregateScore(
            tasks: List<TaskEntity>,
            startDate: LocalDate,
            endDate: LocalDate,
            completionMap: Map<String, Map<String, Int>>
        ): Float {


            val dailyScores = mutableListOf<Float>()
            var currentDate = startDate

            while (!currentDate.isAfter(endDate)) {

                val dateStr = currentDate.toString()

                val score = calculateScoreForDate(
                    tasks,
                    dateStr,
                    completionMap
                )

                if (score > 0f) dailyScores.add(score)

                currentDate = currentDate.plusDays(1)
            }

            if (dailyScores.isEmpty()) return 0f

            return ((dailyScores.sum() / dailyScores.size) * 10)
                .roundToInt() / 10f
        }



        fun formatDate(inputDate: String): String {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            return try {
                val date = inputFormat.parse(inputDate)
                if (date != null) outputFormat.format(date) else ""
            } catch (e: Exception) {
                ""
            }
        }


    }


}


//    private fun showTimePickerDialog(triggerSwitch: SwitchCompat?) {
//        var hourOfDay: Int
//        var minute: Int
//
//        val currentTimeText = binding.txtTime.text.toString().trim()
//        if (currentTimeText != "--") {
//            try {
//                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
//                val date = sdf.parse(currentTimeText)
//                val cal = Calendar.getInstance().apply { time = date!! }
//                hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
//                minute = cal.get(Calendar.MINUTE)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                val calendar = Calendar.getInstance()
//                calendar.add(Calendar.HOUR_OF_DAY, 1)
//                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
//                minute = calendar.get(Calendar.MINUTE)
//            }
//        } else {
//            val calendar = Calendar.getInstance()
//            calendar.add(Calendar.HOUR_OF_DAY, 1)
//            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
//            minute = calendar.get(Calendar.MINUTE)
//        }
//
//        var isTimeSelected = false
//
//        val timePickerDialog = TimePickerDialog(
//            requireContext(),
//            { _, selectedHour, selectedMinute ->
//                isTimeSelected = true
//                val cal = Calendar.getInstance()
//                cal.set(Calendar.HOUR_OF_DAY, selectedHour)
//                cal.set(Calendar.MINUTE, selectedMinute)
//                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
//                binding.txtTime.text = sdf.format(cal.time)
//            },
//            hourOfDay, minute, false
//        )
//
//        timePickerDialog.setCancelable(true)
//        timePickerDialog.setCanceledOnTouchOutside(true)
//
//        timePickerDialog.setOnCancelListener {
//            if (triggerSwitch?.isChecked == true) triggerSwitch.isChecked = false
//        }
//
//        timePickerDialog.setOnDismissListener {
//            if (!isTimeSelected && triggerSwitch?.isChecked == true) {
//                triggerSwitch.isChecked = false
//            }
//        }
//
//        timePickerDialog.setOnShowListener {
//            val positive = timePickerDialog.getButton(TimePickerDialog.BUTTON_POSITIVE)
//            val negative = timePickerDialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)
//            val color = ContextCompat.getColor(requireContext(), R.color.brand_blue)
//            positive?.setTextColor(color)
//            negative?.setTextColor(color)
//        }
//
//        timePickerDialog.show()
//    }
