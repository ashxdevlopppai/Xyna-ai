package com.javris.assistant.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.chaquo.python.Python
import com.javris.assistant.util.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HybridAIService(private val context: Context) {
    
    private val py = Python.getInstance()
    private val offlineAI = py.getModule("offline_ai")
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var isOfflineMode = false
    private var lastOnlineCheck = 0L
    private val onlineCheckInterval = 30_000L // 30 seconds
    
    data class AIResponse(
        val text: String,
        val source: String,
        val confidence: Float,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    init {
        // Initialize offline models
        offlineAI.callAttr("load_models", context.cacheDir.absolutePath)
    }
    
    fun processInput(input: String, chatHistory: String = "", imagePath: String? = null): Flow<AIResponse> = flow {
        try {
            // Check connectivity and update mode
            updateConnectivityStatus()
            
            if (!isOfflineMode) {
                try {
                    // Try online processing first
                    val onlineResponse = processOnline(input, chatHistory, imagePath)
                    emit(AIResponse(
                        text = onlineResponse,
                        source = "online",
                        confidence = 0.9f
                    ))
                    
                    // Cache successful online response
                    cacheResponse(input, onlineResponse)
                    return@flow
                } catch (e: Exception) {
                    // Fall back to offline mode if online fails
                    isOfflineMode = true
                }
            }
            
            // Process offline
            val offlineResponse = processOffline(input, chatHistory, imagePath)
            emit(AIResponse(
                text = offlineResponse,
                source = "offline",
                confidence = 0.7f
            ))
            
        } catch (e: Exception) {
            emit(AIResponse(
                text = "I encountered an error processing your request: ${e.message}",
                source = "error",
                confidence = 0.0f
            ))
        }
    }
    
    private suspend fun processOnline(input: String, chatHistory: String, imagePath: String?): String {
        return withContext(Dispatchers.IO) {
            val requestBody = JSONObject().apply {
                put("model", ApiConfig.OPENROUTER_MODEL)
                put("messages", JSONObject().apply {
                    put("role", "user")
                    put("content", input)
                })
                put("temperature", 0.7)
                put("max_tokens", 1000)
            }
            
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .headers(Headers.of(ApiConfig.getApiHeaders()))
                .post(RequestBody.create(
                    MediaType.parse("application/json"),
                    requestBody.toString()
                ))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response: ${response.code()}")
                
                val responseBody = JSONObject(response.body()?.string() ?: "{}")
                responseBody.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }
    }
    
    private suspend fun processOffline(input: String, chatHistory: String, imagePath: String?): String {
        return withContext(Dispatchers.Default) {
            offlineAI.callAttr(
                "process_input",
                input,
                chatHistory,
                imagePath
            ).toString()
        }
    }
    
    private fun updateConnectivityStatus() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOnlineCheck < onlineCheckInterval) return
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        isOfflineMode = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
        lastOnlineCheck = currentTime
    }
    
    private fun cacheResponse(input: String, response: String) {
        try {
            offlineAI.callAttr(
                "cache_response",
                input,
                response,
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // Ignore caching errors
        }
    }
    
    fun updateOfflineModels() {
        try {
            offlineAI.callAttr("update_models", context.cacheDir.absolutePath)
        } catch (e: Exception) {
            // Handle update errors
        }
    }
    
    fun clearCache() {
        try {
            offlineAI.callAttr("clear_cache")
        } catch (e: Exception) {
            // Handle cache clearing errors
        }
    }
} 