package com.javris.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.javris.assistant.service.ActivityMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailySummaryReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val activityMonitorService = ActivityMonitorService(context)
        
        // Use coroutine to handle async operations
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Generate daily summary
                val report = activityMonitorService.generateDailySummary()
                
                // Show notification with summary
                activityMonitorService.showDailySummaryNotification(report)
                
                // Save report to local storage
                saveDailyReport(context, report)
            } catch (e: Exception) {
                // Handle errors
            }
        }
    }
    
    private fun saveDailyReport(context: Context, report: ActivityMonitorService.DailyReport) {
        // Save report to local storage for history tracking
        context.getSharedPreferences("daily_reports", Context.MODE_PRIVATE)
            .edit()
            .putString(report.date, report.toString())
            .apply()
    }
} 