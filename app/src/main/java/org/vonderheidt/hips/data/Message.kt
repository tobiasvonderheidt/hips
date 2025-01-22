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
 * @param timestamp Time the message was sent at (milliseconds since Unix epoch, i.e. 1970-01-01 00:00).
 * @param content Content of the message.
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
    val timestamp: Long,
    val content: String
) {
    companion object {
        /**
         * Some sample messages for the conversation screen.
         */
        val Samples = listOf(
            Message(1, 0, System.currentTimeMillis() - 5982341, "Oi, Lionel! What a match today! I honestly thought we were done for at halftime being 2-0 down, but that second half was brilliant! We really pulled it together, didn’t we?"),
            Message(0, 1, System.currentTimeMillis() - 4553793, "Absolutely, Cristiano! That comeback was mental! Your goal really got everyone buzzing. And that assist I had? Proper chuffed to finally chip in like that! But can we talk about the ref? What a wanker!"),
            Message(1, 0, System.currentTimeMillis() - 3163455, "Right? I mean, some of those calls were ridiculous! I thought he was going to cost us the game. Thank goodness we managed to turn it around despite him!"),
            Message(0, 1, System.currentTimeMillis() - 2398574, "No doubt about it! I reckon we’re finally starting to gel as a team, but we need to work on our communication a bit. I lost track of you a couple of times out there!"),
            Message(1, 0, System.currentTimeMillis() - 1637465, "Totally agree! Let’s sort out a practice this week to work on that. And afterwards, we should grab a bite and find a pub that shows the highlights! What do you reckon?")
        )
    }
}