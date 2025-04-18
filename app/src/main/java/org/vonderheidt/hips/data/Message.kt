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
) {
    companion object {
        /**
         * Some sample messages for the conversation screen.
         */
        val Samples = listOf(
            Message(User.Bob.id, User.Alice.id, "Oi, Lionel! What a match today! I honestly thought we were done for at halftime being 2-0 down, but that second half was brilliant! We really pulled it together, didn’t we?", null, System.currentTimeMillis() - 5982341),
            Message(User.Alice.id, User.Bob.id, "Absolutely, Cristiano! That comeback was mental! Your goal really got everyone buzzing. And that assist I had? Proper chuffed to finally chip in like that! But can we talk about the ref? What a wanker!", null, System.currentTimeMillis() - 4553793),
            Message(User.Bob.id, User.Alice.id, "Right? I mean, some of those calls were ridiculous! I thought he was going to cost us the game. Thank goodness we managed to turn it around despite him!", null, System.currentTimeMillis() - 3163455),
            Message(User.Alice.id, User.Bob.id, "No doubt about it! I reckon we’re finally starting to gel as a team, but we need to work on our communication a bit. I lost track of you a couple of times out there!", null, System.currentTimeMillis() - 2398574),
            Message(User.Bob.id, User.Alice.id, "Totally agree! Let’s sort out a practice this week to work on that. And afterwards, we should grab a bite and find a pub that shows the highlights! What do you reckon?", null, System.currentTimeMillis() - 1637465)
        )
    }
}