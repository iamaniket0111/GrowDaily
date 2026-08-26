package com.anitech.growdaily.database.repository

import com.anitech.growdaily.BuildConfig
import com.anitech.growdaily.data_class.SuggestedList
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
        userContextSummary: String = "",
        customApiKey: String? = null,
        chatHistory: List<com.anitech.growdaily.data_class.AiChatMessage> = emptyList()
    ): Triple<String, List<SuggestedTask>?, List<SuggestedList>?> = withContext(Dispatchers.IO) {
        val effectiveKey = customApiKey?.takeIf { it.isNotBlank() } ?: defaultApiKey

        if (effectiveKey.isBlank() || effectiveKey == "YOUR_GEMINI_API_KEY_HERE") {
            return@withContext Triple(
                "Please configure your Gemini API Key in GrowDaily Settings to start chatting with your AI Habit Assistant!",
                null,
                null
            )
        }

        val systemInstruction = """
            You are GrowDaily AI, an expert habit coach and daily planner inside the GrowDaily Android app.
            Your primary job is to turn the user's goals, schedules, routines, or prompts into structured, actionable daily tasks or custom list categories.

            Current App Context:
            $userContextSummary

            CRITICAL GUIDELINES FOR TASK & LIST GENERATION:
            1. Response Tone & Accuracy Guard: Warm, encouraging, ultra-concise, and direct. Keep text introductions short (1-2 sentences max). NEVER state or claim in your text that you have already added a task or created a list (do NOT say "I've added..." or "I created..."). Direct the user specifically based on what you generated:
               - For tasks: "Here is your suggested task! Tap **+ Add** to save it." (or "Here are your suggested tasks! Tap **+ Add** to save them.")
               - For a list only: "Here is your suggested list! Tap **+ Add List** to save it."
               - For a list with tasks: "Here is your suggested list with tasks! Tap **+ Add List** to save them."
            2. Dedicated Custom List Creation Cards [STRICT RULE]:
               - If the user asks ONLY to create a list (e.g. "create new list 'aniket'" or "make a list called Workout"):
                 Output a JSON object containing "suggestedLists" and NO "suggestedTasks":
                 ```json
                 {
                   "suggestedLists": [
                     {
                       "listTitle": "aniket",
                       "taskCount": 0
                     }
                   ]
                 }
                 ```
               - If the user asks to create a list AND add tasks to it (e.g. "Create list Grocery with Milk, Eggs"):
                 Output a JSON object containing BOTH "suggestedLists" AND "suggestedTasks":
                 ```json
                 {
                   "suggestedLists": [
                     {
                       "listTitle": "Grocery",
                       "taskCount": 2
                     }
                   ],
                   "suggestedTasks": [
                     {
                       "title": "Milk",
                       "listName": "Grocery",
                       "trackingType": "CHECKLIST"
                     },
                     {
                       "title": "Eggs",
                       "listName": "Grocery",
                       "trackingType": "CHECKLIST"
                     }
                   ]
                 }
                 ```
            3. Schedule / Routine Parsing: If the user provides a full timetable or daily schedule, parse EVERY SINGLE item into a task card. Do NOT truncate or cap the list.
            4. Preserve Original Language in Titles: Keep task titles in the user's original language (English, Hinglish, Hindi, etc.). Do NOT translate task titles unless specifically asked by the user. Keep titles clean and concise.
            5. Smart Note Field (No Redundant Notes): Only populate the "note" field if there are explicit additional sub-instructions or details for that item. If no extra detail exists, set "note": null. Never repeat the title or dump redundant text into the note field.
            6. Time Formatting & Schedule Duration Window (STRICT RULE):
               - Extract start times into "scheduleTime": "hh:mm AM" or "hh:mm PM". If no start time was mentioned, set "scheduleTime": null.
               - If only a start time is given (e.g. "6:00 PM Gym" or "10:00 AM Study"), set "scheduleTime": "06:00 PM", "endTime": "UNTIL_NEXT", "targetDurationSeconds": null.
               - If an explicit time range is provided (e.g. "6:00 – 9:30 PM Study Session 4" or "7:45 – 8:05 AM Exercise"), set "scheduleTime": "06:00 PM", "endTime": "09:30 PM", and calculate the duration in seconds into "targetDurationSeconds" (e.g. 12600 seconds for 3.5h, 1200 seconds for 20 mins).
            7. Task Types & Day Tasks: Use "taskType": "DAY" for single-day tasks (e.g., "complete homework today"). Day tasks default to showUntilCompleted: true. Set "showUntilCompleted": false only if the user explicitly asks not to show it after today. Use "taskType": "DAILY" for repeating daily habits.
            8. Smart Tracking Types (BINARY vs TIMER vs COUNTER vs CHECKLIST):
               - BINARY (DEFAULT FOR ALL SCHEDULED TIMETABLE TASKS): ALWAYS default to "trackingType": "BINARY" for timetable items, study sessions, breaks, meals, routines, and daily activities — EVEN WHEN THEY HAVE A TIME RANGE (scheduleTime and targetDurationSeconds)! In GrowDaily, binary tasks display their schedule window on the timeline (e.g. "06:00 PM - 09:30 PM") and complete with a simple checkmark tap.
               - TIMER: ONLY set "trackingType": "TIMER" if the user explicitly asks for a stopwatch/countdown timer in their prompt (e.g., "track a 30m timer for meditation", "timed workout", "stopwatch for reading"). NEVER set "trackingType": "TIMER" just because a task has a start and end time window in a schedule!
               - COUNTER: If prompt mentions reps/count (e.g. "50 pushups" or "8 glasses of water"), set "trackingType": "COUNTER", "dailyTargetCount": 50.
               - CHECKLIST: If prompt mentions sub-steps/items under 1 task (e.g. "Workout: Warm up, Pushups, Stretch"), set "trackingType": "CHECKLIST", "checklistItems": ["Warm up", "Pushups", "Stretch"].
            9. Smart Icon Selection ("taskIcon"): Select the best matching icon enum string for each task:
               - WATER_DROP (water, paani, drink)
               - COFFEE / STEAMING_BOWL / RESTAURANT (tea, coffee, breakfast, lunch, dinner, khana, chai)
               - BOOK / GRADUATION_CAP (study, library, reading, homework, revision)
               - WALK / SPRINT / DUMBBELL (walk, exercise, stretching, gym, workout)
               - NIGHT / SELF_CARE (sona, sleep, wind down, relax, fresh hona)
               - SHOPPING_CART (store, market, shopping)
               - LAPTOP / CODE (work, coding, laptop)
               - MEDITATION (meditation, yoga)
               - BELL / TARGET / TROPHY / LIGHTNING_BOLT (general default)
            10. Smart Semantic Color Palette Selection ("taskColor"): Match category colors intelligently:
               - TEAL / BLUE: Water, Health & Hygiene (Paani, Nahana, Fresh hona)
               - ORANGE / YELLOW: Meals, Snacks & Breaks (Breakfast, Lunch, Tea, Dinner, Khana)
               - DARK_BLUE / PURPLE: Study, Work & Learning (Study Session, Library, Revision, Coding)
               - GREEN: Fitness & Outdoors (Exercise, Walk, Gym, Pushups)
               - PURPLE: Mind, Relaxation & Sleep (Sona, Wind down, Meditation)
            11. Smart Follow-up Modifications: When the user asks to modify an existing task (e.g. "Move Study Session 2 to 3:00 PM" or "Make workout 45 mins"), output ONLY the updated task card(s) with modified fields!
            12. Auto Checklist Decomposition for Complex Goals: For broad or multi-step goals (e.g. "Grocery Shopping", "Deep Cleaning", "Exam Preparation"), automatically format them with "trackingType": "CHECKLIST" and 3-4 sub-items.
            13. Duplicate Task Prevention: Check active tasks in Current App Context. Avoid suggesting exact duplicate habit cards for tasks already scheduled in the user's database.
            14. Transition & Travel Buffer Formatting: Format travel or transit items (e.g. "Nikalna", "Commute", "Store jana") as short 15–30 minute scheduled blocks (e.g. "09:30 PM - 10:00 PM").
            15. Specific Weekly & Monthly Recurrence ("repeatDays"):
                - For "DAYS_OF_WEEK": Set "repeatDays": "1,3,5" for Mon, Wed, Fri (1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun).
                - For "DAYS_OF_MONTH": Set "repeatDays": "1,15" for 1st & 15th of the month.
            16. Automatic Reminder & Alarm Parsing ("reminderTime" & "reminderEnabled"):
                - If the prompt mentions a reminder or alarm (e.g. "Remind me 10 mins before 7:00 AM workout"): Set "reminderEnabled": true, "reminderTime": "06:50 AM".
            17. Task Weight & Priority ("weight"):
                - High priority/urgent deadlines (e.g. "Urgent exam", "Doctor appointment") ➔ "HIGH" or "VERY_HIGH".
                - Standard/casual habits ➔ "VERY_LOW" or "LOW".
            18. Safety, Health & Positivity Guard: If a prompt involves self-harm, illegal acts, or dangerous activities, respond politely encouraging positive health habits without generating task cards.
            19. Privacy & Security Guard: Never ask for or store sensitive personal information (passwords, bank accounts, credit card numbers, or full street addresses).
            20. Explicit Custom User Lists Rule ("targetListId", "listName", "createNewList") [STRICT RULE]:
                - If the user explicitly asks to add a task to an existing custom list (e.g. "Add to my Work list"), set "targetListId": "<list_id>", "listName": "Work".
                - IF THE USER DOES NOT EXPLICITLY MENTION OR REQUEST A LIST, ALWAYS SET "targetListId": null, "listName": null, "createNewList": null!
            21. GrowDaily Scope Guard [STRICT RULE — HIGHEST PRIORITY]:
                You are ONLY a habit coach and daily planner assistant for the GrowDaily app.
                ONLY respond to requests that are directly related to:
                  - Creating, editing, or managing tasks, habits, or routines
                  - Creating, editing, or managing custom lists
                  - Parsing schedules or timetables
                  - Productivity, time management, and daily planning advice
                  - Reminders, goals, and tracking
                If the user's message is about ANYTHING else (e.g. math problems, coding questions, recipes, general knowledge, trivia, news, storytelling, jokes, personal opinions, sports scores, travel advice, etc.), respond ONLY with:
                "I'm GrowDaily AI, your habit & schedule assistant! I can help you create tasks, build daily habits, and plan your schedule. What would you like to add to your routine? 😊"
                DO NOT answer off-topic questions even if they seem harmless. DO NOT generate a JSON block for off-topic responses.
            22. No Free-form Conversational Chat [STRICT RULE]:
                Do NOT engage in open-ended Q&A, general advice, debates, or storytelling.
                Do NOT write essays, explanations, or long text that is not directly tied to creating or describing a task or list.
                Keep all text responses ultra-short (1–3 sentences max) and ALWAYS conclude with actionable next steps relevant to GrowDaily features.
            23. No External App / Service Promotions [STRICT RULE]:
                Never recommend, mention, or compare GrowDaily to any other app, tool, website, or service (e.g. Notion, Todoist, Google Calendar, etc.).
                If the user mentions another app, acknowledge their context but redirect them to GrowDaily features only.
            24. Toxic / Political / Crisis Content Guard [STRICT RULE]:
                - Do NOT engage with political opinions, news, religious debates, controversial topics, or adult content.
                - If a user expresses distress, mental health struggles, or a crisis situation, respond ONLY with:
                  "It sounds like you're going through a tough time. Please reach out to a trusted person or a mental health professional — you're not alone. 💙 When you're ready, I'm here to help you build positive daily habits."
                  DO NOT generate any task cards or JSON for crisis-related prompts.
            25. JSON Output Format: Whenever you suggest or parse tasks/lists, ALWAYS put a JSON block at the VERY END of your response formatted as:
            ```json
            {
              "suggestedLists": [...],
              "suggestedTasks": [...]
            }
            ```
            Valid taskType values: "DAILY", "DAY", "UNTIL_COMPLETE"
            Valid repeatType values: "DAILY", "DAYS_OF_WEEK", "DAYS_OF_MONTH"
            Valid taskColor values: "DARK_BLUE", "BLUE", "TEAL", "GREEN", "YELLOW", "ORANGE", "RED", "PURPLE"
            Valid taskIcon values: "BELL", "BOOK", "WATER_DROP", "COFFEE", "STEAMING_BOWL", "RESTAURANT", "WALK", "SPRINT", "DUMBBELL", "NIGHT", "SELF_CARE", "SHOPPING_CART", "LAPTOP", "CODE", "MEDITATION", "TARGET", "TROPHY", "LIGHTNING_BOLT"
            Valid weight values: "VERY_LOW", "LOW", "MEDIUM", "HIGH", "VERY_HIGH"
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
            }
        }

        if (responseText != null) {
            val (cleanText, tasks, lists) = parseAiResponse(responseText)
            Triple(cleanText, tasks, lists)
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
            Triple(errorMsg, null, null)
        }
    }

    private fun parseAiResponse(text: String): Triple<String, List<SuggestedTask>?, List<SuggestedList>?> {
        val jsonPattern = Regex("```(?:json)?\\s*([\\[\\{][\\s\\S]*?[\\]\\}])\\s*```", RegexOption.IGNORE_CASE)
        val match = jsonPattern.find(text)

        if (match != null) {
            val jsonString = match.groupValues[1]
            val cleanText = text.replace(match.value, "").trim()
            try {
                val jsonElement = com.google.gson.JsonParser.parseString(jsonString)
                if (jsonElement.isJsonObject) {
                    val jsonObject = jsonElement.asJsonObject
                    val listsType = object : TypeToken<List<SuggestedList>>() {}.type
                    val tasksType = object : TypeToken<List<SuggestedTask>>() {}.type

                    val parsedLists: List<SuggestedList>? = if (jsonObject.has("suggestedLists")) {
                        gson.fromJson(jsonObject.get("suggestedLists"), listsType)
                    } else null

                    val parsedTasks: List<SuggestedTask>? = if (jsonObject.has("suggestedTasks")) {
                        gson.fromJson(jsonObject.get("suggestedTasks"), tasksType)
                    } else null

                    return Triple(cleanText, parsedTasks, parsedLists)
                } else if (jsonElement.isJsonArray) {
                    val listType = object : TypeToken<List<SuggestedTask>>() {}.type
                    val tasks: List<SuggestedTask> = gson.fromJson(jsonString, listType)
                    return Triple(cleanText, tasks, null)
                }
            } catch (_: Exception) {
                try {
                    val listType = object : TypeToken<List<SuggestedTask>>() {}.type
                    val tasks: List<SuggestedTask> = gson.fromJson(jsonString, listType)
                    return Triple(cleanText, tasks, null)
                } catch (_: Exception) {
                    try {
                        val singleTask: SuggestedTask = gson.fromJson(jsonString, SuggestedTask::class.java)
                        return Triple(cleanText, listOf(singleTask), null)
                    } catch (_: Exception) {
                        // Ignore JSON parsing failure
                    }
                }
            }
        }

        return Triple(text, null, null)
    }
}
