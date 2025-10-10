package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitech.growdaily.CommonMethods
import com.anitech.growdaily.adapter.DayNoteAdapter
import com.anitech.growdaily.data_class.DiaryEntry
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.FragmentAddDiaryBinding
import java.util.UUID

class AddDiaryFragment : Fragment() {
    private var _binding: FragmentAddDiaryBinding? = null
    private val binding get() = _binding!!
    private val args: AddDiaryFragmentArgs by navArgs()
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val argTask = args.diary
        val argDate = args.date

        if (argTask != null) {
            // Update case
            binding.editTextTitle.setText(argTask.title)
            binding.editTextNote.setText(argTask.content ?: "")

            viewModel.getNotesOnDate(CommonMethods.getTodayDate())
        }

        binding.buttonSave.setOnClickListener {
            val title = binding.editTextTitle.text.toString().trim()
            val note = binding.editTextNote.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (argTask == null) {
                // New diary
                val diaryEntry = DiaryEntry(
                    id = UUID.randomUUID().toString(),
                    date = argDate ?: java.time.LocalDate.now()
                        .toString(), // agar date null hai to aaj ka date
                    title = title,
                    content = note.ifEmpty { null }
                )
                viewModel.insert(diaryEntry)
                Toast.makeText(requireContext(), "Diary saved", Toast.LENGTH_SHORT).show()
            } else {
                // Update diary
                val diaryId = argTask.diaryId
                if (diaryId != null) {
                    val diaryEntry = DiaryEntry(
                        id = argTask.diaryId,
                        date = argTask.date, // update case me date vhi rakhi
                        title = title,
                        content = note.ifEmpty { null }
                    )
                    viewModel.update(diaryEntry)
                    Toast.makeText(requireContext(), "Diary updated", Toast.LENGTH_SHORT).show()
                }
            }
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}