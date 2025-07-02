package com.javris.assistant.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.hardware.camera2.CameraManager
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ARService(private val context: Context) {

    data class ARObject(
        val id: String,
        val type: ARObjectType,
        val position: PointF,
        val data: Map<String, Any>,
        val priority: Int = 0
    )

    enum class ARObjectType {
        NOTIFICATION,
        DIRECTION,
        POI,
        CONTACT,
        TASK,
        HEALTH_METRIC,
        DEVICE_STATUS
    }

    data class AROverlay(
        val objects: List<ARObject>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
    }

    private var surfaceHolder: SurfaceHolder? = null
    private var isRunning = false
    private val arObjects = mutableListOf<ARObject>()

    fun startAR(holder: SurfaceHolder) {
        surfaceHolder = holder
        isRunning = true
        startRendering()
    }

    fun stopAR() {
        isRunning = false
        surfaceHolder = null
    }

    fun addARObject(arObject: ARObject) {
        arObjects.add(arObject)
        arObjects.sortByDescending { it.priority }
    }

    fun removeARObject(id: String) {
        arObjects.removeAll { it.id == id }
    }

    fun getAROverlay(): Flow<AROverlay> = flow {
        while (isRunning) {
            emit(AROverlay(arObjects.toList()))
            kotlinx.coroutines.delay(100) // Update at 10fps
        }
    }

    private fun startRendering() {
        Thread {
            while (isRunning) {
                surfaceHolder?.let { holder ->
                    var canvas: Canvas? = null
                    try {
                        canvas = holder.lockCanvas()
                        canvas?.let { drawARObjects(it) }
                    } finally {
                        canvas?.let { holder.unlockCanvasAndPost(it) }
                    }
                }
                Thread.sleep(33) // ~30fps
            }
        }.start()
    }

    private fun drawARObjects(canvas: Canvas) {
        // Clear canvas
        canvas.drawColor(Color.TRANSPARENT)

        arObjects.forEach { arObject ->
            when (arObject.type) {
                ARObjectType.NOTIFICATION -> drawNotification(canvas, arObject)
                ARObjectType.DIRECTION -> drawDirection(canvas, arObject)
                ARObjectType.POI -> drawPOI(canvas, arObject)
                ARObjectType.CONTACT -> drawContact(canvas, arObject)
                ARObjectType.TASK -> drawTask(canvas, arObject)
                ARObjectType.HEALTH_METRIC -> drawHealthMetric(canvas, arObject)
                ARObjectType.DEVICE_STATUS -> drawDeviceStatus(canvas, arObject)
            }
        }
    }

    private fun drawNotification(canvas: Canvas, arObject: ARObject) {
        paint.color = Color.WHITE
        paint.alpha = 200
        
        val title = arObject.data["title"] as? String ?: return
        val message = arObject.data["message"] as? String ?: return
        
        canvas.drawText(title, arObject.position.x, arObject.position.y, paint)
        paint.textSize = 30f
        canvas.drawText(message, arObject.position.x, arObject.position.y + 40f, paint)
        paint.textSize = 40f
    }

    private fun drawDirection(canvas: Canvas, arObject: ARObject) {
        paint.color = ContextCompat.getColor(context, android.R.color.holo_blue_light)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        
        val destination = arObject.data["destination"] as? PointF ?: return
        val distance = arObject.data["distance"] as? Float ?: return
        
        // Draw arrow
        canvas.drawLine(
            arObject.position.x, arObject.position.y,
            destination.x, destination.y,
            paint
        )
        
        // Draw distance
        paint.style = Paint.Style.FILL
        canvas.drawText(
            "${distance.toInt()}m",
            (arObject.position.x + destination.x) / 2,
            (arObject.position.y + destination.y) / 2,
            paint
        )
    }

    private fun drawPOI(canvas: Canvas, arObject: ARObject) {
        paint.color = ContextCompat.getColor(context, android.R.color.holo_green_light)
        paint.style = Paint.Style.FILL
        
        val name = arObject.data["name"] as? String ?: return
        val type = arObject.data["type"] as? String ?: return
        
        // Draw POI marker
        canvas.drawCircle(arObject.position.x, arObject.position.y, 20f, paint)
        
        // Draw POI info
        paint.color = Color.WHITE
        canvas.drawText(name, arObject.position.x + 30f, arObject.position.y, paint)
        paint.textSize = 30f
        canvas.drawText(type, arObject.position.x + 30f, arObject.position.y + 40f, paint)
        paint.textSize = 40f
    }

    private fun drawContact(canvas: Canvas, arObject: ARObject) {
        paint.color = ContextCompat.getColor(context, android.R.color.holo_purple)
        
        val name = arObject.data["name"] as? String ?: return
        val status = arObject.data["status"] as? String ?: return
        
        // Draw contact info
        canvas.drawText(name, arObject.position.x, arObject.position.y, paint)
        paint.textSize = 30f
        canvas.drawText(status, arObject.position.x, arObject.position.y + 40f, paint)
        paint.textSize = 40f
    }

    private fun drawTask(canvas: Canvas, arObject: ARObject) {
        paint.color = ContextCompat.getColor(context, android.R.color.holo_orange_light)
        
        val title = arObject.data["title"] as? String ?: return
        val deadline = arObject.data["deadline"] as? String ?: return
        
        // Draw task info
        canvas.drawText(title, arObject.position.x, arObject.position.y, paint)
        paint.textSize = 30f
        canvas.drawText(deadline, arObject.position.x, arObject.position.y + 40f, paint)
        paint.textSize = 40f
    }

    private fun drawHealthMetric(canvas: Canvas, arObject: ARObject) {
        paint.color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        
        val metric = arObject.data["metric"] as? String ?: return
        val value = arObject.data["value"] as? String ?: return
        
        // Draw health metric
        canvas.drawText(metric, arObject.position.x, arObject.position.y, paint)
        paint.textSize = 30f
        canvas.drawText(value, arObject.position.x, arObject.position.y + 40f, paint)
        paint.textSize = 40f
    }

    private fun drawDeviceStatus(canvas: Canvas, arObject: ARObject) {
        paint.color = Color.WHITE
        paint.alpha = 180
        
        val status = arObject.data["status"] as? String ?: return
        val value = arObject.data["value"] as? String ?: return
        
        // Draw device status
        canvas.drawText(status, arObject.position.x, arObject.position.y, paint)
        paint.textSize = 30f
        canvas.drawText(value, arObject.position.x, arObject.position.y + 40f, paint)
        paint.textSize = 40f
    }
} 