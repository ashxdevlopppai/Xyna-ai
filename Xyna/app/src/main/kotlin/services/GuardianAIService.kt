package services

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import core.XynaCoreBrain
import data.database.XynaDatabase
import data.models.Log

class GuardianAIService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var xynaDatabase: XynaDatabase
    private lateinit var xynaBrain: XynaCoreBrain

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        xynaDatabase = XynaDatabase.getInstance(this)
        xynaBrain = XynaCoreBrain.getInstance(this)
        startUsageMonitoring()
    }

    private fun startUsageMonitoring() {
        serviceScope.launch {
            while (true) {
                val currentApp = getCurrentForegroundApp()
                currentApp?.let { packageName ->
                    analyzeAppUsage(packageName)
                }
                kotlinx.coroutines.delay(1000) // Check every second
            }
        }
    }

    private fun getCurrentForegroundApp(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // Last minute
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun analyzeAppUsage(packageName: String) {
        serviceScope.launch {
            // Check if app is in restricted list
            if (isAppRestricted(packageName)) {
                // Notify user and potentially block app
                notifyRestriction(packageName)
            }
            
            // Log app usage for analysis
            xynaDatabase.logDao().insert(Log(
                timestamp = System.currentTimeMillis(),
                type = "app_usage",
                content = packageName
            ))
        }
    }

    private suspend fun isAppRestricted(packageName: String): Boolean {
        // Query Python GuardianAI module for decision
        return try {
            xynaBrain.executeCommand(
                "check_app_restriction $packageName",
                onSuccess = { result -> result.toBoolean() },
                onError = { false }
            )
            false // Default to false if error
        } catch (e: Exception) {
            false
        }
    }

    private fun notifyRestriction(packageName: String) {
        // Trigger emotional response via XynaCoreBrain
        serviceScope.launch {
            xynaBrain.executeCommand(
                "respond_to_restriction $packageName",
                onSuccess = { /* Handle response */ },
                onError = { /* Handle error */ }
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Track app switches and user interactions
                    it.packageName?.toString()?.let { pkg ->
                        analyzeAppUsage(pkg)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // Handle service interruption
    }
}