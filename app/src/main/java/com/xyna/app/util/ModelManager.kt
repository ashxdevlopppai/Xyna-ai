package com.xyna.app.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ModelManager {

    private const val PREFS_NAME = "XynaModelPrefs"
    private const val MODELS_READY_KEY = "models_are_ready"

    private val REQUIRED_MODELS = listOf(
        "bert-base-uncased.zip",
        "facebook_bart-large-cnn.zip",
        "gpt2.zip",
        "vosk-model-small-en-us-0.15.zip",
        "XTTS-v2.zip"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun areModelsReady(context: Context): Boolean {
        return getPrefs(context).getBoolean(MODELS_READY_KEY, false)
    }

    fun getModelPath(context: Context, modelName: String): String? {
        val modelDir = File(context.filesDir, "models")
        val specificModelDir = File(modelDir, modelName.removeSuffix(".zip"))
        return if (specificModelDir.exists() && specificModelDir.isDirectory) {
            specificModelDir.absolutePath
        } else {
            null
        }
    }

    suspend fun initialize(
        context: Context,
        onProgress: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (areModelsReady(context)) {
            onComplete()
            return@withContext
        }

        val externalModelDir = File(Environment.getExternalStorageDirectory(), "Xyna/models")
        if (!externalModelDir.exists() || !externalModelDir.isDirectory) {
            onError("Model directory not found at: ${externalModelDir.absolutePath}. Please create it and place model .zip files inside.")
            return@withContext
        }

        val internalModelDir = File(context.filesDir, "models")
        if (!internalModelDir.exists()) {
            internalModelDir.mkdirs()
        }

        try {
            for ((index, modelZipName) in REQUIRED_MODELS.withIndex()) {
                onProgress("Checking for $modelZipName (${index + 1}/${REQUIRED_MODELS.size})...")
                val modelZipFile = File(externalModelDir, modelZipName)
                val modelUnzipDir = File(internalModelDir, modelZipName.removeSuffix(".zip"))

                if (!modelZipFile.exists()) {
                    onError("Required model file not found: $modelZipName")
                    return@withContext
                }

                if (modelUnzipDir.exists()) {
                    onProgress("$modelZipName already unzipped.")
                    continue
                }

                onProgress("Unzipping $modelZipName...")
                unzip(modelZipFile, modelUnzipDir)
            }

            getPrefs(context).edit().putBoolean(MODELS_READY_KEY, true).apply()
            onProgress("All models ready.")
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
            onError("An error occurred during model initialization: ${e.message}")
        }
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDirectory, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    File(newFile.parent).mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
