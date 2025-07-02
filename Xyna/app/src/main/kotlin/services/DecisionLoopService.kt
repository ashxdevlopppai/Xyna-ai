package services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.*
import core.XynaCoreBrain
import data.database.XynaDatabase
import data.models.Thought
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class DecisionLoopService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var xynaBrain: XynaCoreBrain
    private lateinit var xynaDatabase: XynaDatabase
    private var isThinking = false

    override fun onCreate() {
        super.onCreate()
        xynaBrain = XynaCoreBrain.getInstance(this)
        xynaDatabase = XynaDatabase.getInstance(this)
        startDecisionLoop()
        schedulePeriodicTasks()
    }

    private fun startDecisionLoop() {
        isThinking = true
        serviceScope.launch {
            while (isThinking) {
                try {
                    // Process current context and generate thoughts
                    processCurrentContext()
                    // Generate and execute decisions
                    makeDecisions()
                    // Evolve emotional state
                    updateEmotionalState()
                    delay(5000) // Think every 5 seconds
                } catch (e: Exception) {
                    // Log error and continue
                    logError(e)
                }
            }
        }
    }

    private suspend fun processCurrentContext() {
        xynaBrain.executeCommand(
            "analyze_context",
            onSuccess = { context ->
                // Store thought in database
                xynaDatabase.thoughtDao().insert(
                    Thought(
                        timestamp = System.currentTimeMillis(),
                        content = context,
                        type = "context_analysis"
                    )
                )
            },
            onError = { logError(it) }
        )
    }

    private suspend fun makeDecisions() {
        xynaBrain.executeCommand(
            "make_decision",
            onSuccess = { decision ->
                executeDecision(decision)
            },
            onError = { logError(it) }
        )
    }

    private suspend fun executeDecision(decision: String) {
        // Parse and execute the decision
        when {
            decision.startsWith("speak:") -> {
                // Trigger speech via AudioManager
            }
            decision.startsWith("learn:") -> {
                // Trigger learning process
                scheduleLearningTask(decision)
            }
            decision.startsWith("evolve:") -> {
                // Trigger self-evolution
                scheduleSelfEvolution()
            }
        }
    }

    private suspend fun updateEmotionalState() {
        xynaBrain.executeCommand(
            "update_emotion",
            onSuccess = { emotion ->
                // Update UI via broadcast
                sendBroadcast(Intent(ACTION_EMOTION_UPDATED).apply {
                    putExtra(EXTRA_EMOTION, emotion)
                })
            },
            onError = { logError(it) }
        )
    }

    private fun schedulePeriodicTasks() {
        // Schedule periodic memory consolidation
        val memoryWork = PeriodicWorkRequestBuilder<MemoryWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "memory_consolidation",
            ExistingPeriodicWorkPolicy.KEEP,
            memoryWork
        )

        // Schedule daily self-improvement
        val evolutionWork = PeriodicWorkRequestBuilder<EvolutionWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "self_evolution",
            ExistingPeriodicWorkPolicy.KEEP,
            evolutionWork
        )
    }

    private fun scheduleLearningTask(learningGoal: String) {
        val learningWork = OneTimeWorkRequestBuilder<LearningWorker>()
            .setInputData(workDataOf("goal" to learningGoal))
            .build()

        WorkManager.getInstance(this).enqueue(learningWork)
    }

    private fun scheduleSelfEvolution() {
        val evolutionWork = OneTimeWorkRequestBuilder<EvolutionWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build())
            .build()

        WorkManager.getInstance(this).enqueue(evolutionWork)
    }

    private fun logError(error: Exception) {
        serviceScope.launch {
            xynaDatabase.logDao().insert(
                data.models.Log(
                    timestamp = System.currentTimeMillis(),
                    type = "error",
                    content = error.toString()
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isThinking = false
    }

    companion object {
        const val ACTION_EMOTION_UPDATED = "com.xyna.EMOTION_UPDATED"
        const val EXTRA_EMOTION = "emotion"
    }
}