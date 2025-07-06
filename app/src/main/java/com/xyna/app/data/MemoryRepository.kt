package com.xyna.app.data

import com.xyna.app.data.db.EmotionLog
import com.xyna.app.data.db.Memory
import com.xyna.app.data.db.XynaDatabase

class MemoryRepository(private val database: XynaDatabase) {

    private val memoryDao = database.memoryDao()
    private val emotionLogDao = database.emotionLogDao()

    suspend fun insertMemory(memory: Memory) {
        memoryDao.insertMemory(memory)
    }

    suspend fun getRecentMemories(limit: Int): List<Memory> {
        return memoryDao.getRecentMemories(limit)
    }

    suspend fun searchMemories(query: String): List<Memory> {
        return memoryDao.searchMemories("%$query%")
    }

    suspend fun insertEmotionLog(emotionLog: EmotionLog) {
        emotionLogDao.insertEmotionLog(emotionLog)
    }

    suspend fun getRecentEmotionLogs(limit: Int): List<EmotionLog> {
        return emotionLogDao.getRecentEmotionLogs(limit)
    }
    
    suspend fun getEmotionLogsSince(timestamp: Long): List<EmotionLog> {
        return emotionLogDao.getEmotionLogsSince(timestamp)
    }
}
