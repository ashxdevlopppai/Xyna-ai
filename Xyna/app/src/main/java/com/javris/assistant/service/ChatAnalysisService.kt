package com.javris.assistant.service

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class ChatAnalysisService(private val context: Context) {

    data class ChatMessage(
        val id: String,
        val contactName: String,
        val message: String,
        val timestamp: Long,
        val isIncoming: Boolean,
        val intent: MessageIntent,
        val sentiment: Float,
        val suggestions: List<String>
    )

    data class ChatFolder(
        val contactName: String,
        val messages: List<ChatMessage>,
        val lastInteraction: Long,
        val overallSentiment: Float,
        val commonTopics: List<String>,
        val contactStatus: String
    )

    enum class MessageIntent {
        FRIENDLY,
        BUSINESS,
        REQUEST,
        COMPLAINT,
        URGENT,
        CASUAL,
        PERSONAL,
        UNKNOWN
    }

    private val chatFolders = mutableMapOf<String, ChatFolder>()
    private val hinglishPhrases = mapOf(
        "hello" to listOf("Kaise ho?", "Namaste!", "Hey kya haal hai?"),
        "goodbye" to listOf("Alvida!", "Phir milenge!", "Bye bye!"),
        "thanks" to listOf("Shukriya!", "Thank you yaar!", "Bahut bahut dhanyavaad!"),
        "sorry" to listOf("Maaf kardo yaar!", "Sorry yaar!", "Galti ho gayi!"),
        "agree" to listOf("Haan bilkul!", "Ekdum sahi!", "Perfect hai!"),
        "disagree" to listOf("Nahi yaar!", "Mujhe nahi lagta!", "I don't think so yaar!")
    )

    fun analyzeChatInRealTime(contactName: String): Flow<ChatMessage> = flow {
        val messageObserver = MessageObserver { message ->
            val analyzedMessage = analyzeMessage(contactName, message)
            updateChatFolder(contactName, analyzedMessage)
            emit(analyzedMessage)
        }
        // Start observing messages
        observeMessages(contactName, messageObserver)
    }

    fun getChatFolder(contactName: String): ChatFolder? = chatFolders[contactName]

    fun getSuggestions(message: String, context: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Generate context-aware Hinglish suggestions
        when {
            message.contains("hello", ignoreCase = true) -> 
                suggestions.addAll(hinglishPhrases["hello"] ?: emptyList())
            message.contains("bye", ignoreCase = true) -> 
                suggestions.addAll(hinglishPhrases["goodbye"] ?: emptyList())
            message.contains("thank", ignoreCase = true) -> 
                suggestions.addAll(hinglishPhrases["thanks"] ?: emptyList())
            message.contains("sorry", ignoreCase = true) -> 
                suggestions.addAll(hinglishPhrases["sorry"] ?: emptyList())
        }

        // Add custom contextual suggestions
        when (analyzeIntent(message)) {
            MessageIntent.FRIENDLY -> suggestions.add("Arey yaar, tum bhi na! 😊")
            MessageIntent.URGENT -> suggestions.add("Main abhi dekhta/dekhti hoon!")
            MessageIntent.REQUEST -> suggestions.add("Haan zaroor, ho jayega!")
            MessageIntent.COMPLAINT -> suggestions.add("Really sorry yaar, will fix it!")
            else -> suggestions.add("Hmm, interesting!")
        }

        return suggestions.take(3)
    }

    private fun analyzeMessage(contactName: String, message: String): ChatMessage {
        val intent = analyzeIntent(message)
        val sentiment = analyzeSentiment(message)
        val suggestions = getSuggestions(message, contactName)

        return ChatMessage(
            id = System.currentTimeMillis().toString(),
            contactName = contactName,
            message = message,
            timestamp = System.currentTimeMillis(),
            isIncoming = true,
            intent = intent,
            sentiment = sentiment,
            suggestions = suggestions
        )
    }

    private fun analyzeIntent(message: String): MessageIntent {
        return when {
            message.contains(Regex("(urgent|asap|emergency|help)", RegexOption.IGNORE_CASE)) ->
                MessageIntent.URGENT
            message.contains(Regex("(can you|please|could you|would you)", RegexOption.IGNORE_CASE)) ->
                MessageIntent.REQUEST
            message.contains(Regex("(issue|problem|complaint|wrong)", RegexOption.IGNORE_CASE)) ->
                MessageIntent.COMPLAINT
            message.contains(Regex("(love|miss you|heart|dear)", RegexOption.IGNORE_CASE)) ->
                MessageIntent.PERSONAL
            message.contains(Regex("(meeting|work|project|deadline)", RegexOption.IGNORE_CASE)) ->
                MessageIntent.BUSINESS
            message.contains(Regex("(hey|hi|hello|what's up)", RegexOption.IGNORE_CASE)) ->
                MessageIntent.FRIENDLY
            else -> MessageIntent.CASUAL
        }
    }

    private fun analyzeSentiment(message: String): Float {
        val positiveWords = setOf("good", "great", "awesome", "thanks", "love", "happy", "best")
        val negativeWords = setOf("bad", "worst", "hate", "angry", "sad", "disappointed", "sorry")

        val words = message.toLowerCase().split(" ")
        val positiveCount = words.count { it in positiveWords }
        val negativeCount = words.count { it in negativeWords }

        return when {
            positiveCount > negativeCount -> 0.8f
            negativeCount > positiveCount -> -0.8f
            else -> 0f
        }
    }

    private fun updateChatFolder(contactName: String, message: ChatMessage) {
        val currentFolder = chatFolders[contactName]
        val updatedMessages = (currentFolder?.messages ?: emptyList()) + message
        
        chatFolders[contactName] = ChatFolder(
            contactName = contactName,
            messages = updatedMessages,
            lastInteraction = message.timestamp,
            overallSentiment = updatedMessages.map { it.sentiment }.average().toFloat(),
            commonTopics = analyzeCommonTopics(updatedMessages),
            contactStatus = determineContactStatus(updatedMessages)
        )

        // Save chat folder to storage
        saveChatFolder(contactName)
    }

    private fun analyzeCommonTopics(messages: List<ChatMessage>): List<String> {
        val topics = mutableMapOf<String, Int>()
        val topicKeywords = mapOf(
            "work" to listOf("meeting", "project", "deadline", "office"),
            "personal" to listOf("family", "home", "life", "feeling"),
            "social" to listOf("party", "meet", "hangout", "fun"),
            "urgent" to listOf("emergency", "asap", "urgent", "help"),
            "casual" to listOf("weather", "food", "movie", "music")
        )

        messages.forEach { message ->
            topicKeywords.forEach { (topic, keywords) ->
                if (keywords.any { message.message.contains(it, ignoreCase = true) }) {
                    topics[topic] = topics.getOrDefault(topic, 0) + 1
                }
            }
        }

        return topics.entries.sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }

    private fun determineContactStatus(messages: List<ChatMessage>): String {
        val sentiments = messages.map { it.sentiment }
        val averageSentiment = sentiments.average()
        val messageFrequency = messages.size / (messages.maxOf { it.timestamp } - messages.minOf { it.timestamp })
        
        return when {
            averageSentiment > 0.5 && messageFrequency > 0.5 -> "Best Friend"
            averageSentiment > 0.3 -> "Close Contact"
            averageSentiment < -0.3 -> "Needs Follow-up"
            messageFrequency > 0.7 -> "Frequent Contact"
            else -> "Regular Contact"
        }
    }

    private fun saveChatFolder(contactName: String) {
        val folder = chatFolders[contactName] ?: return
        val file = File(context.filesDir, "chats/${contactName.replace(" ", "_")}.json")
        file.parentFile?.mkdirs()
        file.writeText(folder.toString()) // Use proper JSON serialization in production
    }

    private fun loadChatFolder(contactName: String) {
        val file = File(context.filesDir, "chats/${contactName.replace(" ", "_")}.json")
        if (file.exists()) {
            // Load and parse JSON file
            // Implementation depends on your JSON serialization library
        }
    }

    private fun interface MessageObserver {
        fun onMessageReceived(message: String)
    }

    private fun observeMessages(contactName: String, observer: MessageObserver) {
        // Implement message observation logic using ContentObserver
        // This is a placeholder for the actual implementation
    }
} 