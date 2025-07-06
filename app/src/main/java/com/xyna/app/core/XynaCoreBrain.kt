package com.xyna.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xyna.app.data.MemoryRepository
import com.xyna.app.data.db.XynaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object XynaCoreBrain {

    private lateinit var memoryRepository: MemoryRepository

    // Simple state for now, will be expanded
    private val _conversationState = MutableStateFlow<List<String>>(emptyList())
    val conversationState = _conversationState.asStateFlow()

    fun initialize(context: Context) {
        val database = XynaDatabase.getDatabase(context)
        memoryRepository = MemoryRepository(database)
    }

    fun processUserInput(text: String) {
        // Add user input to state
        val currentConversation = _conversationState.value.toMutableList()
        currentConversation.add("You: $text")

        // TODO: Call reasoning engine
        val response = "Xyna: You said '$text'"
        currentConversation.add(response)

        _conversationState.value = currentConversation

        // TODO: Save to memory repository
    }

    val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.xyna.app.WAKE_WORD_DETECTED") {
                // TODO: Bring app to foreground or show overlay
            }
        }
    }
}
