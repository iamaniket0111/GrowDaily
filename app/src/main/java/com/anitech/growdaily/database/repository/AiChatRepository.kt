package com.anitech.growdaily.database.repository

import com.anitech.growdaily.data_class.SuggestedTask
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiChatRepository {

    private val gson = Gson()
    
    // Default API Key fallback / placeholder for local dev testing
    // Users can also supply their own key in Settings if needed
    private var apiKey: String = ""

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    suspend fun generateResponse(
        userPrompt: String,
        userContextSummary: String = "",
        customApiKey: String? = null
    ): Pair<String, List<SuggestedTask>?> = withContext(Dispatchers.IO) {
        val effectiveKey = customApiKey?.ifBlank { null } ?: apiKey

        if (effectiveKey.isBlank()) {
            return@withContext Pair(
                "Please configure your Gemini API Key in GrowDaily Settings to start chatting with your AI Habit Assistant!",
                null
            )
        }

        val systemInstruction = """
            You are GrowDaily AI, a friendly, actionable habit coach and task assistant inside the GrowDaily Android app.
            Your goal is to help the user build positive routines, manage daily tasks, and achieve personal growth.
            
            Current App Context:
            $userContextSummary
            
            Guidelines:
            1. Keep responses clear, warm, concise, and structured with bullet points.
            2. When the user asks for habit suggestions, task breakdowns, or routines, recommend 1 to 4 actionable tasks.
            3. Whenever you recommend specific tasks, ALSO include a JSON block at the very end of your response formatted exactly as:
            ```json
            [
              {
                "title": "Drink 500ml Water",
                "note": "Hydrate first thing in the morning",
                "taskType": "DAILY_TASK",
                "repeatType": "EVERY_DAY",
                "scheduleTime": "08:00 AM"
              }
            ]
            ```
            This JSON will automatically generate 1-tap "Add to Tasks" cards for the user in GrowDaily!
        """.trimIndent()

        try {
            // Attempt with gemini-2.5-flash / gemini-1.5-flash
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = effectiveKey
            )

            val fullPrompt = "$systemInstruction\n\nUser Question: $userPrompt"
            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text ?: "I couldn't process that request right now. Please try again!"

            val (cleanText, tasks) = parseSuggestedTasksFromText(responseText)
            Pair(cleanText, tasks)
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("404") == true -> "Model version updated. Please check your Gemini API connection."
                e.message?.contains("API_KEY_INVALID") == true -> "Invalid API Key. Please check your Gemini API key in Settings."
                else -> "Unable to connect to AI Assistant: ${e.localizedMessage ?: "Unknown error"}"
            }
            Pair(errorMsg, null)
        }
    }

    private fun parseSuggestedTasksFromText(text: String): Pair<String, List<SuggestedTask>?> {
        val jsonPattern = Regex("```json\\s*([\\[\\{][\\s\\S]*?[\\]\\}])\\s*```")
        val match = jsonPattern.find(text)

        if (match != null) {
            val jsonString = match.groupValues[1]
            val cleanText = text.replace(match.value, "").trim()
            try {
                val listType = object : TypeToken<List<SuggestedTask>>() {}.type
                val tasks: List<SuggestedTask> = gson.fromJson(jsonString, listType)
                return Pair(cleanText, tasks)
            } catch (e: Exception) {
                // If single object returned instead of array
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
