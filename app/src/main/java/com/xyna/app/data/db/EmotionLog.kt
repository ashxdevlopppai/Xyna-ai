package com.xyna.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emotion_logs")
data class EmotionLog(
    @PrimaryKey(autoGenerate = true) val eid: Long = 0,
    val timestamp: Long,
    val mood: String, // e.g., "joy", "sadness", "anger"
    val intensity: Float // A value from 0.0 to 1.0
)
