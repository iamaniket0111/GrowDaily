package com.anitech.growdaily

import com.anitech.growdaily.data_class.DailyTask
import com.anitech.growdaily.data_class.DateItemEntity
import com.anitech.growdaily.enum_class.TaskType
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class CommonMethods {
    companion object {
        object CalculateDailyScoreForDate {

        }

        private const val DATE_FORMATE: String = "yyyy-MM-dd"

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

//        fun filterTasks(tasks: List<DailyTask>, date: String): List<DailyTask> {
//            return tasks.filter { task ->
//                if (task.isDaily) {
//                    task.taskAddedDate <= date &&
//                            (task.taskRemovedDate == null || task.taskRemovedDate > date)
//                } else {
//                    task.taskAddedDate == date
//                }
//            }
//        }

        fun filterTasks(tasks: List<DailyTask>, date: String): List<DailyTask> {
            return tasks.filter { task ->
                when (task.taskType) {
                    TaskType.DAILY -> {
                        task.taskAddedDate <= date &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date)
                    }
                    TaskType.DAY -> {
                        task.taskAddedDate == date
                    }
                    TaskType.UNTIL_COMPLETE -> {
                        val lastCompletedDate = task.completedDates.maxOrNull()
                        // Agar complete ho gaya hai aur date uske baad hai → hide
                        val isAfterCompletion = lastCompletedDate != null && date > lastCompletedDate

                        !isAfterCompletion &&
                                task.taskAddedDate <= date &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date)
                    }
                }
            }
        }

        fun filterTasksByCondition(
            tasks: List<DailyTask>,
            date: String,
            dateItemEntity: DateItemEntity
        ): List<DailyTask> {

            // FIXME: not tested yet 
            // Get all unique itemIds from all dateData
            val excludedIds = dateItemEntity.data
                .flatMap { it.itemIds }
                .toSet()

            return tasks.filter { task ->
                // Check latest completion date (if any)
                val lastCompletedDate = task.completedDates.maxOrNull()
                val isAfterCompletion = lastCompletedDate != null && date > lastCompletedDate

                if (isAfterCompletion) return@filter false

                when (task.taskType) {
                    TaskType.DAILY -> {
                        task.taskAddedDate <= date &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date) &&
                                !excludedIds.contains(task.id)
                    }
                    TaskType.DAY -> {
                        task.taskAddedDate == date &&
                                !excludedIds.contains(task.id)
                    }
                    TaskType.UNTIL_COMPLETE -> {
                        task.taskAddedDate <= date &&
                                !isAfterCompletion && // hide after last completed date
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date) &&
                                !excludedIds.contains(task.id)
                    }
                }
            }
        }





//        fun filterTasksByCondition(
//            tasks: List<DailyTask>,
//            date: String,
//            dateItemEntity: DateItemEntity
//        ): List<DailyTask> {
//            // Get all unique itemIds from all dateData
//            val excludedIds = dateItemEntity.data
//                .flatMap { it.itemIds }
//                .toSet()
//
//            return tasks.filter { task ->
//                if (task.isDaily) {
//                    task.taskAddedDate <= date &&
//                            (task.taskRemovedDate == null || task.taskRemovedDate > date) &&
//                            !excludedIds.contains(task.id) // exclude tasks whose id is in the set
//                } else {
//                    task.taskAddedDate == date
//                }
//            }
//        }


        fun calculateDailyScoreForDate(tasks: List<DailyTask>, date: String): String {//pass only filtered task or fix here instead
            val tasksOnDate = filterTasks(tasks, date)

            var totalWeight = 0f
            var completedWeight = 0f

            for (task in tasksOnDate) {
                totalWeight += task.weight.weight
                if (task.completedDates.contains(date)) {
                    completedWeight += task.weight.weight
                }
            }

            val score = if (totalWeight > 0f) {
                ((completedWeight / totalWeight) * 10f * 10).roundToInt() / 10f
            } else 0f

            // format: remove trailing ".0" if present
            return if (score % 1f == 0f) {
                score.toInt().toString()
            } else {
                String.format("%.1f", score)
            }
        }

    }



}
