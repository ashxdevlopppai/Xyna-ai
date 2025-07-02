package com.javris.assistant.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*
import java.text.SimpleDateFormat
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ActivityMonitorService(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs: SharedPreferences = context.getSharedPreferences("activity_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "daily_summary"
        private const val SUMMARY_NOTIFICATION_ID = 1001
        private const val DAILY_SUMMARY_HOUR = 22 // 10 PM
    }
    
    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val duration: Long,
        val lastTimeUsed: Long,
        val category: String,
        val isProductiveApp: Boolean
    )
    
    data class DailyReport(
        val date: String,
        val totalScreenTime: Long,
        val productiveTime: Long,
        val entertainmentTime: Long,
        val socialTime: Long,
        val mostUsedApps: List<AppUsageInfo>,
        val notifications: Int,
        val unlockCount: Int,
        val healthMetrics: HealthMetrics,
        val productivityScore: Float,
        val focusSessions: List<FocusSession>,
        val distractions: List<Distraction>,
        val recommendations: List<String>
    )
    
    data class HealthMetrics(
        val eyeStrainWarnings: Int,
        val postureSuggestions: Int,
        val breakReminders: Int,
        val steps: Int,
        val activeMinutes: Int,
        val screenBreaks: Int,
        val waterReminders: Int,
        val ergonomicAlerts: Int
    )
    
    data class FocusSession(
        val startTime: Long,
        val duration: Long,
        val apps: List<String>,
        val productivity: Float
    )
    
    data class Distraction(
        val time: Long,
        val app: String,
        val duration: Long
    )

    init {
        scheduleDailySummary()
    }

    private fun scheduleDailySummary() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, DAILY_SUMMARY_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, DailySummaryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun generateDailySummary(): DailyReport {
        val calendar = Calendar.getInstance()
        val startTime = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            System.currentTimeMillis()
        )
        
        val appUsage = processAppUsage(stats)
        val focusSessions = getFocusSessions(startTime)
        val distractions = getDistractions(startTime)
        val healthMetrics = getHealthMetrics()
        
        val totalScreenTime = stats.sumOf { it.totalTimeInForeground }
        val productiveTime = appUsage.filter { it.isProductiveApp }.sumOf { it.duration }
        val entertainmentTime = appUsage.filter { it.category == "Entertainment" }.sumOf { it.duration }
        val socialTime = appUsage.filter { it.category == "Social" }.sumOf { it.duration }
        
        val productivityScore = calculateProductivityScore(
            productiveTime,
            totalScreenTime,
            focusSessions,
            distractions
        )
        
        return DailyReport(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            totalScreenTime = totalScreenTime,
            productiveTime = productiveTime,
            entertainmentTime = entertainmentTime,
            socialTime = socialTime,
            mostUsedApps = appUsage.sortedByDescending { it.duration }.take(5),
            notifications = getNotificationCount(startTime),
            unlockCount = getUnlockCount(startTime),
            healthMetrics = healthMetrics,
            productivityScore = productivityScore,
            focusSessions = focusSessions,
            distractions = distractions,
            recommendations = generateRecommendations(
                totalScreenTime,
                productivityScore,
                healthMetrics,
                distractions
            )
        )
    }

    private fun processAppUsage(stats: List<android.app.usage.UsageStats>): List<AppUsageInfo> {
        return stats.mapNotNull { stat ->
            try {
                val appInfo = packageManager.getApplicationInfo(stat.packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val category = getAppCategory(stat.packageName)
                
                AppUsageInfo(
                    packageName = stat.packageName,
                    appName = appName,
                    duration = stat.totalTimeInForeground,
                    lastTimeUsed = stat.lastTimeUsed,
                    category = category,
                    isProductiveApp = isProductiveApp(category)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    private fun getFocusSessions(startTime: Long): List<FocusSession> {
        val sessions = mutableListOf<FocusSession>()
        val events = usageStatsManager.queryEvents(startTime, System.currentTimeMillis())
        var currentSession: MutableList<String>? = null
        var sessionStart = 0L
        
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (isProductiveApp(getAppCategory(event.packageName))) {
                        if (currentSession == null) {
                            currentSession = mutableListOf()
                            sessionStart = event.timeStamp
                        }
                        currentSession.add(event.packageName)
                    } else {
                        currentSession?.let {
                            if (event.timeStamp - sessionStart >= 15 * 60 * 1000) { // 15 minutes minimum
                                sessions.add(
                                    FocusSession(
                                        startTime = sessionStart,
                                        duration = event.timeStamp - sessionStart,
                                        apps = it.distinct(),
                                        productivity = calculateSessionProductivity(it)
                                    )
                                )
                            }
                            currentSession = null
                        }
                    }
                }
            }
        }
        
        return sessions
    }

    private fun getDistractions(startTime: Long): List<Distraction> {
        val distractions = mutableListOf<Distraction>()
        val events = usageStatsManager.queryEvents(startTime, System.currentTimeMillis())
        var lastProductiveTime = 0L
        
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val category = getAppCategory(event.packageName)
                if (!isProductiveApp(category) && 
                    (category == "Entertainment" || category == "Social")) {
                    if (lastProductiveTime > 0 && 
                        event.timeStamp - lastProductiveTime < 30 * 60 * 1000) { // Within 30 minutes
                        distractions.add(
                            Distraction(
                                time = event.timeStamp,
                                app = event.packageName,
                                duration = getAppUsageDuration(event.packageName, event.timeStamp)
                            )
                        )
                    }
                } else if (isProductiveApp(category)) {
                    lastProductiveTime = event.timeStamp
                }
            }
        }
        
        return distractions
    }

    private fun calculateProductivityScore(
        productiveTime: Long,
        totalScreenTime: Long,
        focusSessions: List<FocusSession>,
        distractions: List<Distraction>
    ): Float {
        val productivityRatio = if (totalScreenTime > 0) {
            productiveTime.toFloat() / totalScreenTime
        } else 0f
        
        val focusScore = focusSessions.size * 0.1f
        val distractionPenalty = distractions.size * -0.05f
        
        return (productivityRatio * 0.6f + focusScore * 0.3f + distractionPenalty * 0.1f)
            .coerceIn(0f, 1f)
    }

    private fun generateRecommendations(
        totalScreenTime: Long,
        productivityScore: Float,
        healthMetrics: HealthMetrics,
        distractions: List<Distraction>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Screen time recommendations
        if (totalScreenTime > 8 * 60 * 60 * 1000) { // More than 8 hours
            recommendations.add("Consider reducing your screen time for better eye health")
        }
        
        // Productivity recommendations
        if (productivityScore < 0.4f) {
            recommendations.add("Try using app blockers during work hours to improve focus")
        }
        
        // Health recommendations
        if (healthMetrics.breakReminders > 5) {
            recommendations.add("Take regular breaks to prevent eye strain and maintain posture")
        }
        
        // Distraction recommendations
        if (distractions.size > 10) {
            recommendations.add("Consider turning off notifications during focus sessions")
        }
        
        return recommendations
    }

    private fun showDailySummaryNotification(report: DailyReport) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Your Daily Activity Summary")
            .setContentText("Productivity Score: ${(report.productivityScore * 100).toInt()}%")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("""
                    Screen Time: ${formatDuration(report.totalScreenTime)}
                    Productive Time: ${formatDuration(report.productiveTime)}
                    Focus Sessions: ${report.focusSessions.size}
                    Top Recommendation: ${report.recommendations.firstOrNull() ?: "Great job today!"}
                """.trimIndent()))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun formatDuration(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
        return "${hours}h ${minutes}m"
    }

    private fun getAppCategory(packageName: String): String {
        return when {
            packageName.contains("game") -> "Entertainment"
            packageName.contains("netflix") || packageName.contains("youtube") -> "Entertainment"
            packageName.contains("whatsapp") || packageName.contains("telegram") -> "Social"
            packageName.contains("instagram") || packageName.contains("facebook") -> "Social"
            packageName.contains("gmail") || packageName.contains("outlook") -> "Productivity"
            packageName.contains("docs") || packageName.contains("office") -> "Productivity"
            packageName.contains("calendar") || packageName.contains("tasks") -> "Productivity"
            packageName.contains("health") || packageName.contains("fitness") -> "Health"
            packageName.contains("meditation") || packageName.contains("mindfulness") -> "Wellness"
            else -> "Other"
        }
    }

    private fun isProductiveApp(category: String): Boolean {
        return category in setOf("Productivity", "Health", "Wellness", "Education")
    }

    private fun calculateSessionProductivity(apps: List<String>): Float {
        val productiveApps = apps.count { isProductiveApp(getAppCategory(it)) }
        return (productiveApps.toFloat() / apps.size).coerceIn(0f, 1f)
    }

    private fun getAppUsageDuration(packageName: String, startTime: Long): Long {
        val endTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        return stats.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
    }

    private fun getNotificationCount(startTime: Long): Int {
        // Implementation depends on notification access permission
        return prefs.getInt("notification_count", 0)
    }

    private fun getUnlockCount(startTime: Long): Int {
        // Implementation depends on usage access permission
        return prefs.getInt("unlock_count", 0)
    }

    private fun getHealthMetrics(): HealthMetrics {
        return HealthMetrics(
            eyeStrainWarnings = prefs.getInt("eye_strain_warnings", 0),
            postureSuggestions = prefs.getInt("posture_suggestions", 0),
            breakReminders = prefs.getInt("break_reminders", 0),
            steps = prefs.getInt("steps", 0),
            activeMinutes = prefs.getInt("active_minutes", 0),
            screenBreaks = prefs.getInt("screen_breaks", 0),
            waterReminders = prefs.getInt("water_reminders", 0),
            ergonomicAlerts = prefs.getInt("ergonomic_alerts", 0)
        )
    }
} 