package services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import utils.AudioManager

class WakeWordService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var audioManager: AudioManager
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        audioManager = AudioManager(applicationContext)
        initializeWakeWordDetection()
    }

    private fun initializeWakeWordDetection() {
        serviceScope.launch {
            try {
                // Initialize Porcupine wake word detector
                // When wake word detected, start Vosk STT
                startVoskRecognition()
            } catch (e: Exception) {
                // Log error and restart service
            }
        }
    }

    private fun startVoskRecognition() {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { text ->
                    // Process command via XynaCoreBrain
                }
            }
            // Implement other RecognitionListener methods
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
    }
}