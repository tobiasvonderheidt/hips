package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay
import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using Huffman encoding.
 */
object Huffman {
    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using Huffman encoding.
     *
     * Corresponds to Stegasuras method `encode_huffman` in `huffman_baseline.py`.
     */
    suspend fun encode(context: String, cipherBits: ByteArray, bitsPerToken: Int = Settings.bitsPerToken): String {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val coverText = ""

        return coverText
    }

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using Huffman decoding.
     *
     * Corresponds to Stegasuras method `decode_huffman` in `huffman_baseline.py`.
     */
    suspend fun decode(context: String, coverText: String, bitsPerToken: Int = Settings.bitsPerToken): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val cipherBits = ByteArray(size = 0)

        return cipherBits
    }
}