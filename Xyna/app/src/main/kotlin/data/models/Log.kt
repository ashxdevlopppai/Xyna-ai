package data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "logs")
data class Log(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val type: LogType,
    val severity: LogSeverity,
    val message: String,
    val details: String?,
    val source: String,          // Component/module that generated the log
    val timestamp: Date,
    val stackTrace: String?,
    val userId: String?,         // For multi-user support in future
    val correlationId: String?,  // For tracking related log entries
    val metadata: Map<String, String>? = null // Additional contextual data
)

enum class LogType {
    SYSTEM,         // System-level events (startup, shutdown, etc.)
    SECURITY,       // Security-related events
    PERFORMANCE,    // Performance metrics and issues
    USER_ACTION,    // User interactions and activities
    AI_DECISION,    // AI decision-making processes
    MODEL_LOADING,  // AI model loading/unloading events
    MEMORY,         // Memory-related operations
    EMOTION,        // Emotional state changes
    NETWORK,        // Network operations
    SENSOR,         // Sensor data and events
    ERROR,          // Error conditions
    DEBUG,          // Debug information
    MAINTENANCE     // System maintenance activities
}

enum class LogSeverity {
    TRACE,      // Finest-grained informational events
    DEBUG,      // Debugging information
    INFO,       // Normal operational messages
    NOTICE,     // Normal but significant events
    WARNING,    // Potentially harmful situations
    ERROR,      // Error events that might still allow the system to continue
    CRITICAL,   // Critical conditions that require immediate attention
    ALERT,      // System is in a critical state and requires immediate action
    EMERGENCY   // System is unusable
}

// Extension functions for log analysis
fun Log.isError(): Boolean {
    return severity in listOf(
        LogSeverity.ERROR,
        LogSeverity.CRITICAL,
        LogSeverity.ALERT,
        LogSeverity.EMERGENCY
    )
}

fun Log.requiresImmediate(): Boolean {
    return severity in listOf(
        LogSeverity.CRITICAL,
        LogSeverity.ALERT,
        LogSeverity.EMERGENCY
    )
}

fun Log.formatForDisplay(): String {
    return "[$severity] ${timestamp}: $message"
}

fun Log.getMetadataValue(key: String): String? {
    return metadata?.get(key)
}