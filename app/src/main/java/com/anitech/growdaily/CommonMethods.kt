package com.anitech.growdaily

import com.anitech.growdaily.data_class.DailyScore
import com.anitech.growdaily.data_class.TaskEntity
import com.anitech.growdaily.enum_class.DateMode
import com.anitech.growdaily.enum_class.RepeatType
import com.anitech.growdaily.enum_class.TaskType
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class CommonMethods {
    companion object {
        object CalculateDailyScoreForDate {
        }

        const val DATE_FORMATE: String = "yyyy-MM-dd"
        val sdf = DateTimeFormatter.ofPattern(DATE_FORMATE)


        fun isTodayDate(currentDate: String): Boolean {
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()
            return date.isEqual(today)
        }

        fun isPastDate(currentDate: String): Boolean {
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()
            return date.isBefore(today)
        }

        fun isFutureDate(currentDate: String): Boolean {
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()
            return date.isAfter(today)
        }

        fun getDateMode(currentDate: String): DateMode {
            val date = LocalDate.parse(currentDate, sdf)
            val today = LocalDate.now()

            return when {
                date.isEqual(today) -> DateMode.TODAY
                date.isBefore(today) -> DateMode.PAST
                else -> DateMode.FUTURE
            }
        }

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
            val date = LocalDate.parse(currentDate, sdf)
            val tomorrow = LocalDate.now().plusDays(1)
            return date.isEqual(tomorrow)
        }


        fun isYesterdayDate(currentDate: String): Boolean {
            val date = LocalDate.parse(currentDate, sdf)
            val yesterday = LocalDate.now().minusDays(1)
            return date.isEqual(yesterday)
        }


        fun filterTasksForDate(
            tasks: List<TaskEntity>,
            dateString: String
        ): List<TaskEntity> {
            return tasks.filter { task ->
                when (task.taskType) {

                    TaskType.DAILY ->
                        isTaskActiveOnDate(task, dateString)

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
                        isTaskActiveOnDate(task, date)

                    TaskType.DAY ->
                        task.taskAddedDate == date

                    TaskType.UNTIL_COMPLETE ->
                        task.taskAddedDate <= date &&
                                (task.taskRemovedDate == null || task.taskRemovedDate >= date)
                }
            }
        }

        fun isTaskActiveOnDate(task: TaskEntity, dateString: String): Boolean {
            if (task.taskAddedDate > dateString) return false
            if (task.taskRemovedDate != null && task.taskRemovedDate < dateString) return false
            if (task.taskType != TaskType.DAILY) return true
            return matchesRepeatPattern(task, dateString)
        }

        fun isWithinTaskLifetime(task: TaskEntity, dateString: String): Boolean {
            if (task.taskAddedDate > dateString) return false
            if (task.taskRemovedDate != null && task.taskRemovedDate < dateString) return false
            return true
        }

        fun previousScheduledDate(task: TaskEntity, dateString: String): String? {
            if (task.taskType != TaskType.DAILY) return null
            val current = runCatching { LocalDate.parse(dateString, sdf) }.getOrNull() ?: return null
            var cursor = current.minusDays(1)
            val start = runCatching { LocalDate.parse(task.taskAddedDate, sdf) }.getOrNull() ?: return null

            while (!cursor.isBefore(start)) {
                val candidate = cursor.format(sdf)
                if (isTaskActiveOnDate(task, candidate)) {
                    return candidate
                }
                cursor = cursor.minusDays(1)
            }
            return null
        }

        fun matchesRepeatPattern(task: TaskEntity, dateString: String): Boolean {
            if (task.taskType != TaskType.DAILY) return true

            val repeatType = task.repeatType ?: RepeatType.DAILY
            if (repeatType == RepeatType.DAILY) return true

            val date = runCatching { LocalDate.parse(dateString, sdf) }.getOrNull() ?: return true
            val values = parseRepeatDays(task.repeatDays)

            return when (repeatType) {
                RepeatType.DAILY -> true
                RepeatType.DAYS_OF_WEEK -> values.contains(date.dayOfWeek.value)
                RepeatType.DAYS_OF_MONTH -> values.contains(date.dayOfMonth)
            }
        }

        fun parseRepeatDays(raw: String?): List<Int> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .distinct()
                .sorted()
        }

        fun formatRepeatSummary(repeatType: RepeatType?, repeatDays: String?): String {
            return when (repeatType ?: RepeatType.DAILY) {
                RepeatType.DAILY -> "Every day"
                RepeatType.DAYS_OF_WEEK -> {
                    val labels = parseRepeatDays(repeatDays)
                        .mapNotNull { value ->
                            DayOfWeek.entries.firstOrNull { it.value == value }
                                ?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        }
                    if (labels.isEmpty()) "Specific weekdays" else labels.joinToString(", ")
                }
                RepeatType.DAYS_OF_MONTH -> {
                    val days = parseRepeatDays(repeatDays)
                    if (days.isEmpty()) "Specific month days"
                    else days.joinToString(", ") { ordinalDay(it) }
                }
            }
        }

        fun serializeRepeatDays(days: List<Int>): String? {
            val cleaned = days.distinct().sorted()
            return if (cleaned.isEmpty()) null else cleaned.joinToString(",")
        }

        fun scheduledDatesBetween(
            task: TaskEntity,
            startInclusive: LocalDate,
            endInclusive: LocalDate
        ): Set<LocalDate> {
            if (endInclusive.isBefore(startInclusive)) return emptySet()

            val scheduled = mutableSetOf<LocalDate>()
            var date = startInclusive
            while (!date.isAfter(endInclusive)) {
                if (isTaskActiveOnDate(task, date.format(sdf))) {
                    scheduled.add(date)
                }
                date = date.plusDays(1)
            }
            return scheduled
        }

        fun calculateCurrentStreak(
            taskStart: LocalDate,
            completedDates: Set<LocalDate>,
            scheduledDates: Set<LocalDate>? = null
        ): Int {

            val allScheduledDates = scheduledDates ?: buildSet {
                var date = taskStart
                val today = LocalDate.now()
                while (!date.isAfter(today)) {
                    add(date)
                    date = date.plusDays(1)
                }
            }

            val today = LocalDate.now()
            val latestScheduled = allScheduledDates
                .filter { !it.isAfter(today) }
                .maxOrNull() ?: return 0

            var date = if (completedDates.contains(latestScheduled)) {
                latestScheduled
            } else {
                previousScheduledDate(latestScheduled, allScheduledDates) ?: return 0
            }

            var streak = 0
            while (!date.isBefore(taskStart) && allScheduledDates.contains(date)) {
                if (!completedDates.contains(date)) break
                streak++
                date = previousScheduledDate(date, allScheduledDates) ?: break
            }

            return streak
        }

        fun calculateBestStreak(
            taskStart: LocalDate,
            completedDates: Set<LocalDate>,
            scheduledDates: Set<LocalDate>? = null
        ): Int {

            val datesToCheck = (scheduledDates ?: buildSet {
                var date = taskStart
                val today = LocalDate.now()
                while (!date.isAfter(today)) {
                    add(date)
                    date = date.plusDays(1)
                }
            }).filter { !it.isBefore(taskStart) }.sorted()

            var best = 0
            var current = 0

            datesToCheck.forEach { date ->
                if (completedDates.contains(date)) {
                    current++
                    best = maxOf(best, current)
                } else {
                    current = 0
                }
            }

            return best
        }

        private fun previousScheduledDate(
            current: LocalDate,
            scheduledDates: Set<LocalDate>
        ): LocalDate? {
            return scheduledDates
                .filter { it.isBefore(current) }
                .maxOrNull()
        }

        private fun ordinalDay(day: Int): String {
            val suffix = if (day % 100 in 11..13) {
                "th"
            } else {
                when (day % 10) {
                    1 -> "st"
                    2 -> "nd"
                    3 -> "rd"
                    else -> "th"
                }
            }
            return "$day$suffix"
        }

        fun calculateScoreForDate(
            tasks: List<TaskEntity>,
            date: String,
            completionMap: Map<String, Map<String, Int>>
        ): Float {

            val tasksForDate = filterTasksForDate(tasks, date)
            if (tasksForDate.isEmpty()) return 0f

            val completionForDate = completionMap[date] ?: emptyMap()

            var totalWeight = 0f
            var achievedWeight = 0f

            for (task in tasksForDate) {

                val taskWeight = task.weight.weight.toFloat()
                totalWeight += taskWeight

                val count = completionForDate[task.id] ?: 0
                val target = task.dailyTargetCount.coerceAtLeast(1)
                val progressRatio = (count.coerceAtMost(target).toFloat() / target.toFloat())

                achievedWeight += taskWeight * progressRatio
            }

            if (totalWeight == 0f) return 0f

            val rawScore =
                (achievedWeight / totalWeight) * 10f

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
            val today = LocalDate.now()

            while (!currentDate.isAfter(endDate)) {
                if (!currentDate.isAfter(today)) {
                    val dateStr = currentDate.toString()
                    val tasksForDate = filterTasksForDate(tasks, dateStr)
                    if (tasksForDate.isNotEmpty()) {
                        val score = calculateScoreForDate(
                            tasks,
                            dateStr,
                            completionMap
                        )
                        dailyScores.add(score)
                    }
                }

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

        fun applySmartTimeOrder(tasks: List<TaskEntity>): List<TaskEntity> {

            val baseList = tasks.sortedBy { it.manualOrder }.toMutableList()

            val timedTasks = baseList
                .filter { it.scheduledMinutes != null }
                .sortedBy { it.scheduledMinutes }

            var timedIndex = 0

            for (i in baseList.indices) {
                if (baseList[i].scheduledMinutes != null) {
                    baseList[i] = timedTasks[timedIndex++]
                }
            }

            return baseList
        }

        private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

         fun timeToMinutes(time: String?): Int? {
            if (time.isNullOrBlank()) return null

            return try {
                val date = timeFormatter.parse(time) ?: return null

                val cal = Calendar.getInstance().apply { this.time = date }

                cal.get(Calendar.HOUR_OF_DAY) * 60 +
                        cal.get(Calendar.MINUTE)

            } catch (e: Exception) {
                null
            }
        }

        fun currentMinutes(): Int {
            return timeToMinutes(getCurrentTime()) ?: 0
        }


        fun getCurrentTime(): String {
            return timeFormatter.format(Date())
        }


    }


}
