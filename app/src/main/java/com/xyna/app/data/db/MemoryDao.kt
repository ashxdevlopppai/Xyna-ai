package com.xyna.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory)

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<Memory>

    @Query("SELECT * FROM memories WHERE content LIKE :query ORDER BY timestamp DESC")
    suspend fun searchMemories(query: String): List<Memory>
}
