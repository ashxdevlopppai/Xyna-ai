package utils

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ModelLoader private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val modelCache = LruCache<String, ByteBuffer>(MAX_MODELS_IN_MEMORY)
    private val loadedModels = mutableSetOf<String>()
    private var totalMemoryUsed = 0L

    companion object {
        private const val MAX_MODELS_IN_MEMORY = 3
        private const val MAX_MEMORY_USAGE = 4L * 1024 * 1024 * 1024 // 4GB

        @Volatile
        private var instance: ModelLoader? = null

        fun getInstance(context: Context): ModelLoader {
            return instance ?: synchronized(this) {
                instance ?: ModelLoader(context).also { instance = it }
            }
        }

        // Model configurations
        private val MODEL_CONFIGS = mapOf(
    "voice_tts" to ModelConfig(
        name = "fastspeech2",
        size = 250_000_000L,
        format = "onnx",
        priority = 2
    ),
    "voice_stt" to ModelConfig(
        name = "wav2vec2",
        size = 350_000_000L,
        format = "onnx",
        priority = 2
    ),
            "reasoning" to ModelConfig(
                name = "phi-2-onnx",
                size = 2_500_000_000L, // 2.5GB
                format = "onnx",
                priority = 1
            ),
            "tts" to ModelConfig(
                name = "coqui-vits",
                size = 700_000_000L, // 700MB
                format = "pytorch",
                priority = 2
            ),
            "stt" to ModelConfig(
                name = "vosk-model",
                size = 150_000_000L, // 150MB
                format = "custom",
                priority = 2
            ),
            "emotion" to ModelConfig(
                name = "distilroberta-emotion",
                size = 150_000_000L, // 150MB
                format = "onnx",
                priority = 3
            ),
            "embedding" to ModelConfig(
                name = "minilm",
                size = 90_000_000L, // 90MB
                format = "onnx",
                priority = 3
            ),
            "ocr" to ModelConfig(
                name = "tesseract",
                size = 50_000_000L, // 50MB
                format = "native",
                priority = 4
            ),
            "object_detection" to ModelConfig(
                name = "yolov8n",
                size = 100_000_000L, // 100MB
                format = "onnx",
                priority = 4
            ),
            "summary" to ModelConfig(
                name = "t5-small",
                size = 300_000_000L, // 300MB
                format = "onnx",
                priority = 3
            )
        )
    }

    data class ModelConfig(
        val name: String,
        val size: Long,
        val format: String,
        val priority: Int // Lower number = higher priority
    )

    suspend fun loadModel(modelType: String): ByteBuffer? {
        return withContext(Dispatchers.IO) {
            try {
                val config = MODEL_CONFIGS[modelType] ?: throw IllegalArgumentException("Unknown model type: $modelType")

                // Check if model is already loaded
                modelCache.get(modelType)?.let { return@withContext it }

                // Check memory constraints
                if (totalMemoryUsed + config.size > MAX_MEMORY_USAGE) {
                    freeMemoryForModel(config.size)
                }

                // Load model from assets
                val modelBuffer = loadModelFromAssets(config)
                modelCache.put(modelType, modelBuffer)
                loadedModels.add(modelType)
                totalMemoryUsed += config.size

                modelBuffer
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun unloadModel(modelType: String) {
        withContext(Dispatchers.IO) {
            MODEL_CONFIGS[modelType]?.let { config ->
                modelCache.remove(modelType)
                loadedModels.remove(modelType)
                totalMemoryUsed -= config.size
            }
        }
    }

    private fun freeMemoryForModel(requiredSize: Long) {
        var freedMemory = 0L
        val modelsToUnload = loadedModels
            .map { MODEL_CONFIGS[it]!! }
            .sortedByDescending { it.priority }

        for (model in modelsToUnload) {
            if (freedMemory >= requiredSize) break
            modelCache.remove(model.name)
            loadedModels.remove(model.name)
            freedMemory += model.size
            totalMemoryUsed -= model.size
        }
    }

    private suspend fun loadModelFromAssets(config: ModelConfig): ByteBuffer {
        return withContext(Dispatchers.IO) {
            val modelFile = File(appContext.getExternalFilesDir(null), "models/${config.name}")

            if (!modelFile.exists()) {
                // Copy from assets to external storage
                appContext.assets.open("models/${config.name}").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Load into memory
            val buffer = ByteBuffer.allocateDirect(config.size.toInt())
            modelFile.inputStream().use { input ->
                val channel = input.channel
                channel.read(buffer)
            }
            buffer.rewind()
            buffer
        }
    }

    fun getLoadedModels(): Set<String> = loadedModels.toSet()

    fun getMemoryUsage(): Long = totalMemoryUsed

    fun isModelLoaded(modelType: String): Boolean = loadedModels.contains(modelType)
}