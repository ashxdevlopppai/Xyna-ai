package com.javris.assistant.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class VisionService(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun analyzeImage(uri: Uri): ImageAnalysisResult {
        val image = InputImage.fromFilePath(context, uri)
        
        // Perform all analyses concurrently
        val textResult = try {
            textRecognizer.process(image).await()
        } catch (e: Exception) {
            null
        }

        val labelsResult = try {
            imageLabeler.process(image).await()
        } catch (e: Exception) {
            null
        }

        val objectsResult = try {
            objectDetector.process(image).await()
        } catch (e: Exception) {
            null
        }

        return ImageAnalysisResult(
            text = textResult?.text,
            labels = labelsResult?.map { label ->
                ImageLabel(
                    text = label.text,
                    confidence = label.confidence
                )
            } ?: emptyList(),
            objects = objectsResult?.map { obj ->
                DetectedObject(
                    boundingBox = obj.boundingBox,
                    labels = obj.labels.map { label ->
                        ImageLabel(
                            text = label.text,
                            confidence = label.confidence
                        )
                    }
                )
            } ?: emptyList()
        )
    }

    suspend fun processImageForAI(bitmap: Bitmap): String {
        // Create a temporary file
        val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun cleanup() {
        // Clean up temporary files
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_image_")) {
                file.delete()
            }
        }
    }
}

data class ImageAnalysisResult(
    val text: String?,
    val labels: List<ImageLabel>,
    val objects: List<DetectedObject>
)

data class ImageLabel(
    val text: String,
    val confidence: Float
)

data class DetectedObject(
    val boundingBox: android.graphics.Rect,
    val labels: List<ImageLabel>
) 