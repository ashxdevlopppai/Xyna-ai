package com.javris.assistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.javris.assistant.R
import com.javris.assistant.service.ChatAnalysisService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChatAnalysisFragment : Fragment() {

    private lateinit var chatService: ChatAnalysisService
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var suggestionsAdapter: SuggestionsAdapter
    
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var suggestionsRecyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.chat_analysis_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        chatService = ChatAnalysisService(requireContext())
        
        setupViews(view)
        setupRecyclerViews()
        setupClickListeners()
        observeChat()
    }

    private fun setupViews(view: View) {
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        suggestionsRecyclerView = view.findViewById(R.id.suggestionsRecyclerView)
    }

    private fun setupRecyclerViews() {
        chatAdapter = ChatAdapter()
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
        }

        suggestionsAdapter = SuggestionsAdapter { suggestion ->
            messageInput.setText(suggestion)
        }
        suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = suggestionsAdapter
        }
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            val message = messageInput.text?.toString()
            if (!message.isNullOrBlank()) {
                sendMessage(message)
                messageInput.text?.clear()
            }
        }
    }

    private fun observeChat() {
        lifecycleScope.launch {
            chatService.analyzeChatInRealTime(getCurrentContactName()).collect { message ->
                chatAdapter.addMessage(message)
                updateSuggestions(message)
                updateAnalysisCard(message)
            }
        }
    }

    private fun sendMessage(message: String) {
        val contactName = getCurrentContactName()
        val chatMessage = ChatAnalysisService.ChatMessage(
            id = System.currentTimeMillis().toString(),
            contactName = contactName,
            message = message,
            timestamp = System.currentTimeMillis(),
            isIncoming = false,
            intent = ChatAnalysisService.MessageIntent.UNKNOWN,
            sentiment = 0f,
            suggestions = emptyList()
        )
        chatAdapter.addMessage(chatMessage)
    }

    private fun updateSuggestions(message: ChatAnalysisService.ChatMessage) {
        val suggestions = chatService.getSuggestions(message.message, message.contactName)
        suggestionsAdapter.updateSuggestions(suggestions)
    }

    private fun updateAnalysisCard(message: ChatAnalysisService.ChatMessage) {
        view?.findViewById<View>(R.id.analysisCard)?.apply {
            visibility = View.VISIBLE
            findViewById<android.widget.TextView>(R.id.intentText).text = 
                "Intent: ${message.intent}"
            findViewById<android.widget.TextView>(R.id.sentimentText).text = 
                "Sentiment: ${formatSentiment(message.sentiment)}"
            findViewById<android.widget.TextView>(R.id.topicsText).text = 
                "Contact Status: ${chatService.getChatFolder(message.contactName)?.contactStatus}"
        }
    }

    private fun formatSentiment(sentiment: Float): String {
        return when {
            sentiment > 0.5f -> "Very Positive 😊"
            sentiment > 0f -> "Positive 🙂"
            sentiment == 0f -> "Neutral 😐"
            sentiment > -0.5f -> "Negative 🙁"
            else -> "Very Negative 😢"
        }
    }

    private fun getCurrentContactName(): String {
        // TODO: Get current contact name from navigation args or activity
        return "Test Contact"
    }

    inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
        private val messages = mutableListOf<ChatAnalysisService.ChatMessage>()

        fun addMessage(message: ChatAnalysisService.ChatMessage) {
            messages.add(message)
            notifyItemInserted(messages.size - 1)
            chatRecyclerView.scrollToPosition(messages.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(messages[position])
        }

        override fun getItemCount() = messages.size

        inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(message: ChatAnalysisService.ChatMessage) {
                // TODO: Implement message binding
            }
        }
    }

    inner class SuggestionsAdapter(
        private val onSuggestionClick: (String) -> Unit
    ) : RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder>() {
        private val suggestions = mutableListOf<String>()

        fun updateSuggestions(newSuggestions: List<String>) {
            suggestions.clear()
            suggestions.addAll(newSuggestions)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_suggestion, parent, false)
            return SuggestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            holder.bind(suggestions[position])
        }

        override fun getItemCount() = suggestions.size

        inner class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            init {
                itemView.setOnClickListener {
                    onSuggestionClick(suggestions[adapterPosition])
                }
            }

            fun bind(suggestion: String) {
                // TODO: Implement suggestion binding
            }
        }
    }
} 