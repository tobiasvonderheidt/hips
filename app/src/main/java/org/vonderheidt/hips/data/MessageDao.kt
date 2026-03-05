package org.vonderheidt.hips.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

/**
 * Interface to implement the data access object (DAO) for the Message data class.
 */
@Dao
interface MessageDao {
    // Covers all CRUD operations

    /**
     * Function to upsert (update or insert) a message into the database.
     *
     * @param message A message.
     */
    @Upsert
    suspend fun upsertMessage(message: Message)

    /**
     * Function to get all messages between two users from the database.
     *
     * @param userID1 A user ID.
     * @param userID2 Another user ID.
     * @return List of all messages between the two users.
     */
    @Query("SELECT * FROM message WHERE (sender_id = :userID1 AND receiver_id = :userID2) OR (sender_id = :userID2 AND receiver_id = :userID1) ORDER BY timestamp;")
    suspend fun getConversation(userID1: Int, userID2: Int): List<Message>

    /**
     * Function to delete a message from the database.
     *
     * @param message The message.
     */
    @Delete
    suspend fun deleteMessage(message: Message)
}