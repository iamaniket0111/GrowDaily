package com.anitech.growdaily.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adapter.AiChatAdapter
import com.anitech.growdaily.database.viewmodel.AiChatViewModel
import com.anitech.growdaily.database.viewmodel.DailyTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentAiChatBinding
import java.util.Calendar

class AiChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AiChatViewModel
    private lateinit var chatAdapter: AiChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = (requireActivity().application as MyApp).repository
        val factory = DailyTaskViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[AiChatViewModel::class.java]

        setupRecyclerView()
        setupListeners()
        setupTimeAwareChips()
        observeViewModel()
        observeAccentColor()
    }

    private fun setupRecyclerView() {
        chatAdapter = AiChatAdapter { messageId, taskIndex ->
            viewModel.addSuggestedTask(messageId, taskIndex)
            Toast.makeText(requireContext(), "Task added to GrowDaily!", Toast.LENGTH_SHORT).show()
        }

        binding.rvChatMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupListeners() = with(binding) {
        btnSend.setOnClickListener {
            submitMessage()
        }

        etPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitMessage()
                true
            } else {
                false
            }
        }
    }

    private fun setupTimeAwareChips() = with(binding) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        when (currentHour) {
            in 5..11 -> {
                // Morning
                chipMorningRoutine.text = "🌿 Morning Habits"
                chipMorningRoutine.setOnClickListener {
                    sendQuickPrompt("Suggest 3 positive morning habits for productivity and health.")
                }

                chipBreakDownGoal.text = "🎯 Set Daily Priorities"
                chipBreakDownGoal.setOnClickListener {
                    sendQuickPrompt("Help me set 3 core priorities for today.")
                }

                chipEveningUnwind.text = "⚡ Morning Energy"
                chipEveningUnwind.setOnClickListener {
                    sendQuickPrompt("Suggest 3 quick habits to boost my morning energy.")
                }

                chipFocusTips.text = "💧 Daily Health Habits"
                chipFocusTips.setOnClickListener {
                    sendQuickPrompt("Suggest simple daily health habits like hydration and stretching.")
                }
            }
            in 12..16 -> {
                // Afternoon
                chipMorningRoutine.text = "☀️ Mid-day Focus Reset"
                chipMorningRoutine.setOnClickListener {
                    sendQuickPrompt("Suggest a 5-minute mid-day mental reset routine.")
                }

                chipBreakDownGoal.text = "🎯 Break Down a Goal"
                chipBreakDownGoal.setOnClickListener {
                    sendQuickPrompt("Help me break down a goal into simple daily actionable steps.")
                }

                chipEveningUnwind.text = "☕ Afternoon Energy Boost"
                chipEveningUnwind.setOnClickListener {
                    sendQuickPrompt("Give me quick tips to beat the afternoon slump.")
                }

                chipFocusTips.text = "📋 Review Progress"
                chipFocusTips.setOnClickListener {
                    sendQuickPrompt("Give me actionable advice to review and complete today's remaining tasks.")
                }
            }
            else -> {
                // Evening / Night
                chipMorningRoutine.text = "🌙 Evening Unwind"
                chipMorningRoutine.setOnClickListener {
                    sendQuickPrompt("Recommend an evening routine to help me unwind and get good sleep.")
                }

                chipBreakDownGoal.text = "✨ Review Today's Wins"
                chipBreakDownGoal.setOnClickListener {
                    sendQuickPrompt("Help me reflect on today's progress and achievements.")
                }

                chipEveningUnwind.text = "🧘 De-stress Routine"
                chipEveningUnwind.setOnClickListener {
                    sendQuickPrompt("Suggest 3 relaxing habits for late evening.")
                }

                chipFocusTips.text = "🌅 Prepare for Tomorrow"
                chipFocusTips.setOnClickListener {
                    sendQuickPrompt("Suggest 3 habits to prepare smoothly for tomorrow morning.")
                }
            }
        }
    }

    private fun sendQuickPrompt(promptText: String) {
        binding.etPrompt.setText(promptText)
        submitMessage()
    }

    private fun submitMessage() {
        val userPrompt = binding.etPrompt.text.toString().trim()
        if (userPrompt.isBlank()) return

        viewModel.sendMessage(userPrompt)
        binding.etPrompt.text?.clear()
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messageList ->
            chatAdapter.submitList(messageList) {
                if (messageList.isNotEmpty()) {
                    binding.rvChatMessages.smoothScrollToPosition(messageList.size - 1)
                }
            }
        }
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            binding.btnSend.backgroundTintList = ColorStateList.valueOf(color)
            chatAdapter.updateAccentColor(color)
        }
    }

    private fun showApiKeyDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Enter Gemini API Key"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Gemini API Setup")
            .setMessage("Enter your Google Gemini API Key to enable AI responses in GrowDaily:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    viewModel.setApiKey(key)
                    Toast.makeText(requireContext(), "API Key saved!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
