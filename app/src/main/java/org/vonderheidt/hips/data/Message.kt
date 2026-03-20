package org.vonderheidt.hips.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE

/**
 * Class that represents a chat message in a conversation.
 *
 * @param senderID User ID of the message's sender.
 * @param receiverID User ID of the message's receiver.
 * @param content Content of the message.
 * @param inverseHuffmanCodes Inverse Huffman codes generated during compression of the message (if applicable).
 * @param timestamp Time the message was sent at (milliseconds since Unix epoch, i.e. 1970-01-01 00:00).
 */
@Entity(
    primaryKeys = ["sender_id", "receiver_id", "timestamp"],
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["sender_id"], onDelete = CASCADE, onUpdate = CASCADE),
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["receiver_id"], onDelete = CASCADE, onUpdate = CASCADE)
    ]
)
data class Message (
    @ColumnInfo(name = "sender_id") val senderID: Int,
    @ColumnInfo(name = "receiver_id") val receiverID: Int,
    val content: String,
    @ColumnInfo(name = "inverse_huffman_codes") val inverseHuffmanCodes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)