package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentAddDiaryBinding
import java.util.UUID

class AddDiaryFragment : Fragment() {

    private var _binding: FragmentAddDiaryBinding? = null
    private val binding get() = _binding!!

    private val args: AddDiaryFragmentArgs by navArgs()
    private val viewModel: AppViewModel by activityViewModels()

    private val todayDate = CommonMethods.getTodayDate()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()
        setupSaveClick()
    }

    private fun setupUi() {
        args.diary?.let { diary ->
            binding.editTextTitle.setText(diary.title)
            binding.editTextNote.setText(diary.content.orEmpty())
        }
    }

    private fun setupSaveClick() {
        binding.buttonSave.setOnClickListener {

            val title = binding.editTextTitle.text.toString().trim()
            val note = binding.editTextNote.text.toString().trim()

            if (title.isEmpty()) {
                showToast("Please enter a title")
                return@setOnClickListener
            }

            val diaryEntry = createDiaryEntry(title, note)

            if (args.diary == null) {
                viewModel.insert(diaryEntry)
                showToast("Diary saved")
            } else {
                viewModel.update(diaryEntry)
                showToast("Diary updated")
            }

            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun createDiaryEntry(title: String, note: String): DiaryEntry {
        val existingDiary = args.diary

        return if (existingDiary == null) {
            DiaryEntry(
                id = UUID.randomUUID().toString(),
                date = args.date ?: todayDate,
                title = title,
                content = note.ifEmpty { null }
            )
        } else {
            DiaryEntry(
                id = existingDiary.diaryId!!,
                date = existingDiary.date,
                title = title,
                content = note.ifEmpty { null }
            )


        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

