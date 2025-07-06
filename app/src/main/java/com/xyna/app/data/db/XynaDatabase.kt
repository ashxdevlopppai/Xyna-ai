package com.xyna.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Memory::class, EmotionLog::class], version = 1, exportSchema = false)
abstract class XynaDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun emotionLogDao(): EmotionLogDao

    companion object {
        @Volatile
        private var INSTANCE: XynaDatabase? = null

        fun getDatabase(context: Context): XynaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    XynaDatabase::class.java,
                    "xyna_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
