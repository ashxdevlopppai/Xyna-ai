package com.javris.assistant.util

import com.javris.assistant.BuildConfig

object ApiConfig {
    val OPENROUTER_API_KEY: String = BuildConfig.OPENROUTER_API_KEY
    val OPENROUTER_MODEL: String = BuildConfig.OPENROUTER_MODEL
    
    fun isApiKeyConfigured(): Boolean {
        return OPENROUTER_API_KEY.isNotEmpty()
    }
    
    fun getApiHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer $OPENROUTER_API_KEY",
            "Content-Type" to "application/json",
            "HTTP-Referer" to "https://github.com/yourusername/javris"
        )
    }
    
    fun getModelConfig(): Map<String, Any> {
        return mapOf(
            "model" to OPENROUTER_MODEL,
            "temperature" to 0.7,
            "max_tokens" to 1000
        )
    }
} 