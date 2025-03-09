package org.vonderheidt.hips.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Class that represents a user.
 *
 * @param id The user ID.
 * @param name A user name.
 */
@Entity
data class User(
    @PrimaryKey val id: Int,
    val name: String
) {
    companion object {
        /**
         * Sample user for the conversation screen.
         */
        val Alice = User(0, "Alice")

        /**
         * Sample user for the conversation screen.
         */
        val Bob = User(1, "Bob")
    }
}