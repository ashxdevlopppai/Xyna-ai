package com.javris.assistant.service

import android.content.Context
import android.provider.Telephony
import android.provider.CallLog
import java.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RelationshipAnalysisService(private val context: Context) {

    data class Contact(
        val id: String,
        val name: String,
        val phoneNumber: String,
        val relationshipScore: Float,
        val interactionStats: InteractionStats,
        val communicationTrends: CommunicationTrends,
        val sentimentAnalysis: SentimentAnalysis
    )

    data class InteractionStats(
        val totalMessages: Int,
        val sentMessages: Int,
        val receivedMessages: Int,
        val callsDuration: Long,
        val missedCalls: Int,
        val lastInteraction: Long,
        val responseTime: Long
    )

    data class CommunicationTrends(
        val dailyMessageAverage: Float,
        val peakCommunicationHours: List<Int>,
        val commonTopics: List<String>,
        val commonEmojis: List<String>,
        val messageLength: MessageLengthTrend,
        val responsePattern: ResponsePattern
    )

    data class SentimentAnalysis(
        val overallSentiment: Float, // -1 to 1
        val sentimentTrend: List<Float>,
        val emotionalTone: Map<String, Float>,
        val keyPhrases: List<String>,
        val concernFlags: List<String>
    )

    data class MessageLengthTrend(
        val averageLength: Int,
        val trend: String // "increasing", "decreasing", "stable"
    )

    data class ResponsePattern(
        val averageResponseTime: Long,
        val responseConsistency: Float, // 0 to 1
        val initiationRatio: Float // how often they initiate vs respond
    )

    fun analyzeRelationships(): Flow<List<Contact>> = flow {
        while (true) {
            val contacts = getContacts()
            val analyzedContacts = contacts.map { contact ->
                val interactions = getInteractionStats(contact.phoneNumber)
                val trends = analyzeCommunicationTrends(contact.phoneNumber)
                val sentiment = analyzeSentiment(contact.phoneNumber)
                
                contact.copy(
                    interactionStats = interactions,
                    communicationTrends = trends,
                    sentimentAnalysis = sentiment,
                    relationshipScore = calculateRelationshipScore(interactions, trends, sentiment)
                )
            }
            emit(analyzedContacts)
            kotlinx.coroutines.delay(30 * 60 * 1000) // Update every 30 minutes
        }
    }

    private fun getContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val name = cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER))
                
                contacts.add(Contact(
                    id = id,
                    name = name,
                    phoneNumber = number,
                    relationshipScore = 0f,
                    interactionStats = InteractionStats(0, 0, 0, 0, 0, 0, 0),
                    communicationTrends = CommunicationTrends(
                        0f, emptyList(), emptyList(), emptyList(),
                        MessageLengthTrend(0, "stable"),
                        ResponsePattern(0, 0f, 0f)
                    ),
                    sentimentAnalysis = SentimentAnalysis(0f, emptyList(), emptyMap(), emptyList(), emptyList())
                ))
            }
        }
        return contacts
    }

    private fun getInteractionStats(phoneNumber: String): InteractionStats {
        var totalMessages = 0
        var sentMessages = 0
        var receivedMessages = 0
        var callsDuration = 0L
        var missedCalls = 0
        var lastInteraction = 0L
        var totalResponseTime = 0L
        var responseCount = 0

        // Query SMS
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(phoneNumber),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                totalMessages++
                val type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE))
                val date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE))
                
                if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                    sentMessages++
                } else if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    receivedMessages++
                }
                
                if (date > lastInteraction) {
                    lastInteraction = date
                }
            }
        }

        // Query Call Log
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            "${CallLog.Calls.NUMBER} = ?",
            arrayOf(phoneNumber),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
                val duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION))
                val date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE))
                
                when (type) {
                    CallLog.Calls.MISSED_TYPE -> missedCalls++
                    CallLog.Calls.INCOMING_TYPE, CallLog.Calls.OUTGOING_TYPE -> callsDuration += duration
                }
                
                if (date > lastInteraction) {
                    lastInteraction = date
                }
            }
        }

        val averageResponseTime = if (responseCount > 0) totalResponseTime / responseCount else 0

        return InteractionStats(
            totalMessages = totalMessages,
            sentMessages = sentMessages,
            receivedMessages = receivedMessages,
            callsDuration = callsDuration,
            missedCalls = missedCalls,
            lastInteraction = lastInteraction,
            responseTime = averageResponseTime
        )
    }

    private fun analyzeCommunicationTrends(phoneNumber: String): CommunicationTrends {
        val messagesByHour = IntArray(24)
        val topics = mutableMapOf<String, Int>()
        val emojis = mutableMapOf<String, Int>()
        val messageLengths = mutableListOf<Int>()
        var previousMessageTime = 0L
        var totalResponseTime = 0L
        var responseCount = 0
        var initiatedCount = 0
        var respondedCount = 0

        // Analyze messages
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(phoneNumber),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY))
                val date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE))
                val type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE))
                
                // Hour analysis
                val hour = Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.HOUR_OF_DAY)
                messagesByHour[hour]++
                
                // Message length
                messageLengths.add(message.length)
                
                // Topic and emoji analysis
                analyzeMessageContent(message, topics, emojis)
                
                // Response patterns
                if (previousMessageTime > 0) {
                    val responseTime = date - previousMessageTime
                    if (responseTime < 24 * 60 * 60 * 1000) { // Within 24 hours
                        totalResponseTime += responseTime
                        responseCount++
                    }
                }
                previousMessageTime = date
                
                // Initiation patterns
                if (isNewConversation(date, previousMessageTime)) {
                    if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                        initiatedCount++
                    } else {
                        respondedCount++
                    }
                }
            }
        }

        val peakHours = messagesByHour.mapIndexed { index, count ->
            index to count
        }.sortedByDescending { it.second }.take(3).map { it.first }

        val averageLength = if (messageLengths.isNotEmpty()) messageLengths.average().toInt() else 0
        val lengthTrend = calculateMessageLengthTrend(messageLengths)

        val responsePattern = ResponsePattern(
            averageResponseTime = if (responseCount > 0) totalResponseTime / responseCount else 0,
            responseConsistency = calculateResponseConsistency(totalResponseTime, responseCount),
            initiationRatio = if (respondedCount > 0) initiatedCount.toFloat() / respondedCount else 0f
        )

        return CommunicationTrends(
            dailyMessageAverage = messageLengths.size / 30f, // Assuming 30 days
            peakCommunicationHours = peakHours,
            commonTopics = topics.entries.sortedByDescending { it.value }.take(5).map { it.key },
            commonEmojis = emojis.entries.sortedByDescending { it.value }.take(5).map { it.key },
            messageLength = MessageLengthTrend(averageLength, lengthTrend),
            responsePattern = responsePattern
        )
    }

    private fun analyzeSentiment(phoneNumber: String): SentimentAnalysis {
        val sentiments = mutableListOf<Float>()
        val emotions = mutableMapOf<String, Int>()
        val phrases = mutableSetOf<String>()
        val concerns = mutableListOf<String>()

        // Analyze recent messages
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(phoneNumber),
            "${Telephony.Sms.DATE} DESC LIMIT 100"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY))
                
                // Analyze message sentiment
                val (sentiment, emotion) = analyzeMessageSentiment(message)
                sentiments.add(sentiment)
                emotion?.let { emotions[it] = emotions.getOrDefault(it, 0) + 1 }
                
                // Extract key phrases
                extractKeyPhrases(message).forEach { phrases.add(it) }
                
                // Check for concern flags
                checkConcernFlags(message)?.let { concerns.add(it) }
            }
        }

        return SentimentAnalysis(
            overallSentiment = if (sentiments.isNotEmpty()) sentiments.average().toFloat() else 0f,
            sentimentTrend = sentiments,
            emotionalTone = emotions.mapValues { it.value.toFloat() / emotions.values.sum() },
            keyPhrases = phrases.toList(),
            concernFlags = concerns
        )
    }

    private fun analyzeMessageContent(message: String, topics: MutableMap<String, Int>, emojis: MutableMap<String, Int>) {
        // Simple topic extraction based on keywords
        val topicKeywords = mapOf(
            "work" to listOf("meeting", "project", "deadline", "office"),
            "social" to listOf("party", "dinner", "hangout", "meet"),
            "family" to listOf("mom", "dad", "sister", "brother"),
            "health" to listOf("doctor", "sick", "health", "exercise"),
            "entertainment" to listOf("movie", "game", "play", "watch")
        )

        topicKeywords.forEach { (topic, keywords) ->
            if (keywords.any { message.contains(it, ignoreCase = true) }) {
                topics[topic] = topics.getOrDefault(topic, 0) + 1
            }
        }

        // Emoji extraction
        val emojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+")
        emojiRegex.findAll(message).forEach { match ->
            val emoji = match.value
            emojis[emoji] = emojis.getOrDefault(emoji, 0) + 1
        }
    }

    private fun analyzeMessageSentiment(message: String): Pair<Float, String?> {
        // Simple sentiment analysis based on keywords
        val positiveWords = setOf("happy", "great", "good", "love", "wonderful", "thanks", "thank you")
        val negativeWords = setOf("sad", "bad", "angry", "upset", "sorry", "hate", "unfortunate")
        
        val words = message.toLowerCase().split(" ")
        val positiveCount = words.count { it in positiveWords }
        val negativeCount = words.count { it in negativeWords }
        
        val sentiment = when {
            positiveCount > negativeCount -> 0.5f
            negativeCount > positiveCount -> -0.5f
            else -> 0f
        }

        val emotion = when {
            message.contains("😊") || message.contains("❤️") -> "joy"
            message.contains("😢") || message.contains("😭") -> "sadness"
            message.contains("😠") || message.contains("😡") -> "anger"
            message.contains("😨") || message.contains("😰") -> "fear"
            else -> null
        }

        return sentiment to emotion
    }

    private fun extractKeyPhrases(message: String): List<String> {
        // Simple key phrase extraction based on common patterns
        val phrases = mutableListOf<String>()
        
        // Look for phrases between punctuation marks
        val sentenceRegex = Regex("[.!?]+")
        message.split(sentenceRegex).forEach { sentence ->
            if (sentence.length > 10 && sentence.split(" ").size > 3) {
                phrases.add(sentence.trim())
            }
        }

        return phrases.take(3)
    }

    private fun checkConcernFlags(message: String): String? {
        val concernKeywords = mapOf(
            "health concerns" to listOf("sick", "hospital", "pain", "doctor"),
            "emotional distress" to listOf("depressed", "anxiety", "worried", "stress"),
            "relationship issues" to listOf("breakup", "fight", "argument", "ignore"),
            "work problems" to listOf("fired", "quit", "overtime", "stressed")
        )

        concernKeywords.forEach { (concern, keywords) ->
            if (keywords.any { message.contains(it, ignoreCase = true) }) {
                return concern
            }
        }

        return null
    }

    private fun calculateMessageLengthTrend(lengths: List<Int>): String {
        if (lengths.size < 2) return "stable"
        
        val firstHalf = lengths.take(lengths.size / 2).average()
        val secondHalf = lengths.takeLast(lengths.size / 2).average()
        
        return when {
            secondHalf > firstHalf * 1.2 -> "increasing"
            secondHalf < firstHalf * 0.8 -> "decreasing"
            else -> "stable"
        }
    }

    private fun calculateResponseConsistency(totalTime: Long, count: Int): Float {
        if (count < 2) return 1f
        
        val averageTime = totalTime / count
        val variance = 1f - (averageTime.toFloat() / (24 * 60 * 60 * 1000)) // Normalize to 24 hours
        
        return variance.coerceIn(0f, 1f)
    }

    private fun isNewConversation(currentTime: Long, previousTime: Long): Boolean {
        return currentTime - previousTime > 6 * 60 * 60 * 1000 // 6 hours gap
    }

    private fun calculateRelationshipScore(
        stats: InteractionStats,
        trends: CommunicationTrends,
        sentiment: SentimentAnalysis
    ): Float {
        var score = 0f
        
        // Interaction frequency score (30%)
        val frequencyScore = minOf(stats.totalMessages / 100f, 1f) * 0.3f
        
        // Response pattern score (20%)
        val responseScore = trends.responsePattern.responseConsistency * 0.2f
        
        // Sentiment score (30%)
        val sentimentScore = ((sentiment.overallSentiment + 1) / 2) * 0.3f
        
        // Communication balance score (20%)
        val balanceScore = (1 - kotlin.math.abs(0.5f - trends.responsePattern.initiationRatio)) * 0.2f
        
        score = frequencyScore + responseScore + sentimentScore + balanceScore
        return score.coerceIn(0f, 1f)
    }
} 