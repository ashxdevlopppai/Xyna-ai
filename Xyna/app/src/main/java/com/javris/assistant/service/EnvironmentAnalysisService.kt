package com.javris.assistant.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class EnvironmentAnalysisService(
    private val context: Context,
    private val visionService: VisionService,
    private val voiceService: VoiceService
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraOpenCloseLock = Semaphore(1)
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isAnalyzing = false

    data class AnalysisResult(
        val objects: List<String>,
        val text: String?,
        val sceneLabels: List<String>,
        val description: String
    )

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }
    }

    fun startEnvironmentAnalysis(useFrontCamera: Boolean): Flow<AnalysisResult> = flow {
        try {
            isAnalyzing = true
            startBackgroundThread()
            
            // Select camera
            val cameraId = selectCamera(useFrontCamera)
            
            // Open camera
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            
            while (isAnalyzing) {
                val result = analyzeCurrentView()
                emit(result)
                
                // Provide voice feedback
                provideVoiceFeedback(result)
                
                // Wait before next analysis
                kotlinx.coroutines.delay(2000) // Analysis every 2 seconds
            }
        } finally {
            stopAnalysis()
        }
    }

    private fun selectCamera(useFrontCamera: Boolean): String {
        return cameraManager.cameraIdList.find { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (useFrontCamera) {
                facing == CameraCharacteristics.LENS_FACING_FRONT
            } else {
                facing == CameraCharacteristics.LENS_FACING_BACK
            }
        } ?: throw RuntimeException("Camera not found")
    }

    private fun createCaptureSession() {
        val size = android.util.Size(1280, 720)
        imageReader = ImageReader.newInstance(
            size.width, size.height,
            ImageFormat.JPEG, 2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                // Process image
                image?.close()
            }, backgroundHandler)
        }

        val surface = imageReader?.surface ?: return

        cameraDevice?.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    val captureRequest = cameraDevice?.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW
                    )?.apply {
                        addTarget(surface)
                    }?.build()

                    captureRequest?.let {
                        session.setRepeatingRequest(it, null, backgroundHandler)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Handle configuration failure
                }
            },
            backgroundHandler
        )
    }

    private suspend fun analyzeCurrentView(): AnalysisResult = withContext(Dispatchers.Default) {
        // Capture and analyze image
        val bitmap = captureImage()
        val imagePath = visionService.processImageForAI(bitmap)
        val analysisResult = visionService.analyzeImage(android.net.Uri.parse(imagePath))

        // Process results
        val objects = analysisResult.objects.flatMap { obj -> 
            obj.labels.map { it.text }
        }.distinct()

        val sceneLabels = analysisResult.labels.map { it.text }
        
        // Generate natural description
        val description = generateDescription(objects, analysisResult.text, sceneLabels)

        AnalysisResult(
            objects = objects,
            text = analysisResult.text,
            sceneLabels = sceneLabels,
            description = description
        )
    }

    private fun generateDescription(
        objects: List<String>,
        text: String?,
        sceneLabels: List<String>
    ): String {
        val description = StringBuilder()

        // Describe objects
        if (objects.isNotEmpty()) {
            description.append("I can see ${objects.joinToString(", ", transform = ::formatItem)}. ")
        }

        // Describe any text
        if (!text.isNullOrBlank()) {
            description.append("I can read text that says: $text. ")
        }

        // Describe scene
        if (sceneLabels.isNotEmpty()) {
            description.append("This appears to be ${sceneLabels.joinToString(" and ")}. ")
        }

        return description.toString()
    }

    private fun formatItem(item: String): String {
        return when {
            item.startsWith("a ") || item.startsWith("an ") -> item
            "aeiou".contains(item.firstOrNull()?.lowercaseChar() ?: ' ') -> "an $item"
            else -> "a $item"
        }
    }

    private suspend fun provideVoiceFeedback(result: AnalysisResult) {
        voiceService.speak(result.description)
    }

    private fun captureImage(): Bitmap {
        // Implementation to capture current camera view as bitmap
        // This is a placeholder - actual implementation would use the camera capture session
        return Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun stopAnalysis() {
        isAnalyzing = false
        imageReader?.close()
        cameraDevice?.close()
        stopBackgroundThread()
        visionService.cleanup()
    }
} 