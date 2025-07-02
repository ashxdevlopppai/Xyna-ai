package com.javris.assistant.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceService(private val context: Context) : TextToSpeech.OnInitListener {
    
    private var textToSpeech: TextToSpeech = TextToSpeech(context, this)
    private var currentLanguage = "en-US"
    private var currentVoice: Voice? = null
    private var speechRate = 1.0f
    private var pitch = 1.0f
    private var isSpeaking = MutableStateFlow(false)
    
    enum class Language(val code: String, val displayName: String) {
        ENGLISH_US("en-US", "English (US)"),
        ENGLISH_UK("en-GB", "English (UK)"),
        ENGLISH_IN("en-IN", "English (India)"),
        HINDI("hi-IN", "Hindi"),
        HINGLISH("en-IN", "Hinglish")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set initial language
            setLanguage(Language.ENGLISH_US)
            
            // Set utterance progress listener
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking.value = false
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking.value = false
                }
            })
        }
    }

    fun setLanguage(language: Language) {
        currentLanguage = language.code
        val locale = when (language) {
            Language.ENGLISH_US -> Locale.US
            Language.ENGLISH_UK -> Locale.UK
            Language.ENGLISH_IN -> Locale("en", "IN")
            Language.HINDI -> Locale("hi", "IN")
            Language.HINGLISH -> Locale("en", "IN")
        }
        
        textToSpeech.language = locale
        
        // Select appropriate voice for the language
        val voices = textToSpeech.voices
        val preferredVoice = voices?.firstOrNull { voice ->
            voice.locale == locale && !voice.name.contains("network")
        }
        preferredVoice?.let { setVoice(it) }
    }

    fun setVoice(voice: Voice) {
        currentVoice = voice
        textToSpeech.voice = voice
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        textToSpeech.setSpeechRate(rate)
    }

    fun setPitch(newPitch: Float) {
        pitch = newPitch
        textToSpeech.setPitch(newPitch)
    }

    fun getAvailableVoices(): List<Voice> {
        return textToSpeech.voices?.toList() ?: emptyList()
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        // Add natural pauses for better flow
        val processedText = addNaturalPauses(text)
        
        textToSpeech.speak(
            processedText,
            queueMode,
            null,
            UUID.randomUUID().toString()
        )
    }

    private fun addNaturalPauses(text: String): String {
        return text.replace(". ", "... ")
            .replace(", ", "... ")
            .replace("! ", "... ")
            .replace("? ", "... ")
    }

    fun isSpeaking(): StateFlow<Boolean> = isSpeaking

    fun stop() {
        textToSpeech.stop()
        isSpeaking.value = false
    }

    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
} 