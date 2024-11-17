package org.vonderheidt.hips.data

/**
 * Class that represents a chat message in a conversation.
 */
data class Message (
    val senderID: Int,
    val receiverID: Int,
    val timestamp: Long,
    val content: String
)