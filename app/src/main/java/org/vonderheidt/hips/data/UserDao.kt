package org.vonderheidt.hips.data

import androidx.room.Dao
import androidx.room.Upsert

/**
 * Interface to implement the data access object (DAO) for the User data class.
 */
@Dao
interface UserDao {
    /**
     * Function to upsert (update or insert) a user into the database.
     *
     * @param user A user.
     */
    @Upsert
    suspend fun upsertUser(user: User)
}