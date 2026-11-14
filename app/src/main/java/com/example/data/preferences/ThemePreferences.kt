package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "faceattend_prefs")

class ThemePreferences(private val context: Context) {

    private val darkThemeOverrideKey = booleanPreferencesKey("dark_theme_override")
    private val hasThemeOverrideKey = booleanPreferencesKey("has_theme_override")

    val isDarkThemeOverride: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        val hasOverride = prefs[hasThemeOverrideKey] ?: false
        if (hasOverride) prefs[darkThemeOverrideKey] ?: false else null
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[hasThemeOverrideKey] = true
            prefs[darkThemeOverrideKey] = isDark
        }
    }
}
