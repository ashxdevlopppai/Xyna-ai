package com.javris.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import com.javris.assistant.util.ApiConfig
import org.json.JSONObject

class ScreenAssistService : AccessibilityService() {

    private var isMonitoring = false
    private var overlayView: FrameLayout? = null
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val mediaProjectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private var mediaProjection: MediaProjection? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val screenState: Flow<ScreenState> = _screenState

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "screen_assist"
        private const val NOTIFICATION_ID = 2001
    }

    data class ScreenContext(
        val currentApp: String,
        val currentActivity: String,
        val visibleText: List<String>,
        val clickableElements: List<String>,
        val editableFields: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    sealed class ScreenState {
        object Idle : ScreenState()
        data class Monitoring(val context: ScreenContext) : ScreenState()
        data class Assisting(
            val context: ScreenContext,
            val suggestion: String,
            val actions: List<AssistAction>
        ) : ScreenState()
        data class Error(val message: String) : ScreenState()
    }

    data class AssistAction(
        val name: String,
        val description: String,
        val execute: () -> Unit
    )

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
        serviceInfo = info
        
        showNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isMonitoring) return
        
        coroutineScope.launch {
            try {
                val context = extractScreenContext(event)
                _screenState.emit(ScreenState.Monitoring(context))
                
                // Analyze context and provide assistance
                val assistance = analyzeContext(context)
                if (assistance != null) {
                    _screenState.emit(ScreenState.Assisting(
                        context = context,
                        suggestion = assistance.first,
                        actions = assistance.second
                    ))
                }
            } catch (e: Exception) {
                _screenState.emit(ScreenState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    override fun onInterrupt() {
        stopMonitoring()
    }

    fun startMonitoring() {
        isMonitoring = true
        showOverlay()
    }

    fun stopMonitoring() {
        isMonitoring = false
        removeOverlay()
        _screenState.tryEmit(ScreenState.Idle)
    }

    private fun extractScreenContext(event: AccessibilityEvent): ScreenContext {
        val rootNode = rootInActiveWindow ?: throw IllegalStateException("No active window")
        
        return ScreenContext(
            currentApp = event.packageName?.toString() ?: "",
            currentActivity = event.className?.toString() ?: "",
            visibleText = extractVisibleText(rootNode),
            clickableElements = extractClickableElements(rootNode),
            editableFields = extractEditableFields(rootNode)
        )
    }

    private fun extractVisibleText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        
        if (!node.text.isNullOrEmpty()) {
            texts.add(node.text.toString())
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                texts.addAll(extractVisibleText(child))
                child.recycle()
            }
        }
        
        return texts
    }

    private fun extractClickableElements(node: AccessibilityNodeInfo): List<String> {
        val elements = mutableListOf<String>()
        
        if (node.isClickable && !node.text.isNullOrEmpty()) {
            elements.add(node.text.toString())
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                elements.addAll(extractClickableElements(child))
                child.recycle()
            }
        }
        
        return elements
    }

    private fun extractEditableFields(node: AccessibilityNodeInfo): List<String> {
        val fields = mutableListOf<String>()
        
        if (node.isEditable) {
            node.text?.toString()?.let { fields.add(it) }
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                fields.addAll(extractEditableFields(child))
                child.recycle()
            }
        }
        
        return fields
    }

    private suspend fun analyzeContext(context: ScreenContext): Pair<String, List<AssistAction>>? {
        // Prepare context for AI analysis
        val prompt = """
            Current App: ${context.currentApp}
            Current Activity: ${context.currentActivity}
            Visible Text: ${context.visibleText.joinToString("\n")}
            Clickable Elements: ${context.clickableElements.joinToString(", ")}
            Editable Fields: ${context.editableFields.joinToString(", ")}
            
            Based on this context, provide assistance and possible actions.
        """.trimIndent()

        // Get AI suggestions
        val response = getAISuggestions(prompt)
        if (response.isNullOrEmpty()) return null

        // Parse AI response
        val suggestion = response.lines().first()
        val actions = parseActions(response, context)

        return suggestion to actions
    }

    private suspend fun getAISuggestions(prompt: String): String? {
        return try {
            // Use HybridAIService for suggestions
            val hybridAI = HybridAIService(this)
            hybridAI.processInput(prompt).collect { response ->
                return response.text
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseActions(aiResponse: String, context: ScreenContext): List<AssistAction> {
        val actions = mutableListOf<AssistAction>()
        
        // Add navigation actions
        context.clickableElements.forEach { element ->
            actions.add(AssistAction(
                name = "Click '$element'",
                description = "Click on the '$element' element",
                execute = { performClick(element) }
            ))
        }
        
        // Add input actions
        context.editableFields.forEach { field ->
            actions.add(AssistAction(
                name = "Fill '$field'",
                description = "Enter text in the '$field' field",
                execute = { performInput(field) }
            ))
        }
        
        // Add app-specific actions
        when (context.currentApp) {
            "com.android.chrome" -> actions.add(AssistAction(
                name = "Search Web",
                description = "Search the web for relevant information",
                execute = { performWebSearch(context.visibleText.firstOrNull() ?: "") }
            ))
            "com.whatsapp" -> actions.add(AssistAction(
                name = "Smart Reply",
                description = "Generate a smart reply based on the conversation",
                execute = { generateSmartReply(context.visibleText) }
            ))
        }
        
        return actions
    }

    private fun performClick(elementText: String) {
        val rootNode = rootInActiveWindow ?: return
        findAndClickNode(rootNode, elementText)
        rootNode.recycle()
    }

    private fun performInput(fieldText: String) {
        val rootNode = rootInActiveWindow ?: return
        findAndFillNode(rootNode, fieldText)
        rootNode.recycle()
    }

    private fun performWebSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun generateSmartReply(conversation: List<String>) {
        // Implement smart reply generation
    }

    private fun findAndClickNode(node: AccessibilityNodeInfo, text: String) {
        if (node.text?.toString() == text && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAndClickNode(child, text)
                child.recycle()
            }
        }
    }

    private fun findAndFillNode(node: AccessibilityNodeInfo, text: String) {
        if (node.text?.toString() == text && node.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            return
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAndFillNode(child, text)
                child.recycle()
            }
        }
    }

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        overlayView = FrameLayout(this).apply {
            // Add overlay UI elements
        }
        
        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun showNotification() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screen Assistant Active")
            .setContentText("Monitoring your screen for assistance")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
} 