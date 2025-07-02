package com.javris.assistant.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceAuthenticationService(private val context: Context) {
    
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    private var voiceprintFile: File? = null
    private var isEnrolled = false
    
    init {
        voiceprintFile = File(context.filesDir, "voiceprint.dat")
        isEnrolled = voiceprintFile?.exists() ?: false
    }
    
    suspend fun enrollVoice(durationMs: Long = 5000): Flow<EnrollmentState> = flow {
        emit(EnrollmentState.Started)
        
        try {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            // Start recording
            audioRecord.startRecording()
            emit(EnrollmentState.Recording)
            
            // Calculate number of frames needed
            val framesToRecord = (sampleRate * (durationMs / 1000.0)).toInt()
            val audioData = ShortArray(framesToRecord)
            var recordedFrames = 0
            
            // Record audio
            while (recordedFrames < framesToRecord) {
                val framesRead = audioRecord.read(
                    audioData,
                    recordedFrames,
                    framesToRecord - recordedFrames
                )
                if (framesRead > 0) {
                    recordedFrames += framesRead
                    emit(EnrollmentState.Progress((recordedFrames.toFloat() / framesToRecord) * 100))
                }
            }
            
            // Stop recording
            audioRecord.stop()
            audioRecord.release()
            
            // Process voice data and create voiceprint
            val voiceprint = processVoiceData(audioData)
            saveVoiceprint(voiceprint)
            
            isEnrolled = true
            emit(EnrollmentState.Completed)
            
        } catch (e: Exception) {
            emit(EnrollmentState.Error(e.message ?: "Unknown error"))
        }
    }
    
    suspend fun authenticateVoice(audioData: ShortArray): Boolean {
        if (!isEnrolled) return false
        
        return try {
            val voiceprint = processVoiceData(audioData)
            val savedVoiceprint = loadVoiceprint()
            compareVoiceprints(voiceprint, savedVoiceprint)
        } catch (e: Exception) {
            Log.e("VoiceAuth", "Authentication error", e)
            false
        }
    }
    
    private suspend fun processVoiceData(audioData: ShortArray): ByteArray = withContext(Dispatchers.Default) {
        // Convert audio data to features
        // This is a simplified example - in a real app, you'd want to:
        // 1. Apply pre-emphasis filter
        // 2. Extract MFCC features
        // 3. Create a voice embedding using a neural network
        
        val features = FloatArray(audioData.size / 160) // 10ms frames
        var featureIndex = 0
        
        // Simple feature extraction (energy-based)
        for (i in audioData.indices step 160) {
            var energy = 0f
            for (j in 0 until 160) {
                if (i + j < audioData.size) {
                    energy += (audioData[i + j] * audioData[i + j]).toFloat()
                }
            }
            if (featureIndex < features.size) {
                features[featureIndex++] = energy
            }
        }
        
        // Normalize features
        val max = features.maxOrNull() ?: 1f
        for (i in features.indices) {
            features[i] /= max
        }
        
        // Convert to bytes
        val buffer = ByteBuffer.allocate(features.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        features.forEach { buffer.putFloat(it) }
        buffer.array()
    }
    
    private suspend fun saveVoiceprint(voiceprint: ByteArray) = withContext(Dispatchers.IO) {
        voiceprintFile?.let { file ->
            FileOutputStream(file).use { out ->
                out.write(voiceprint)
            }
        }
    }
    
    private suspend fun loadVoiceprint(): ByteArray = withContext(Dispatchers.IO) {
        voiceprintFile?.readBytes() ?: ByteArray(0)
    }
    
    private fun compareVoiceprints(voiceprint1: ByteArray, voiceprint2: ByteArray): Boolean {
        if (voiceprint1.size != voiceprint2.size) return false
        
        // Convert bytes back to features
        val buffer1 = ByteBuffer.wrap(voiceprint1).order(ByteOrder.LITTLE_ENDIAN)
        val buffer2 = ByteBuffer.wrap(voiceprint2).order(ByteOrder.LITTLE_ENDIAN)
        
        val features1 = FloatArray(voiceprint1.size / 4)
        val features2 = FloatArray(voiceprint2.size / 4)
        
        for (i in features1.indices) {
            features1[i] = buffer1.getFloat()
            features2[i] = buffer2.getFloat()
        }
        
        // Calculate cosine similarity
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in features1.indices) {
            dotProduct += features1[i] * features2[i]
            norm1 += features1[i] * features1[i]
            norm2 += features2[i] * features2[i]
        }
        
        val similarity = dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble()))
        return similarity > 0.85 // Threshold for voice match
    }
    
    fun isVoiceEnrolled() = isEnrolled
    
    fun clearEnrollment() {
        voiceprintFile?.delete()
        isEnrolled = false
    }
    
    sealed class EnrollmentState {
        object Started : EnrollmentState()
        object Recording : EnrollmentState()
        data class Progress(val percent: Float) : EnrollmentState()
        object Completed : EnrollmentState()
        data class Error(val message: String) : EnrollmentState()
    }
} 