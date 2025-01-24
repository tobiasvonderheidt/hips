package org.vonderheidt.hips.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Class that represents the Room database.
 */
@Database(
    entities = [User::class, Message::class],
    version = 1
)
abstract class HiPSDatabase : RoomDatabase() {
    // Data access objects to access tables
    abstract val userDao: UserDao
    abstract val messageDao: MessageDao

    // Companion object to ensure database is a singleton as abstract class can't be instantiated
    companion object {
        // Annotate reference to the database instance as volatile so that r/w to it is atomic and immediately visible to all threads
        // Avoids race conditions, i.e. multiple threads trying to r/w to the database simultaneously
        @Volatile
        private var dbInstance: HiPSDatabase? = null

        /**
         * Function to check if the database is running.
         *
         * @return Boolean that is true if the database is running, false otherwise.
         */
        private fun isRunning(): Boolean {
            return dbInstance != null
        }

        /**
         * Function to start the database in a thread-safe manner.
         *
         * @param context The application context.
         */
        fun startInstance(context: Context) {
            // If the database is already running, there is nothing to do
            if (isRunning()) {
                return
            }

            // Otherwise, start the database
            // Synchronized allows only one thread to execute the code inside {...}, so other threads can't start the database simultaneously
            synchronized(lock = this) {
                if (!isRunning()) {
                    // Database builder specifically requires the application context
                    dbInstance = Room.databaseBuilder(context.applicationContext, HiPSDatabase::class.java, "hips.db").build()
                }
            }
        }

        /**
         * Function to get the currently running database instance.
         *
         * @return The database instance.
         */
        fun getInstance(): HiPSDatabase {
            // Database can be assumed to be running as startInstance is called on app startup in MainActivity
            return dbInstance!!
        }
    }
}