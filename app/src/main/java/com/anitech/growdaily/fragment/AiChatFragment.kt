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

        // Quick Suggestion Chips
        chipMorningRoutine.setOnClickListener {
            sendQuickPrompt("Suggest 3 positive morning habits for productivity and health.")
        }
        chipBreakDownGoal.setOnClickListener {
            sendQuickPrompt("Help me break down a goal into simple daily actionable steps.")
        }
        chipEveningUnwind.setOnClickListener {
            sendQuickPrompt("Recommend an evening routine to help me unwind and get good sleep.")
        }
        chipFocusTips.setOnClickListener {
            sendQuickPrompt("Give me quick actionable tips to boost my focus during daily work.")
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
