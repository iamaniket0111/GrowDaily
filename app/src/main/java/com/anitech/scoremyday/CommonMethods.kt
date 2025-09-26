package com.anitech.scoremyday

import com.anitech.scoremyday.data_class.DailyTask
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class CommonMethods {
    companion object {
        private const val DATE_FORMATE:String ="yyyy-MM-dd"

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
         fun filterTasks(tasks: List<DailyTask>, date: String): List<DailyTask> {
            return tasks.filter { task ->
                if (task.isDaily) {
                    task.taskAddedDate <= date &&
                            (task.taskRemovedDate == null || task.taskRemovedDate > date)
                } else {
                    task.taskAddedDate == date
                }
            }
        }

    }
    
}
