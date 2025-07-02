package com.javris.assistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.javris.assistant.R
import com.javris.assistant.data.ChatMessage
import com.javris.assistant.data.MessageType
import com.javris.assistant.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(message: ChatMessage) {
            binding.apply {
                messageText.text = message.content
                timestampText.text = dateFormat.format(Date(message.timestamp))

                // Configure message appearance based on type
                when (message.type) {
                    MessageType.USER -> {
                        messageCard.setCardBackgroundColor(
                            itemView.context.getColor(R.color.user_message_background)
                        )
                        messageText.setTextColor(
                            itemView.context.getColor(R.color.user_message_text)
                        )
                        avatarImage.visibility = View.GONE
                        messageCard.layoutParams = (messageCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                            marginStart = itemView.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                            marginEnd = itemView.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                        }
                    }
                    MessageType.ASSISTANT -> {
                        messageCard.setCardBackgroundColor(
                            itemView.context.getColor(R.color.assistant_message_background)
                        )
                        messageText.setTextColor(
                            itemView.context.getColor(R.color.assistant_message_text)
                        )
                        avatarImage.visibility = View.VISIBLE
                        avatarImage.setImageResource(R.drawable.ic_assistant_avatar)
                        messageCard.layoutParams = (messageCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                            marginStart = itemView.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                            marginEnd = itemView.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                        }
                    }
                }
            }
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
} 