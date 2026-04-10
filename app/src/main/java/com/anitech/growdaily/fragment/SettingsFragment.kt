package com.anitech.growdaily.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.databinding.FragmentSettingsBinding
import com.anitech.growdaily.settings.ThemePreference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val themePreferencesManager by lazy {
        (requireActivity().application as MyApp).themePreferencesManager
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeThemePreference()
        setClickListeners()
    }

    private fun observeThemePreference() {
        viewLifecycleOwner.lifecycleScope.launch {
            themePreferencesManager.themePreferenceFlow.collectLatest { preference ->
                updateThemeSelection(preference)
            }
        }
    }

    private fun setClickListeners() = with(binding) {
        themeSystemRow.setOnClickListener { saveThemePreference(ThemePreference.SYSTEM) }
        themeLightRow.setOnClickListener { saveThemePreference(ThemePreference.LIGHT) }
        themeDarkRow.setOnClickListener { saveThemePreference(ThemePreference.DARK) }

        rbThemeSystem.setOnClickListener { saveThemePreference(ThemePreference.SYSTEM) }
        rbThemeLight.setOnClickListener { saveThemePreference(ThemePreference.LIGHT) }
        rbThemeDark.setOnClickListener { saveThemePreference(ThemePreference.DARK) }
    }

    private fun saveThemePreference(preference: ThemePreference) {
        viewLifecycleOwner.lifecycleScope.launch {
            themePreferencesManager.setThemePreference(preference)
            AppCompatDelegate.setDefaultNightMode(
                themePreferencesManager.mapToNightMode(preference)
            )
        }
    }

    private fun updateThemeSelection(preference: ThemePreference) = with(binding) {
        setChecked(rbThemeSystem, preference == ThemePreference.SYSTEM)
        setChecked(rbThemeLight, preference == ThemePreference.LIGHT)
        setChecked(rbThemeDark, preference == ThemePreference.DARK)
        txtThemeSummary.text = when (preference) {
            ThemePreference.SYSTEM -> "Using your device setting"
            ThemePreference.LIGHT -> "Always use light theme"
            ThemePreference.DARK -> "Always use dark theme"
        }
    }

    private fun setChecked(button: RadioButton, checked: Boolean) {
        if (button.isChecked != checked) {
            button.isChecked = checked
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
