package com.anitech.growdaily.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anitech.growdaily.MyApp
import com.anitech.growdaily.R
import com.anitech.growdaily.adjustAlpha
import com.anitech.growdaily.databinding.FragmentSettingsBinding
import com.anitech.growdaily.databinding.ItemAccentColorBinding
import com.anitech.growdaily.enum_class.TaskColor
import com.anitech.growdaily.setSolidBackgroundColorCompat
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
        setupAccentColorPicker()
        observePreferences()
        setClickListeners()
    }

    private fun observePreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            themePreferencesManager.themePreferenceFlow.collectLatest { preference ->
                updateThemeSelection(preference)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            themePreferencesManager.accentColorFlow.collectLatest { colorName ->
                updateAccentColorSelection(colorName)
                updateToggleGroupColors(colorName)
            }
        }
    }

    private fun updateToggleGroupColors(colorName: String) = with(binding) {
        val taskColor = TaskColor.fromName(colorName) ?: TaskColor.DARK_BLUE
        val color = ContextCompat.getColor(requireContext(), taskColor.resId)
        
        val textColorStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(color, ContextCompat.getColor(requireContext(), R.color.task_text_secondary))
        )
        
        val strokeColorStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(color, ContextCompat.getColor(requireContext(), R.color.task_card_stroke))
        )

        val backgroundTintStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(color.adjustAlpha(0.12f), android.graphics.Color.TRANSPARENT)
        )

        val rippleColorStateList = android.content.res.ColorStateList.valueOf(color.adjustAlpha(0.1f))

        listOf(btnThemeSystem, btnThemeLight, btnThemeDark).forEach { button ->
            button.setTextColor(textColorStateList)
            button.strokeColor = strokeColorStateList
            button.backgroundTintList = backgroundTintStateList
            button.rippleColor = rippleColorStateList
        }
    }

    private fun setupAccentColorPicker() {
        binding.accentColorContainer.removeAllViews()
        TaskColor.entries.forEach { taskColor ->
            val itemBinding = ItemAccentColorBinding.inflate(
                layoutInflater,
                binding.accentColorContainer,
                false
            )
            itemBinding.colorCircle.setSolidBackgroundColorCompat(
                ContextCompat.getColor(requireContext(), taskColor.resId)
            )
            itemBinding.root.setOnClickListener {
                saveAccentColor(taskColor.name)
            }
            // Store the color name in the tag for easier lookup during update
            itemBinding.root.tag = taskColor.name
            binding.accentColorContainer.addView(itemBinding.root)
        }
    }

    private fun setClickListeners() = with(binding) {
        themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val preference = when (checkedId) {
                    R.id.btnThemeSystem -> ThemePreference.SYSTEM
                    R.id.btnThemeLight -> ThemePreference.LIGHT
                    R.id.btnThemeDark -> ThemePreference.DARK
                    else -> ThemePreference.SYSTEM
                }
                saveThemePreference(preference)
            }
        }

        notificationsRow.setOnClickListener { openNotificationSettings() }
    }

    private fun saveThemePreference(preference: ThemePreference) {
        viewLifecycleOwner.lifecycleScope.launch {
            themePreferencesManager.setThemePreference(preference)
            AppCompatDelegate.setDefaultNightMode(
                themePreferencesManager.mapToNightMode(preference)
            )
        }
    }

    private fun saveAccentColor(colorName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            themePreferencesManager.setAccentColor(colorName)
            // Optional: Recreate activity if accent color change requires it
            // requireActivity().recreate()
        }
    }

    private fun updateThemeSelection(preference: ThemePreference) = with(binding) {
        val checkedId = when (preference) {
            ThemePreference.SYSTEM -> R.id.btnThemeSystem
            ThemePreference.LIGHT -> R.id.btnThemeLight
            ThemePreference.DARK -> R.id.btnThemeDark
        }
        if (themeToggleGroup.checkedButtonId != checkedId) {
            themeToggleGroup.check(checkedId)
        }
    }

    private fun updateAccentColorSelection(selectedColorName: String) {
        val selectedColor = ContextCompat.getColor(
            requireContext(),
            TaskColor.fromName(selectedColorName)?.resId ?: TaskColor.DARK_BLUE.resId
        )
        val strokeWidth = (resources.displayMetrics.density * 2).toInt()

        for (i in 0 until binding.accentColorContainer.childCount) {
            val child = binding.accentColorContainer.getChildAt(i)
            val itemBinding = ItemAccentColorBinding.bind(child)
            val isSelected = child.tag == selectedColorName
            
            itemBinding.selectionStroke.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemBinding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            if (isSelected) {
                val drawable = itemBinding.selectionStroke.background.mutate()
                if (drawable is android.graphics.drawable.GradientDrawable) {
                    drawable.setStroke(strokeWidth, selectedColor)
                }
            }
        }
    }

    private fun openNotificationSettings() {
        val context = requireContext()
        val packageName = context.packageName
        val packageManager = context.packageManager

        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra("app_package", packageName)
            putExtra("app_uid", context.applicationInfo.uid)
        }

        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )

        val targetIntent = when {
            notificationIntent.resolveActivity(packageManager) != null -> notificationIntent
            fallbackIntent.resolveActivity(packageManager) != null -> fallbackIntent
            else -> null
        }

        if (targetIntent == null) {
            Toast.makeText(
                context,
                getString(R.string.settings_notifications_open_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        runCatching {
            startActivity(targetIntent)
        }.onFailure {
            Toast.makeText(
                context,
                getString(R.string.settings_notifications_open_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
