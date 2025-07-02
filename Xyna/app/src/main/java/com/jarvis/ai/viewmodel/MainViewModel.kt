package com.jarvis.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.model.Message
import com.jarvis.ai.service.EnvironmentAnalysisService
import com.jarvis.ai.service.VoiceService
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val voiceService = VoiceService(application)
    private val environmentAnalysisService = EnvironmentAnalysisService(application)
    
    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages
    
    private val _isListening = MutableLiveData(false)
    val isListening: LiveData<Boolean> = _isListening
    
    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing
    
    init {
        viewModelScope.launch {
            voiceService.speechResults.collect { result ->
                result?.let { processVoiceCommand(it) }
            }
        }
    }
    
    fun toggleVoiceInput() {
        if (_isListening.value == true) {
            voiceService.stopListening()
        } else {
            voiceService.startListening()
        }
        _isListening.value = !(_isListening.value ?: false)
    }
    
    fun sendMessage(message: Message) {
        viewModelScope.launch {
            val currentMessages = _messages.value.orEmpty().toMutableList()
            currentMessages.add(message)
            _messages.value = currentMessages
            
            _isProcessing.value = true
            
            // Process the message
            when {
                isEnvironmentAnalysisCommand(message.content) -> {
                    handleEnvironmentAnalysis(message.content)
                }
                else -> {
                    // Handle other commands or generate response
                    val response = generateResponse(message.content)
                    currentMessages.add(Message(content = response, isUser = false))
                    _messages.value = currentMessages
                }
            }
            
            _isProcessing.value = false
        }
    }
    
    private fun isEnvironmentAnalysisCommand(command: String): Boolean {
        val environmentCommands = listOf(
            "see environment",
            "look around",
            "analyze surroundings",
            "what do you see"
        )
        return environmentCommands.any { command.lowercase().contains(it) }
    }
    
    private suspend fun handleEnvironmentAnalysis(command: String) {
        val useFrontCamera = command.lowercase().contains("front") || 
                            command.lowercase().contains("selfie")
        
        environmentAnalysisService.startAnalysis(useFrontCamera)
    }
    
    private suspend fun generateResponse(input: String): String {
        // TODO: Implement AI response generation
        return "I understand you said: $input"
    }
    
    fun onPermissionsGranted() {
        // Initialize services that require permissions
        voiceService.initialize()
        environmentAnalysisService.initialize()
    }
    
    fun showPermissionError() {
        val currentMessages = _messages.value.orEmpty().toMutableList()
        currentMessages.add(Message(
            content = "I need permissions to help you better. Please grant them in settings.",
            isUser = false
        ))
        _messages.value = currentMessages
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceService.cleanup()
        environmentAnalysisService.cleanup()
    }
} 