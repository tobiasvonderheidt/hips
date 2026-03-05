package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay
import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using bins encoding.
 */
object Bins {
    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using bins encoding.
     *
     * Corresponds to Stegasuras method `encode_block` in `block_baseline.py`.
     */
    suspend fun encode(context: String, cipherBits: ByteArray, blockSize: Int = Settings.blockSize): String {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val coverText = ""

        return coverText
    }

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using bins decoding.
     *
     * Corresponds to Stegasuras method `decode_block` in `block_baseline.py`.
     */
    suspend fun decode(context: String, coverText: String, blockSize: Int = Settings.blockSize): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val cipherBits = ByteArray(size = 0)

        return cipherBits
    }
}