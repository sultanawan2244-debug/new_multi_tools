package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ali_tools_prefs", Context.MODE_PRIVATE)

    fun getGeminiApiKey(): String = prefs.getString("ali_tools_gemini_api_key", "") ?: ""
    fun setGeminiApiKey(key: String) = prefs.edit().putString("ali_tools_gemini_api_key", key).apply()

    fun getOpenRouterApiKey(): String = prefs.getString("ali_tools_openrouter_api_key", "") ?: ""
    fun setOpenRouterApiKey(key: String) = prefs.edit().putString("ali_tools_openrouter_api_key", key).apply()

    fun getHfApiKey(): String = prefs.getString("ali_tools_hf_api_key", "") ?: ""
    fun setHfApiKey(key: String) = prefs.edit().putString("ali_tools_hf_api_key", key).apply()

    fun getAiProvider(): String = prefs.getString("ali_tools_ai_provider", "gemini") ?: "gemini"
    fun setAiProvider(provider: String) = prefs.edit().putString("ali_tools_ai_provider", provider).apply()

    fun getOpenRouterModel(): String = prefs.getString("ali_tools_openrouter_model", "openai/gpt-4o") ?: "openai/gpt-4o"
    fun setOpenRouterModel(model: String) = prefs.edit().putString("ali_tools_openrouter_model", model).apply()

    fun getTheme(): String = prefs.getString("ali_tools_theme", "system") ?: "system"
    fun setTheme(theme: String) = prefs.edit().putString("ali_tools_theme", theme).apply()

    fun getFavorites(): Set<String> {
        return prefs.getStringSet("ali_tools_favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(toolId: String): Boolean {
        val current = getFavorites().toMutableSet()
        val isFav = if (current.contains(toolId)) {
            current.remove(toolId)
            false
        } else {
            current.add(toolId)
            true
        }
        prefs.edit().putStringSet("ali_tools_favorites", current).apply()
        return isFav
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
