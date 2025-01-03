package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay

/**
 * Object (i.e. singleton class) that represents the binary conversion of the secret message using UTF-8 encoding.
 *
 * Renamed from `Unicode` in Stegasuras as UTF-8 is only one of many possible Unicode encodings.
 */
object UTF8 {
    /**
     * Function to convert a string into its binary representation using UTF-8 encoding.
     *
     * @param secretMessage Secret message.
     * @return Binary representation of the secret message.
     */
    suspend fun encode(secretMessage: String): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val plainBits = ByteArray(size = 0)

        return plainBits
    }

    /**
     * Function to convert the binary representation of a string back to the string using UTF-8 decoding.
     *
     * @param plainBits Binary representation of the secret message.
     * @return Secret message.
     */
    suspend fun decode(plainBits: ByteArray): String {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val coverText = ""

        return coverText
    }
}