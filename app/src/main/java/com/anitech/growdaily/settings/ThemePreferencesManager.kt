package com.anitech.growdaily.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreferencesManager(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_preference")

    val themePreferenceFlow: Flow<ThemePreference> = context.themeDataStore.data.map { preferences ->
        preferences[themeKey]
            ?.let { value -> runCatching { ThemePreference.valueOf(value) }.getOrNull() }
            ?: ThemePreference.SYSTEM
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.themeDataStore.edit { preferences: MutablePreferences ->
            preferences[themeKey] = preference.name
        }
    }

    fun mapToNightMode(preference: ThemePreference): Int {
        return when (preference) {
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}
