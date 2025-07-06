package com.xyna.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String, // e.g., "USER_INPUT", "XYNA_RESPONSE", "SYSTEM_NOTE"
    val content: String
)
