package com.example.plantcare.util

import android.content.Context

object Prefs {
    private const val FILE = "plantcare_prefs"
    private const val KEY_BACKEND = "ai_backend"
    private const val KEY_MODEL_URL = "model_url"
    private const val KEY_LM_STUDIO_BASE_URL = "lm_studio_base_url"
    private const val KEY_GROQ_API_KEY = "groq_api_key"
    private const val KEY_GROQ_MODEL = "groq_model"
    private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
    private const val KEY_PROXY_BASE_URL = "proxy_base_url"
    private const val KEY_DARK_THEME = "dark_theme"

    private const val DEFAULT_LM_STUDIO_URL = "http://192.168.1.126:1234"
    private const val DEFAULT_PROXY_BASE_URL = "https://plantcare-proxy.denis-aniskov55.workers.dev/"

    fun setOpenRouterApiKey(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_OPENROUTER_API_KEY, key.trim()).apply()
    }

    fun getOpenRouterApiKey(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_OPENROUTER_API_KEY, "YOUR_OPENROUTER_API_KEY")?.trim() ?: "YOUR_OPENROUTER_API_KEY"
    }

    fun setProxyBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROXY_BASE_URL, url.trim()).apply()
    }

    fun getProxyBaseUrl(context: Context): String {
        var url = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_PROXY_BASE_URL, DEFAULT_PROXY_BASE_URL)?.trim() ?: DEFAULT_PROXY_BASE_URL
        if (url.isBlank()) url = DEFAULT_PROXY_BASE_URL
        return if (url.endsWith("/")) url else "$url/"
    }

    fun setLmStudioBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_LM_STUDIO_BASE_URL, url.trim()).apply()
    }

    fun getLmStudioBaseUrl(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_LM_STUDIO_BASE_URL, DEFAULT_LM_STUDIO_URL)?.trim()?.ifBlank { DEFAULT_LM_STUDIO_URL }
            ?: DEFAULT_LM_STUDIO_URL
    }

    fun setBackend(context: Context, backend: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, backend).apply()
    }

    fun getBackend(context: Context, def: String = "GROQ"): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND, def) ?: def
    }

    fun setModelUrl(context: Context, url: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL_URL, url).apply()
    }

    fun getModelUrl(context: Context, def: String = ""): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_URL, def) ?: def
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun getDarkTheme(context: Context, def: Boolean = false): Boolean {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_THEME, def)
    }

    fun setGroqApiKey(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROQ_API_KEY, key.trim()).apply()
    }

    fun getGroqApiKey(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_GROQ_API_KEY, "YOUR_GROQ_API_KEY")?.trim()
            ?: "YOUR_GROQ_API_KEY"
    }

    fun setGroqModel(context: Context, model: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_GROQ_MODEL, model.trim()).apply()
    }

    fun getGroqModel(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_GROQ_MODEL, "llama-3.1-8b-instant")?.trim()
            ?: "llama-3.1-8b-instant"
    }
}
