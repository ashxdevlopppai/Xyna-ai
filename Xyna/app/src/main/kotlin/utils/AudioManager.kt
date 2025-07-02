package utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        @Volatile
        private var instance: AudioManager? = null

        fun getInstance(context: Context): AudioManager {
            return instance ?: synchronized(this) {
                instance ?: AudioManager(context).also { instance = it }
            }
        }
    }

    init {
        initializeAudioRecord()
        initializeAudioTrack()
    }

    private fun initializeAudioRecord() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )
    }

    private fun initializeAudioTrack() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AUDIO_FORMAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    suspend fun startRecording(): Flow<ByteArray> = flow {
        withContext(Dispatchers.IO) {
            try {
                if (isRecording) return@withContext

                audioRecord?.startRecording()
                isRecording = true

                val buffer = ByteArray(BUFFER_SIZE)
                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: 0
                    if (bytesRead > 0) {
                        emit(buffer.copyOf(bytesRead))
                    }
                }
            } catch (e: Exception) {
                stopRecording()
                throw e
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
    }

    suspend fun playAudio(audioData: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                if (isPlaying) return@withContext

                audioTrack?.play()
                isPlaying = true

                val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
                val shortBuffer = ShortArray(audioData.size / 2)
                for (i in shortBuffer.indices) {
                    shortBuffer[i] = buffer.short
                }

                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            } catch (e: Exception) {
                stopPlaying()
                throw e
            } finally {
                stopPlaying()
            }
        }
    }

    private fun stopPlaying() {
        isPlaying = false
        audioTrack?.stop()
    }

    suspend fun saveAudioToFile(audioData: ByteArray, filename: String) {
        withContext(Dispatchers.IO) {
            val file = File(appContext.getExternalFilesDir(null), "audio/$filename")
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { output ->
                output.write(audioData)
            }
        }
    }

    suspend fun loadAudioFromFile(filename: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(appContext.getExternalFilesDir(null), "audio/$filename")
                if (!file.exists()) return@withContext null

                file.readBytes()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun release() {
        stopRecording()
        stopPlaying()
        audioRecord?.release()
        audioTrack?.release()
        audioRecord = null
        audioTrack = null
        instance = null
    }

    fun isRecording(): Boolean = isRecording
    fun isPlaying(): Boolean = isPlaying
}