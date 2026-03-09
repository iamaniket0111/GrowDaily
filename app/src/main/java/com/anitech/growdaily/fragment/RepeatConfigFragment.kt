package com.anitech.growdaily.fragment


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.anitech.growdaily.databinding.FragmentRepeatConfigBinding

class RepeatConfigFragment : Fragment() {

    private var _binding: FragmentRepeatConfigBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDone.setOnClickListener {
            val repeatType = when (binding.repeatTypeGroup.checkedRadioButtonId) {
                binding.radioEveryDay.id -> "EVERY_DAY"
                binding.radioWeekly.id -> "WEEKLY"
                binding.radioMonthly.id -> "MONTHLY"
                binding.radioCustom.id -> "CUSTOM"
                else -> "EVERY_DAY"
            }

            parentFragmentManager.setFragmentResult(
                "repeatResult",
                bundleOf("repeatType" to repeatType)
            )

            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
