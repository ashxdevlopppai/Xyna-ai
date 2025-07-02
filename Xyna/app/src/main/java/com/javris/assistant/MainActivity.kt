package com.javris.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.javris.assistant.databinding.ActivityMainBinding
import com.javris.assistant.databinding.DialogVoiceSettingsBinding
import com.javris.assistant.databinding.DialogVoiceEnrollmentBinding
import com.javris.assistant.viewmodel.MainViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var voiceService: VoiceService
    private lateinit var voiceAuthService: VoiceAuthenticationService
    private var isVoiceAuthenticated = false
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        voiceService = VoiceService(this)
        voiceAuthService = VoiceAuthenticationService(this)
        
        setupUI()
        checkPermissions()
        initializeSpeechComponents()
        observeViewModel()
        
        // Check voice enrollment
        if (!voiceAuthService.isVoiceEnrolled()) {
            showVoiceEnrollmentDialog()
        }
        
        // Initialize voice settings
        setupVoiceSettings()
    }

    private fun setupUI() {
        binding.micButton.setOnClickListener {
            startListening()
        }
        
        binding.sendButton.setOnClickListener {
            val userInput = binding.inputEditText.text.toString()
            if (userInput.isNotEmpty()) {
                processUserInput(userInput)
                binding.inputEditText.text?.clear()
            }
        }
        
        binding.settingsButton.setOnClickListener {
            showVoiceSettingsDialog()
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
        }
    }

    private fun initializeSpeechComponents() {
        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this, this)
        
        // Initialize Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            private var audioData = mutableListOf<Short>()
            
            override fun onReadyForSpeech(params: Bundle?) {
                binding.micButton.setImageResource(R.drawable.ic_mic_active)
                audioData.clear()
            }

            override fun onResults(results: Bundle?) {
                binding.micButton.setImageResource(R.drawable.ic_mic)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { text ->
                    processUserInput(text, audioData.toShortArray())
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                buffer?.let {
                    val shorts = ShortArray(it.size / 2)
                    ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    audioData.addAll(shorts.toList())
                }
            }

            // Implement other RecognitionListener methods
            override fun onError(error: Int) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (!voiceAuthService.isVoiceEnrolled()) {
            showVoiceEnrollmentDialog()
            return
        }
        
        val intent = RecognizerIntent.getVoiceDetailsIntent(this)
        speechRecognizer.startListening(intent)
    }

    private fun processUserInput(input: String, audioData: ShortArray? = null) {
        lifecycleScope.launch {
            // Authenticate voice if audio data is available
            if (audioData != null) {
                val isAuthenticated = voiceAuthService.authenticateVoice(audioData)
                if (!isAuthenticated) {
                    // Get random rejection message
                    val rejectionMessages = resources.getStringArray(R.array.voice_rejection_messages)
                    val randomMessage = rejectionMessages[(0 until rejectionMessages.size).random()]
                    
                    binding.chatRecyclerView.adapter?.let { adapter ->
                        val message = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = randomMessage,
                            timestamp = System.currentTimeMillis(),
                            type = MessageType.ASSISTANT
                        )
                        (adapter as ChatAdapter).addMessage(message)
                    }
                    voiceService.speak(randomMessage)
                    return@launch
                }
                isVoiceAuthenticated = true
            }
            
            // Process input if authenticated
            if (isVoiceAuthenticated) {
                viewModel.processUserInput(input)
            } else {
                binding.chatRecyclerView.adapter?.let { adapter ->
                    val message = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        content = getString(R.string.voice_auth_required),
                        timestamp = System.currentTimeMillis(),
                        type = MessageType.ASSISTANT
                    )
                    (adapter as ChatAdapter).addMessage(message)
                }
                voiceService.speak(getString(R.string.voice_auth_required))
            }
        }
    }

    private fun showVoiceSettingsDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogVoiceSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        // Setup language spinner
        val languages = VoiceService.Language.values()
        val languageAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            languages.map { getString(getLanguageStringResource(it)) }
        )
        dialogBinding.languageSpinner.setAdapter(languageAdapter)
        
        // Set current language
        val currentLanguage = voiceService.getCurrentLanguage()
        dialogBinding.languageSpinner.setText(
            getString(getLanguageStringResource(currentLanguage)),
            false
        )
        
        // Setup voice spinner
        updateVoiceSpinner(dialogBinding, currentLanguage)
        
        // Handle language selection
        dialogBinding.languageSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedLanguage = languages[position]
            updateVoiceSpinner(dialogBinding, selectedLanguage)
        }
        
        // Setup speech rate and pitch
        dialogBinding.speechRateSeekBar.progress = (voiceService.speechRate * 100).toInt()
        dialogBinding.pitchSeekBar.progress = (voiceService.pitch * 100).toInt()
        
        // Handle test button
        dialogBinding.testButton.setOnClickListener {
            val language = languages[dialogBinding.languageSpinner.listSelection]
            voiceService.setLanguage(language)
            voiceService.setSpeechRate(dialogBinding.speechRateSeekBar.progress / 100f)
            voiceService.setPitch(dialogBinding.pitchSeekBar.progress / 100f)
            
            // Test speech in the selected language
            when (language) {
                VoiceService.Language.HINDI -> voiceService.speak("नमस्ते, मैं जार्विस हूं")
                VoiceService.Language.HINGLISH -> voiceService.speak("Hello, main Jarvis hoon")
                else -> voiceService.speak("Hello, I am Jarvis")
            }
        }
        
        // Handle save button
        dialogBinding.saveButton.setOnClickListener {
            val language = languages[dialogBinding.languageSpinner.listSelection]
            voiceService.setLanguage(language)
            voiceService.setSpeechRate(dialogBinding.speechRateSeekBar.progress / 100f)
            voiceService.setPitch(dialogBinding.pitchSeekBar.progress / 100f)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun updateVoiceSpinner(dialogBinding: DialogVoiceSettingsBinding, language: VoiceService.Language) {
        val voices = voiceService.getAvailableVoices().filter { it.locale.language == language.code.split("-")[0] }
        val voiceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            voices.map { it.name }
        )
        dialogBinding.voiceSpinner.setAdapter(voiceAdapter)
        
        // Set current voice
        voiceService.currentVoice?.let { currentVoice ->
            dialogBinding.voiceSpinner.setText(currentVoice.name, false)
        }
        
        // Handle voice selection
        dialogBinding.voiceSpinner.setOnItemClickListener { _, _, position, _ ->
            voiceService.setVoice(voices[position])
        }
    }

    private fun getLanguageStringResource(language: VoiceService.Language): Int {
        return when (language) {
            VoiceService.Language.ENGLISH_US -> R.string.language_en_us
            VoiceService.Language.ENGLISH_UK -> R.string.language_en_uk
            VoiceService.Language.ENGLISH_IN -> R.string.language_en_in
            VoiceService.Language.HINDI -> R.string.language_hi
            VoiceService.Language.HINGLISH -> R.string.language_hinglish
        }
    }

    private fun showVoiceEnrollmentDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogVoiceEnrollmentBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        var isEnrolling = false
        
        dialogBinding.startButton.setOnClickListener {
            if (!isEnrolling) {
                lifecycleScope.launch {
                    isEnrolling = true
                    dialogBinding.startButton.isEnabled = false
                    dialogBinding.progressBar.visibility = View.VISIBLE
                    
                    voiceAuthService.enrollVoice().collect { state ->
                        when (state) {
                            is VoiceAuthenticationService.EnrollmentState.Started -> {
                                dialogBinding.statusText.setText(R.string.voice_enrollment_instructions)
                            }
                            is VoiceAuthenticationService.EnrollmentState.Recording -> {
                                dialogBinding.statusText.setText(R.string.enrollment_in_progress)
                            }
                            is VoiceAuthenticationService.EnrollmentState.Progress -> {
                                dialogBinding.progressBar.progress = state.percent.toInt()
                                dialogBinding.statusText.text = getString(
                                    R.string.enrollment_in_progress,
                                    state.percent.toInt()
                                )
                            }
                            is VoiceAuthenticationService.EnrollmentState.Completed -> {
                                dialogBinding.statusText.setText(R.string.enrollment_success)
                                dialog.dismiss()
                                isVoiceAuthenticated = true
                            }
                            is VoiceAuthenticationService.EnrollmentState.Error -> {
                                dialogBinding.statusText.text = getString(
                                    R.string.enrollment_failed,
                                    state.message
                                )
                                dialogBinding.startButton.isEnabled = true
                                dialogBinding.startButton.setText(R.string.retry_enrollment)
                            }
                        }
                    }
                    
                    isEnrolling = false
                }
            }
        }
        
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setCancelable(false)
        dialog.show()
    }

    private fun observeViewModel() {
        viewModel.assistantResponse.observe(this) { response ->
            binding.chatRecyclerView.adapter?.notifyItemInserted(binding.chatRecyclerView.adapter?.itemCount ?: 0)
            binding.chatRecyclerView.smoothScrollToPosition(binding.chatRecyclerView.adapter?.itemCount ?: 0)
            
            // Speak the response
            voiceService.speak(response)
        }
        
        viewModel.isProcessing.observe(this) { isProcessing ->
            binding.micButton.isEnabled = !isProcessing
            binding.sendButton.isEnabled = !isProcessing
            
            if (isProcessing) {
                voiceService.speak(getString(R.string.accessibility_assistant_thinking))
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Handle language not supported
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        voiceService.shutdown()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
} 