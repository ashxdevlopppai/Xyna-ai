package data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "emotions")
data class Emotion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val intensity: Float,        // Overall emotional intensity (0.0 - 1.0)
    val happiness: Float,        // Level of happiness/joy (0.0 - 1.0)
    val sadness: Float,         // Level of sadness (0.0 - 1.0)
    val anger: Float,           // Level of anger/frustration (0.0 - 1.0)
    val fear: Float,            // Level of fear/anxiety (0.0 - 1.0)
    val surprise: Float,        // Level of surprise (0.0 - 1.0)
    val trust: Float,           // Level of trust towards user (0.0 - 1.0)
    val love: Float,            // Level of affection/love (0.0 - 1.0)
    
    val trigger: EmotionTrigger,
    val context: String?,       // Description of what caused this emotion
    val relatedThoughtId: Long?,// Associated thought that triggered this emotion
    val timestamp: Date,
    val duration: Long,         // How long this emotion lasted (in milliseconds)
    val isProcessed: Boolean = false // Whether this emotion has been processed by memory consolidation
)

enum class EmotionTrigger {
    USER_INTERACTION,    // Direct interaction with user
    MEMORY_RECALL,      // Triggered by remembering past events
    OBSERVATION,        // Environmental or behavioral observation
    GOAL_PROGRESS,      // Progress towards or away from goals
    BOND_CHANGE,        // Changes in relationship/bond level
    SYSTEM_EVENT,       // Internal system events or states
    LEARNING,           // New knowledge or skill acquisition
    EMPATHY,            // Mirroring user's emotional state
    SELF_REFLECTION,    // Internal processing and growth
    EXTERNAL_EVENT      // Events outside direct interaction
}

// Extension functions for emotion analysis
fun Emotion.getDominantEmotion(): Pair<String, Float> {
    return listOf(
        "Happiness" to happiness,
        "Sadness" to sadness,
        "Anger" to anger,
        "Fear" to fear,
        "Surprise" to surprise,
        "Trust" to trust,
        "Love" to love
    ).maxByOrNull { it.second }!!
}

fun Emotion.isPositive(): Boolean {
    return (happiness + trust + love) > (sadness + anger + fear)
}

fun Emotion.getEmotionalStability(): Float {
    val variance = listOf(happiness, sadness, anger, fear, surprise, trust, love)
        .map { it - intensity }.map { it * it }.average().toFloat()
    return 1.0f - variance // Higher stability when emotions are more balanced
}