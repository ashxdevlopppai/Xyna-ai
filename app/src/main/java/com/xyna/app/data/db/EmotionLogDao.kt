package com.xyna.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmotionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmotionLog(emotionLog: EmotionLog)

    @Query("SELECT * FROM emotion_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEmotionLogs(limit: Int): List<EmotionLog>

    @Query("SELECT * FROM emotion_logs WHERE timestamp >= :sinceTimestamp")
    suspend fun getEmotionLogsSince(sinceTimestamp: Long): List<EmotionLog>
}
