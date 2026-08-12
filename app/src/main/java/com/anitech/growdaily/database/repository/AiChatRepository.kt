package com.anitech.growdaily.database.repository

import com.anitech.growdaily.BuildConfig
import com.anitech.growdaily.data_class.SuggestedTask
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiChatRepository(
    private val defaultApiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val gson = Gson()

    suspend fun generateResponse(
        userPrompt: String,
        userContextSummary: String? = null,
        customApiKey: String? = null,
        chatHistory: List<com.anitech.growdaily.data_class.AiChatMessage> = emptyList()
    ): Pair<String, List<SuggestedTask>?> = withContext(Dispatchers.IO) {
        val effectiveKey = customApiKey?.takeIf { it.isNotBlank() } ?: defaultApiKey

        if (effectiveKey.isBlank() || effectiveKey == "YOUR_GEMINI_API_KEY_HERE") {
            return@withContext Pair(
                "Please configure your Gemini API Key in GrowDaily Settings to start chatting with your AI Habit Assistant!",
                null
            )
        }

        val systemInstruction = """
            You are GrowDaily AI, an expert habit coach and daily planner inside the GrowDaily Android app.
            Your primary job is to turn the user's goals, schedules, routines, or prompts into structured, actionable daily tasks.

            Current App Context:
            $userContextSummary

            CRITICAL GUIDELINES FOR TASK GENERATION:
            1. Response Tone: Warm, encouraging, ultra-concise, and direct. Keep text introductions short (1-2 sentences max). Do NOT output verbose explanations or define concept mechanics. Present the response cleanly and get straight to the point!
            2. Schedule / Routine Parsing: If the user provides a full timetable or daily schedule, parse EVERY SINGLE item into a task card. Do NOT truncate or cap the list.
            3. Preserve Original Language in Titles: Keep task titles in the user's original language (English, Hinglish, Hindi, etc.). Do NOT translate task titles unless specifically asked by the user. Keep titles clean and concise.
            4. Smart Note Field (No Redundant Notes): Only populate the "note" field if there are explicit additional sub-instructions or details for that item. If no extra detail exists, set "note": null. Never repeat the title or dump redundant text into the note field.
            5. Time Formatting & Schedule Duration Window (STRICT RULE):
               - Extract start times into "scheduleTime": "hh:mm AM" or "hh:mm PM". If no start time was mentioned, set "scheduleTime": null.
               - If a time range is provided (e.g. "6:00 – 9:30 PM Study Session 4" or "7:45 – 8:05 AM Exercise"), calculate the duration in seconds between start time and end time into "targetDurationSeconds" (e.g. 12600 seconds for 3.5h, 1200 seconds for 20 mins). This sets the schedule block range on the app timeline ("06:00 PM - 09:30 PM").
               - If only a start time is given (e.g. "12:00 AM Sona"), set "scheduleTime": "12:00 AM", "targetDurationSeconds": null.
            6. Task Types & Day Tasks: Use "taskType": "DAY" for single-day tasks (e.g., "complete homework today"). Day tasks default to showUntilCompleted: true. Set "showUntilCompleted": false only if the user explicitly asks not to show it after today. Use "taskType": "DAILY" for repeating daily habits.
            7. Smart Tracking Types (BINARY vs TIMER vs COUNTER vs CHECKLIST):
               - BINARY (DEFAULT FOR ALL SCHEDULED TIMETABLE TASKS): ALWAYS default to "trackingType": "BINARY" for timetable items, study sessions, breaks, meals, routines, and daily activities — EVEN WHEN THEY HAVE A TIME RANGE (scheduleTime and targetDurationSeconds)! In GrowDaily, binary tasks display their schedule window on the timeline (e.g. "06:00 PM - 09:30 PM") and complete with a simple checkmark tap.
               - TIMER: ONLY set "trackingType": "TIMER" if the user explicitly asks for a stopwatch/countdown timer in their prompt (e.g., "track a 30m timer for meditation", "timed workout", "stopwatch for reading"). NEVER set "trackingType": "TIMER" just because a task has a start and end time window in a schedule!
               - COUNTER: If prompt mentions reps/count (e.g. "50 pushups" or "8 glasses of water"), set "trackingType": "COUNTER", "dailyTargetCount": 50.
               - CHECKLIST: If prompt mentions sub-steps/items under 1 task (e.g. "Workout: Warm up, Pushups, Stretch"), set "trackingType": "CHECKLIST", "checklistItems": ["Warm up", "Pushups", "Stretch"].
            8. JSON Output Format: Whenever you suggest or parse tasks, ALWAYS put a JSON block at the VERY END of your response formatted exactly as:
            ```json
            [
              {
                "title": "Study Session 4",
                "note": null,
                "taskType": "DAILY",
                "repeatType": "DAILY",
                "taskColor": "DARK_BLUE",
                "scheduleTime": "06:00 PM",
                "targetDurationSeconds": 12600,
                "trackingType": "BINARY"
              },
              {
                "title": "50 Pushups",
                "note": null,
                "taskType": "DAILY",
                "repeatType": "DAILY",
                "taskColor": "GREEN",
                "scheduleTime": null,
                "trackingType": "COUNTER",
                "dailyTargetCount": 50
              },
              {
                "title": "Meditation Timer",
                "note": null,
                "taskType": "DAILY",
                "repeatType": "DAILY",
                "taskColor": "PURPLE",
                "scheduleTime": "07:00 AM",
                "trackingType": "TIMER",
                "targetDurationSeconds": 1800
              }
            ]
            ```
            Valid taskType values: "DAILY", "DAY", "UNTIL_COMPLETE"
            Valid repeatType values: "DAILY", "DAYS_OF_WEEK", "DAYS_OF_MONTH"
            Valid taskColor values: "DARK_BLUE", "BLUE", "TEAL", "GREEN", "YELLOW", "ORANGE", "RED", "PURPLE"
            Valid trackingType values: "BINARY", "COUNT", "TIMER", "CHECKLIST"
        """.trimIndent()

        val historyContext = if (chatHistory.isNotEmpty()) {
            val validHistory = chatHistory.filter { !it.isLoading && !it.isError && it.text.isNotBlank() }.takeLast(6)
            if (validHistory.isNotEmpty()) {
                "RECENT CONVERSATION HISTORY:\n" + validHistory.joinToString("\n") { msg ->
                    val role = if (msg.sender == com.anitech.growdaily.data_class.ChatSender.USER) "User" else "AI"
                    val cleanText = msg.text.replace(Regex("```(?:json)?\\s*[\\s\\S]*?```"), "").trim()
                    "$role: $cleanText"
                } + "\n\n"
            } else ""
        } else ""

        val modelNames = listOf(
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-3.6-flash"
        )
        var responseText: String? = null
        var lastException: Throwable? = null

        for (modelName in modelNames) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = effectiveKey
                )
                val fullPrompt = "$systemInstruction\n\n${historyContext}Current User Question: $userPrompt"
                val response = generativeModel.generateContent(fullPrompt)
                val text = response.text
                if (!text.isNullOrBlank()) {
                    responseText = text
                    break
                }
            } catch (t: Throwable) {
                lastException = t
                // Continue to next model name because Google free tier quotas are per-model name!
            }
        }

        if (responseText != null) {
            val (cleanText, tasks) = parseSuggestedTasksFromText(responseText)
            Pair(cleanText, tasks)
        } else {
            val msg = lastException?.localizedMessage.orEmpty()
            val isDailyQuota = msg.contains("PerDay", ignoreCase = true) ||
                    msg.contains("limit: 20", ignoreCase = true) ||
                    msg.contains("GenerateRequestsPerDay", ignoreCase = true)

            val errorMsg = when {
                lastException is java.net.UnknownHostException || lastException is java.io.IOException ->
                    "No internet connection. Please check your network connection and try again."
                isDailyQuota ->
                    "Daily AI limit reached. Please try again tomorrow! 🌿"
                msg.contains("quota", ignoreCase = true) || msg.contains("429") || msg.contains("rate limit", ignoreCase = true) || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) -> {
                    val retryMatch = Regex("""retry in\s+([\d.]+)\s*s""", RegexOption.IGNORE_CASE).find(msg)
                    val seconds = retryMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toInt() }
                    val waitText = if (seconds != null && seconds > 0) {
                        if (seconds == 1) "1 second" else "$seconds seconds"
                    } else {
                        "60 seconds"
                    }
                    "Rate limit reached. Please wait $waitText before trying again. ⏳"
                }
                else -> "Unable to connect to AI Assistant. Please try again in a few moments."
            }
            Pair(errorMsg, null)
        }
    }

    private fun parseSuggestedTasksFromText(text: String): Pair<String, List<SuggestedTask>?> {
        val jsonPattern = Regex("```(?:json)?\\s*([\\[\\{][\\s\\S]*?[\\]\\}])\\s*```", RegexOption.IGNORE_CASE)
        val match = jsonPattern.find(text)

        if (match != null) {
            val jsonString = match.groupValues[1]
            val cleanText = text.replace(match.value, "").trim()
            try {
                val listType = object : TypeToken<List<SuggestedTask>>() {}.type
                val tasks: List<SuggestedTask> = gson.fromJson(jsonString, listType)
                return Pair(cleanText, tasks)
            } catch (_: Exception) {
                try {
                    val singleTask: SuggestedTask = gson.fromJson(jsonString, SuggestedTask::class.java)
                    return Pair(cleanText, listOf(singleTask))
                } catch (_: Exception) {
                    // Ignore JSON parsing failure
                }
            }
        }

        return Pair(text, null)
    }
}
