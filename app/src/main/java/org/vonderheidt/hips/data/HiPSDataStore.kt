package org.vonderheidt.hips.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.vonderheidt.hips.utils.ConversionMode
import org.vonderheidt.hips.utils.SteganographyMode

/**
 * Object (i.e. singleton class) to represent the DataStore instance.
 */
object HiPSDataStore {
    // DataStore can be found under /data/data/org.vonderheidt.hips/files/datastore/settings.preferences_pb
    // Is a binary ProtoBuf file even though Preferences DataStore is used
    private val Context.dataStore by preferencesDataStore(name = "settings")

    // Define the data type of the values for each setting by declaring the corresponding keys
    private val conversionMode = stringPreferencesKey("conversionMode")
    private val steganographyMode = stringPreferencesKey("steganographyMode")
    private val temperature = floatPreferencesKey("temperature")
    private val blockSize = intPreferencesKey("blockSize")
    private val bitsPerToken = intPreferencesKey("bitsPerToken")

    // Annotate the variable referencing the DataStore instance as volatile so that r/w to it is atomic and immediately visible to all threads
    // Avoids race conditions, i.e. multiple threads trying to start the DataStore simultaneously
    @Volatile
    private var instance: DataStore<Preferences>? = null

    /**
     * Function to start the DataStore instance in a thread-safe manner.
     *
     * Doesn't return anything as the HiPSDataStore object stores everything internally.
     *
     * @param context Application context.
     */
    fun startInstance(context: Context) {
        // If the instance is already started, there is nothing to do
        if (instance != null) {
            return
        }

        // Otherwise, instantiate the DataStore
        // Synchronized allows only one thread to execute the code inside {...}, so other threads can't instantiate it simultaneously
        synchronized(this) {
            if (instance == null) {
                instance = context.dataStore
            }
        }
    }

    /**
     * Function to read the settings from the DataStore.
     */
    suspend fun readSettings() {
        // Instance can be asserted not null because startInstance initializes it in MainActivity (i.e. on app startup)
        instance!!.data.map { settings ->
            val conversionMode = settings[conversionMode]
            val steganographyMode = settings[steganographyMode]
            val temperature = settings[temperature]
            val blockSize = settings[blockSize]
            val bitsPerToken = settings[bitsPerToken]

            // See if any settings are currently stored by checking for null values
            val isInitialized = conversionMode != null
                    && steganographyMode != null
                    && temperature != null
                    && blockSize != null
                    && bitsPerToken != null

            // If any settings are stored, return them via .first()
            if (isInitialized) {
                // Can be asserted not null because of check with isInitialized
                Settings.conversionMode = ConversionMode.valueOf(conversionMode!!)
                Settings.steganographyMode = SteganographyMode.valueOf(steganographyMode!!)
                Settings.temperature = temperature!!
                Settings.blockSize = blockSize!!
                Settings.bitsPerToken = bitsPerToken!!
            }
            // Otherwise (i.e. upon installation of this app), store default settings and return them
            else {
                writeSettings()
            }
        }.first()
    }

    /**
     * Function to write the settings to the DataStore.
     */
    suspend fun writeSettings() {
        // Instance can be asserted not null because startInstance initializes it in MainActivity (i.e. on app startup)
        instance!!.edit { settings ->
            settings[conversionMode] = Settings.conversionMode.name
            settings[steganographyMode] = Settings.steganographyMode.name
            settings[temperature] = Settings.temperature
            settings[blockSize] = Settings.blockSize
            settings[bitsPerToken] = Settings.bitsPerToken
        }
    }
}