package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FileViewMode
import com.example.data.model.SortOrder
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyLeafDataStore by preferencesDataStore(name = "studyleaf_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val FILE_VIEW_MODE = stringPreferencesKey("file_view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val GEMINI_API_KEY = stringPreferencesKey("user_gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("user_gemini_model")
    }

    val themePreference: Flow<ThemePreference> = context.studyLeafDataStore.data.map { prefs ->
        runCatching { ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.LIGHT.name) }
            .getOrDefault(ThemePreference.LIGHT)
    }

    val fileViewMode: Flow<FileViewMode> = context.studyLeafDataStore.data.map { prefs ->
        runCatching { FileViewMode.valueOf(prefs[Keys.FILE_VIEW_MODE] ?: FileViewMode.LIST.name) }
            .getOrDefault(FileViewMode.LIST)
    }

    val sortOrder: Flow<SortOrder> = context.studyLeafDataStore.data.map { prefs ->
        runCatching { SortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: SortOrder.RECENT.name) }
            .getOrDefault(SortOrder.RECENT)
    }

    val userGeminiApiKey: Flow<String> = context.studyLeafDataStore.data.map { prefs ->
        prefs[Keys.GEMINI_API_KEY].orEmpty()
    }

    val userGeminiModel: Flow<String> = context.studyLeafDataStore.data.map { prefs ->
        prefs[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.studyLeafDataStore.edit { it[Keys.THEME] = preference.name }
    }

    suspend fun setFileViewMode(mode: FileViewMode) {
        context.studyLeafDataStore.edit { it[Keys.FILE_VIEW_MODE] = mode.name }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.studyLeafDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    suspend fun setUserGeminiApiKey(apiKey: String) {
        val cleaned = apiKey.trim()
        context.studyLeafDataStore.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(Keys.GEMINI_API_KEY)
            } else {
                prefs[Keys.GEMINI_API_KEY] = cleaned
            }
        }
    }

    suspend fun setUserGeminiModel(model: String) {
        val cleaned = model.trim().ifBlank { DEFAULT_GEMINI_MODEL }
        context.studyLeafDataStore.edit { it[Keys.GEMINI_MODEL] = cleaned }
    }

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}
