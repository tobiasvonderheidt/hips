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

    /**
     * Function to get the top 2^bitsPerToken logits for the last token of the prompt. Keeps track of the corresponding token IDs in a map.
     *
     * Parameter `bits_per_word` from Stegasuras was renamed to `bitsPerToken`.
     *
     * @param logits Logits for the last token of the prompt (= last row of logits matrix).
     * @param bitsPerToken Number of bits to encode/decode per cover text token (= height of Huffman tree). Determined by Settings object.
     * @return Map of top 2^bitsPerToken logits and the corresponding token IDs.
     */
    private fun getTopLogits(logits: FloatArray, bitsPerToken: Int = Settings.bitsPerToken): Map<Int, Float> {
        val topLogits = logits
            .mapIndexed{ token, logit -> token to logit }   // Convert to List<Pair<Int, Float>> so token IDs won't get lost
            .sortedByDescending { it.second }               // Sort pairs descending based on logits
            .take(1 shl bitsPerToken)                    // Take top 2^bitsPerToken pairs
            .toMap()                                        // Convert to Map<Int, Float> for Huffman tree (ensures there can't be any duplicate token IDs)

        return topLogits
    }
}