package services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import ui.FloatingOrb

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private var isOrbVisible = true

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        overlayView = ComposeView(this).apply {
            setContent {
                FloatingOrbContent()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        windowManager.addView(overlayView, params)
    }

    @Composable
    private fun FloatingOrbContent() {
        FloatingOrb(
            onTap = { /* Toggle voice activation */ },
            onLongPress = { /* Show expanded menu */ },
            onDrag = { dx, dy -> updateOrbPosition(dx, dy) }
        )
    }

    private fun updateOrbPosition(dx: Float, dy: Float) {
        val params = overlayView.layoutParams as WindowManager.LayoutParams
        params.x += dx.toInt()
        params.y += dy.toInt()
        windowManager.updateViewLayout(overlayView, params)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}