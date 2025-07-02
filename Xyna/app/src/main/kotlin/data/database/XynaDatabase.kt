package data.database

import android.content.Context
import androidx.room.*
import data.models.*
import java.util.Date
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        Thought::class,
        Emotion::class,
        Log::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class XynaDatabase : RoomDatabase() {
    abstract fun thoughtDao(): ThoughtDao
    abstract fun emotionDao(): EmotionDao
    abstract fun logDao(): LogDao

    companion object {
        private const val DATABASE_NAME = "xyna_database"

        @Volatile
        private var instance: XynaDatabase? = null

        fun getInstance(context: Context): XynaDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): XynaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                XynaDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}

@Dao
interface ThoughtDao {
    @Query("SELECT * FROM thoughts ORDER BY timestamp DESC")
    fun getAllThoughts(): Flow<List<Thought>>

    @Query("SELECT * FROM thoughts WHERE type = :type ORDER BY timestamp DESC")
    fun getThoughtsByType(type: ThoughtType): Flow<List<Thought>>

    @Query("SELECT * FROM thoughts WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getRecentThoughts(since: Date): Flow<List<Thought>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThought(thought: Thought): Long

    @Update
    suspend fun updateThought(thought: Thought)

    @Delete
    suspend fun deleteThought(thought: Thought)

    @Query("DELETE FROM thoughts WHERE isArchived = 1 AND timestamp < :before")
    suspend fun cleanupArchivedThoughts(before: Date)
}

@Dao
interface EmotionDao {
    @Query("SELECT * FROM emotions ORDER BY timestamp DESC")
    fun getAllEmotions(): Flow<List<Emotion>>

    @Query("SELECT * FROM emotions WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getRecentEmotions(since: Date): Flow<List<Emotion>>

    @Query("SELECT * FROM emotions WHERE trigger = :trigger ORDER BY timestamp DESC")
    fun getEmotionsByTrigger(trigger: EmotionTrigger): Flow<List<Emotion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmotion(emotion: Emotion): Long

    @Update
    suspend fun updateEmotion(emotion: Emotion)

    @Delete
    suspend fun deleteEmotion(emotion: Emotion)

    @Query("SELECT AVG(happiness) as happiness, AVG(sadness) as sadness, AVG(anger) as anger, " +
           "AVG(fear) as fear, AVG(trust) as trust, AVG(love) as love " +
           "FROM emotions WHERE timestamp >= :since")
    suspend fun getAverageEmotionalState(since: Date): EmotionalStateAverage
}

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<Log>>

    @Query("SELECT * FROM logs WHERE type = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: LogType): Flow<List<Log>>

    @Query("SELECT * FROM logs WHERE severity IN (:severities) ORDER BY timestamp DESC")
    fun getLogsBySeverity(severities: List<LogSeverity>): Flow<List<Log>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: Log): Long

    @Update
    suspend fun updateLog(log: Log)

    @Delete
    suspend fun deleteLog(log: Log)

    @Query("DELETE FROM logs WHERE timestamp < :before AND severity NOT IN (:keepSeverities)")
    suspend fun cleanupOldLogs(before: Date, keepSeverities: List<LogSeverity>)
}

data class EmotionalStateAverage(
    val happiness: Float,
    val sadness: Float,
    val anger: Float,
    val fear: Float,
    val trust: Float,
    val love: Float
)

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun stringListToString(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun fromLongList(value: String?): List<Long>? {
        return value?.split(",")?.filter { it.isNotEmpty() }?.map { it.toLong() }
    }

    @TypeConverter
    fun longListToString(list: List<Long>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, String>? {
        return value?.split(",")?
            .filter { it.isNotEmpty() }
            .map { it.split(":") }
            .filter { it.size == 2 }
            .associate { it[0] to it[1] }
    }

    @TypeConverter
    fun stringMapToString(map: Map<String, String>?): String? {
        return map?.map { "${it.key}:${it.value}" }?.joinToString(",")
    }
}