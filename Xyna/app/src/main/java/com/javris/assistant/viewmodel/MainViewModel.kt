package com.javris.assistant.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.javris.assistant.data.ChatMessage
import com.javris.assistant.data.MessageType
import com.javris.assistant.service.AutomationService
import com.javris.assistant.service.VisionService
import com.javris.assistant.service.EnvironmentAnalysisService
import com.javris.assistant.service.VoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

class MainViewModel(
    private val visionService: VisionService,
    private val automationService: AutomationService,
    private val environmentAnalysisService: EnvironmentAnalysisService,
    private val voiceService: VoiceService
) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _assistantResponse = MutableLiveData<String>()
    val assistantResponse: LiveData<String> = _assistantResponse
    
    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing
    
    private val currentMessages = mutableListOf<ChatMessage>()
    private val py = Python.getInstance()
    private val aiModule = py.getModule("ai_assistant")
    private val commandProcessor = py.getModule("command_processor")

    init {
        _messages.value = currentMessages
        _isProcessing.value = false
    }

    fun processUserInput(input: String, image: Bitmap? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            
            try {
                // Add user message to the chat
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = input,
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.USER
                )
                currentMessages.add(userMessage)
                _messages.value = currentMessages.toList()

                // Process command if it looks like one
                val commandResult = processCommand(input)
                if (commandResult != null) {
                    // Add command result to chat
                    val resultMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = commandResult,
                        timestamp = System.currentTimeMillis(),
                        type = MessageType.ASSISTANT
                    )
                    currentMessages.add(resultMessage)
                    _messages.value = currentMessages.toList()
                    _assistantResponse.value = commandResult
                    return@launch
                }

                // Process image if provided
                val imagePath = image?.let {
                    visionService.processImageForAI(it)
                }

                // Process with AI
                val response = withContext(Dispatchers.IO) {
                    processWithAI(input, imagePath)
                }

                // Add AI response to the chat
                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = response,
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.ASSISTANT
                )
                currentMessages.add(assistantMessage)
                _messages.value = currentMessages.toList()
                _assistantResponse.value = response

            } finally {
                _isProcessing.value = false
                // Cleanup temporary image files
                visionService.cleanup()
            }
        }
    }

    private fun processCommand(input: String): String? {
        try {
            val command = commandProcessor.callAttr("process_command", input)
            val result = JSONObject(command.toString())
            
            return when (result.getString("action")) {
                // YouTube Actions
                "youtube_search" -> {
                    automationService.openYouTubeSearch(result.getString("query"))
                    "Searching YouTube for ${result.getString("query")}..."
                }
                "youtube_play" -> {
                    automationService.openYouTubeVideo(result.getString("video_id"))
                    "Playing YouTube video..."
                }
                
                // WhatsApp Actions
                "whatsapp_message" -> {
                    automationService.sendWhatsAppMessage(
                        result.getString("contact"),
                        result.getString("message")
                    )
                    "Sending WhatsApp message to ${result.getString("contact")}..."
                }
                
                // Social Media Actions
                "instagram_profile" -> {
                    automationService.openInstagramProfile(result.getString("username"))
                    "Opening Instagram profile..."
                }
                "facebook_profile" -> {
                    automationService.openFacebookProfile(result.getString("username"))
                    "Opening Facebook profile..."
                }
                "twitter_profile" -> {
                    automationService.openTwitterProfile(result.getString("username"))
                    "Opening Twitter profile..."
                }
                
                // App Navigation
                "app_navigation" -> {
                    val app = result.getString("app")
                    val action = result.getString("navigation_action")
                    val target = result.getString("target")
                    automationService.performAppAction(app, action, mapOf("target" to target))
                    "Navigating to $target in $app..."
                }

                "make_call" -> {
                    automationService.makePhoneCall(result.getString("contact"))
                    "Calling ${result.getString("contact")}..."
                }
                "send_message" -> {
                    automationService.sendSMS(
                        result.getString("contact"),
                        result.getString("message")
                    )
                    "Sending message to ${result.getString("contact")}..."
                }
                "send_email" -> {
                    automationService.sendEmail(
                        result.getString("recipient"),
                        result.getString("subject"),
                        result.getString("body")
                    )
                    "Sending email to ${result.getString("recipient")}..."
                }
                "create_reminder" -> {
                    val time = result.getString("time")
                    automationService.createNotification(
                        "Reminder",
                        result.getString("task"),
                        "reminders"
                    )
                    "Reminder set for $time"
                }
                "create_calendar_event" -> {
                    val startTime = result.getString("start_time")
                    val duration = result.getInt("duration_minutes")
                    val endTime = Date(Date.parse(startTime) + duration * 60 * 1000)
                    automationService.createCalendarEvent(
                        result.getString("event"),
                        "",
                        Date.parse(startTime),
                        endTime.time
                    )
                    "Event scheduled for $startTime"
                }
                "toggle_wifi" -> {
                    val enable = result.getBoolean("enable")
                    automationService.toggleWifi(enable)
                    if (enable) "WiFi enabled" else "WiFi disabled"
                }
                "toggle_bluetooth" -> {
                    val enable = result.getBoolean("enable")
                    automationService.toggleBluetooth(enable)
                    if (enable) "Bluetooth enabled" else "Bluetooth disabled"
                }
                "set_volume" -> {
                    automationService.setVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        result.getInt("value")
                    )
                    "Volume set to ${result.getInt("value")}%"
                }
                "set_brightness" -> {
                    automationService.setBrightness(result.getInt("value"))
                    "Brightness set to ${result.getInt("value")}%"
                }
                "launch_app" -> {
                    automationService.launchApp(result.getString("app"))
                    "Opening ${result.getString("app")}..."
                }
                "web_search" -> {
                    automationService.searchGoogle(result.getString("query"))
                    "Searching for ${result.getString("query")}..."
                }
                "navigate" -> {
                    automationService.openMap(result.getString("location"))
                    "Getting directions to ${result.getString("location")}..."
                }
                "media_play" -> {
                    automationService.playMedia()
                    "Playing media"
                }
                "media_pause" -> {
                    automationService.pauseMedia()
                    "Media paused"
                }
                "get_battery_info" -> {
                    val level = automationService.getBatteryLevel()
                    val charging = automationService.isDeviceCharging()
                    "Battery level: $level% ${if (charging) "(Charging)" else ""}"
                }
                "analyze_environment" -> {
                    val useFrontCamera = result.optBoolean("use_front_camera", false)
                    analyzeEnvironment(useFrontCamera)
                    "Analyzing environment..."
                }
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun analyzeEnvironment(useFrontCamera: Boolean) {
        viewModelScope.launch {
            try {
                environmentAnalysisService.analyzeEnvironment(useFrontCamera)
                    .collect { analysis ->
                        // Create natural language description
                        val description = buildString {
                            if (analysis.detectedObjects.isNotEmpty()) {
                                append("I can see ")
                                analysis.detectedObjects.forEachIndexed { index, obj ->
                                    when (index) {
                                        0 -> append(obj.label)
                                        analysis.detectedObjects.lastIndex -> append(" and ${obj.label}")
                                        else -> append(", ${obj.label}")
                                    }
                                }
                                append(". ")
                            }
                            
                            if (!analysis.detectedText.isNullOrBlank()) {
                                append("I can read some text that says: ${analysis.detectedText}. ")
                            }
                            
                            if (analysis.labels.isNotEmpty()) {
                                append("The environment appears to be ")
                                analysis.labels.forEachIndexed { index, label ->
                                    when (index) {
                                        0 -> append(label)
                                        analysis.labels.lastIndex -> append(" and $label")
                                        else -> append(", $label")
                                    }
                                }
                                append(".")
                            }
                        }

                        // Speak the analysis
                        voiceService.speak(description)
                    }
            } catch (e: Exception) {
                val errorMessage = "Sorry, I couldn't analyze the environment: ${e.message}"
                voiceService.speak(errorMessage)
            }
        }
    }

    fun processImage(uri: Uri, question: String = "What do you see in this image?") {
        viewModelScope.launch {
            _isProcessing.value = true
            
            try {
                // Analyze image
                val analysis = visionService.analyzeImage(uri)
                
                // Create detailed prompt
                val prompt = buildString {
                    append(question)
                    append("\n\nImage Analysis:")
                    if (!analysis.text.isNullOrBlank()) {
                        append("\nDetected Text: ${analysis.text}")
                    }
                    if (analysis.labels.isNotEmpty()) {
                        append("\nDetected Labels: ")
                        analysis.labels.forEach { label ->
                            append("${label.text} (${String.format("%.1f", label.confidence * 100)}%), ")
                        }
                    }
                    if (analysis.objects.isNotEmpty()) {
                        append("\nDetected Objects: ")
                        analysis.objects.forEach { obj ->
                            append("${obj.labels.firstOrNull()?.text ?: "Unknown"}, ")
                        }
                    }
                }

                // Process with AI
                processUserInput(prompt)
                
            } catch (e: Exception) {
                val errorMessage = "Sorry, I couldn't process that image: ${e.message}"
                val errorResponse = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = errorMessage,
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.ASSISTANT
                )
                currentMessages.add(errorResponse)
                _messages.value = currentMessages.toList()
                _assistantResponse.value = errorMessage
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun processWithAI(input: String, imagePath: String? = null): String {
        return try {
            val result = aiModule.callAttr(
                "process_input",
                input,
                JSONObject(currentMessages.takeLast(10).map { it.toMap() }).toString(),
                imagePath
            )
            result.toString()
        } catch (e: Exception) {
            "I apologize, but I encountered an error processing your request. Please try again."
        }
    }

    fun clearChat() {
        currentMessages.clear()
        _messages.value = currentMessages
    }

    override fun onCleared() {
        super.onCleared()
        visionService.cleanup()
    }
} 