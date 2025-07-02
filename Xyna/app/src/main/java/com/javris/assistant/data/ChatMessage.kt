package com.javris.assistant.data

enum class MessageType {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String,
    val content: String,
    val timestamp: Long,
    val type: MessageType
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "content" to content,
            "timestamp" to timestamp,
            "type" to type.name
        )
    }
} 