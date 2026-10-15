package com.jarvis.localai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "jarvis_settings")

data class AppSettings(
    val systemPrompt: String = "You are JARVIS, a concise and helpful private assistant.",
    val theme: String = "dark"
)

class PreferencesRepository(private val context: Context) {
    private val systemPromptKey = stringPreferencesKey("system_prompt")
    private val themeKey = stringPreferencesKey("theme")
    private val activeModelKey = stringPreferencesKey("active_model_id")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { values ->
        AppSettings(
            systemPrompt = values[systemPromptKey] ?: AppSettings().systemPrompt,
            theme = values[themeKey] ?: "dark"
        )
    }

    val activeModelId: Flow<String?> = context.settingsStore.data.map { it[activeModelKey] }

    suspend fun saveSystemPrompt(value: String) {
        context.settingsStore.edit { it[systemPromptKey] = value }
    }

    suspend fun saveTheme(value: String) {
        context.settingsStore.edit { it[themeKey] = value }
    }

    suspend fun saveActiveModel(id: String?) {
        context.settingsStore.edit { values ->
            if (id == null) values.remove(activeModelKey) else values[activeModelKey] = id
        }
    }
}