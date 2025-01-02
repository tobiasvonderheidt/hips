package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay
import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using arithmetic encoding.
 */
object Arithmetic {
    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using arithmetic encoding.
     *
     * Corresponds to Stegasuras method `encode_arithmetic` in `arithmetic.py`.
     */
    suspend fun encode(context: String, cipherBits: ByteArray, temperature: Float = Settings.temperature): String {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val coverText = ""

        return coverText
    }

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using arithmetic decoding.
     *
     * Corresponds to Stegasuras method `decode_arithmetic` in `arithmetic.py`.
     */
    suspend fun decode(context: String, coverText: String, temperature: Float = Settings.temperature): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val cipherBits = ByteArray(size = 0)

        return cipherBits
    }
}