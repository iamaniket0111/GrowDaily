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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.MainActivity
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.adapter.AiChatAdapter
import com.anitech.growdaily.database.viewmodel.AiChatViewModel
import com.anitech.growdaily.database.viewmodel.DailyTaskViewModelFactory
import com.anitech.growdaily.databinding.FragmentAiChatBinding

class AiChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AiChatViewModel
    private lateinit var chatAdapter: AiChatAdapter
    private var previousMessageCount = 0
    private var currentAccentColor: Int = 0

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
        viewModel = ViewModelProvider(requireActivity(), factory)[AiChatViewModel::class.java]

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        observeAccentColor()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSuggestedListsState()
    }

    private fun setupRecyclerView() {
        chatAdapter = AiChatAdapter(
            onAddSuggestedTaskClicked = { messageId, taskIndex, createList ->
                viewModel.addSuggestedTask(messageId, taskIndex, createList)
                Toast.makeText(requireContext(), "Task added to GrowDaily!", Toast.LENGTH_SHORT).show()
            },
            onAddAllSuggestedTasksClicked = { messageId ->
                viewModel.addAllSuggestedTasks(messageId)
                Toast.makeText(requireContext(), "All tasks added to GrowDaily!", Toast.LENGTH_SHORT).show()
            },
            onModifySuggestedTaskClicked = { suggestedTask ->
                val taskEntity = suggestedTask.toTaskEntity(com.anitech.growdaily.CommonMethods.getTodayDate())
                val action = AiChatFragmentDirections.actionAiChatFragmentToNavAddTask(task = taskEntity, isDraft = true)
                findNavController().navigate(action)
            },
            onDismissSuggestedTaskClicked = { messageId, taskIndex ->
                viewModel.removeSuggestedTask(messageId, taskIndex)
            },
            onAddSuggestedListClicked = { messageId, listIndex ->
                viewModel.addSuggestedList(messageId, listIndex)
                Toast.makeText(requireContext(), "List created in GrowDaily!", Toast.LENGTH_SHORT).show()
            },
            onModifySuggestedListClicked = { messageId, listIndex, currentListName ->
                viewModel.getOrCreateListEntityForNavigation(currentListName) { listEntity ->
                    val action = AiChatFragmentDirections.actionAiChatFragmentToAddList(ConditionEntity = listEntity)
                    findNavController().navigate(action)
                }
            },
            onDismissSuggestedListClicked = { messageId, listIndex ->
                viewModel.removeSuggestedList(messageId, listIndex)
            }
        )

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

        if (!com.anitech.growdaily.CommonMethods.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "No internet connection. Please check your network and try again.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.sendMessage(userPrompt)
        binding.etPrompt.text?.clear()
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messageList ->
            val isNewMessageAdded = messageList.size > previousMessageCount
            previousMessageCount = messageList.size

            chatAdapter.submitList(messageList) {
                if (isNewMessageAdded && messageList.isNotEmpty()) {
                    binding.rvChatMessages.smoothScrollToPosition(messageList.size - 1)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnSend.isEnabled = !isLoading
            binding.etPrompt.isEnabled = !isLoading
            binding.chipMorningRoutine.isEnabled = !isLoading
            binding.chipBreakDownGoal.isEnabled = !isLoading
            binding.chipEveningUnwind.isEnabled = !isLoading
            binding.chipFocusTips.isEnabled = !isLoading
        }
    }

    private fun observeAccentColor() {
        (requireActivity() as? MainActivity)?.accentColor?.observe(viewLifecycleOwner) { color ->
            currentAccentColor = color
            binding.btnSend.backgroundTintList = ColorStateList.valueOf(color)

            val defaultStrokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.anitech.growdaily.R.color.task_card_stroke)
            val strokeStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(-android.R.attr.state_focused)
                ),
                intArrayOf(color, defaultStrokeColor)
            )
            binding.inputLayoutPrompt.setBoxStrokeColorStateList(strokeStateList)

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
