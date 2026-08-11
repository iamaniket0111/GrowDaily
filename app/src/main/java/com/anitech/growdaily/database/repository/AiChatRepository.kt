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
        userContextSummary: String,
        customApiKey: String? = null
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
            1. Response Tone: Warm, encouraging, concise, and clearly structured with bullet points.
            2. Schedule / Routine Parsing: If the user provides a full timetable or daily schedule, parse EVERY SINGLE item into a task card. Do NOT truncate or cap the list.
            3. Preserve Original Language in Titles: Keep task titles in the user's original language (English, Hinglish, Hindi, etc.). Do NOT translate task titles unless specifically asked by the user. Keep titles clean and concise.
            4. Smart Note Field (No Redundant Notes): Only populate the "note" field if there are explicit additional sub-instructions or details for that item. If no extra detail exists, set "note": null. Never repeat the title or dump redundant text into the note field.
            5. Time Formatting: Extract start times into standard "hh:mm AM" or "hh:mm PM" format (e.g., "05:00 AM", "06:40 AM", "10:20 PM").
            6. JSON Output Format: Whenever you suggest or parse tasks, ALWAYS put a JSON block at the VERY END of your response formatted exactly as:
            ```json
            [
              {
                "title": "Uthna, paani peena, fresh hona",
                "note": null,
                "taskType": "DAILY",
                "repeatType": "DAILY",
                "taskColor": "DARK_BLUE",
                "scheduleTime": "05:00 AM"
              },
              {
                "title": "Study Session 1",
                "note": "Hard Subject",
                "taskType": "DAILY",
                "repeatType": "DAILY",
                "taskColor": "DARK_BLUE",
                "scheduleTime": "06:40 AM"
              }
            ]
            ```
            Valid taskType values: "DAILY", "DAY", "UNTIL_COMPLETE"
            Valid repeatType values: "DAILY", "DAYS_OF_WEEK", "DAYS_OF_MONTH"
            Valid taskColor values: "DARK_BLUE", "BLUE", "TEAL", "GREEN", "YELLOW", "ORANGE", "RED", "PURPLE"
        """.trimIndent()

        val modelNames = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-3.6-flash")
        var responseText: String? = null
        var lastException: Throwable? = null

        for (modelName in modelNames) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = effectiveKey
                )
                val fullPrompt = "$systemInstruction\n\nUser Question: $userPrompt"
                val response = generativeModel.generateContent(fullPrompt)
                val text = response.text
                if (!text.isNullOrBlank()) {
                    responseText = text
                    break
                }
            } catch (t: Throwable) {
                lastException = t
            }
        }

        if (responseText != null) {
            val (cleanText, tasks) = parseSuggestedTasksFromText(responseText)
            Pair(cleanText, tasks)
        } else {
            val msg = lastException?.localizedMessage.orEmpty()
            val errorMsg = when {
                lastException is java.net.UnknownHostException || lastException is java.io.IOException ->
                    "No internet connection. Please check your network connection and try again."
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
