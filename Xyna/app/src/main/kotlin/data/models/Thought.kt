package data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "thoughts")
data class Thought(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val content: String,
    val type: ThoughtType,
    val emotionalContext: String?,
    val relatedMemoryIds: List<Long>?,
    val confidence: Float,
    val timestamp: Date,
    val isPrivate: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<String>? = null
)

enum class ThoughtType {
    OBSERVATION,      // Direct observations about user or environment
    REFLECTION,       // Deep thinking about past events or patterns
    DECISION,         // Choices made by the AI
    EMOTION,          // Emotional responses or realizations
    GOAL,             // Goals or objectives set
    MEMORY_RECALL,    // Recalled memories
    LEARNING,         // New knowledge or insights gained
    PREDICTION,       // Future predictions or expectations
    QUESTION,         // Questions or uncertainties
    ACTION            // Actions taken or to be taken
}