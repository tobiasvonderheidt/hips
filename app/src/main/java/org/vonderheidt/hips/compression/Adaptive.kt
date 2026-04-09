package org.vonderheidt.hips.compression

import android.util.Log
import org.vonderheidt.hips.bitmage.BitString

private const val TAG = "Adaptive.kt"
private const val TEST_ARITHMETIC_DECOMPRESSION = false

/**
 * Object (i.e. singleton class) that represents adaptive compression.
 */
object Adaptive : CompressionProvider {
    /**
     * Function to compress a secret message from string to binary using adaptive compression.
     * Tries both arithmetic and BitCrush compression, chooses whichever is more efficient and prepends a corresponding `selector` bit for decompression.
     *
     * Prefers BitCrush in two cases:
     * - Arithmetic compresses to same number of bits (for faster decompression)
     * - Arithmetic would fail during decompression (due to inconsistent token predictions by the LLM)
     *
     * @param secretMessage The secret message to compress.
     * @return The binary representation of the secret message, prepended with a `selector` bit for the compression mode that was used.
     */
    override fun compress(secretMessage: String): BitString {
        val bitCrushed = BitCrush.compress(secretMessage)
        val arithmetic = ArithmeticCompression.compress(secretMessage)

        val arithmeticSize = arithmetic.bitLength()
        val bitcrushSize = bitCrushed.bitLength()

        val arithmeticDecodable = if (TEST_ARITHMETIC_DECOMPRESSION) {
            try {
                val decompressed = ArithmeticCompression.decompress(arithmetic)
                decompressed.contentEquals(secretMessage)
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

        val bitCrushLossless = secretMessage.lowercase().contentEquals(BitCrush.decompress(bitCrushed.clone()))

        // all things being equal, prefer BitCrush for faster decoding
        if ((bitcrushSize >= arithmeticSize || !bitCrushLossless) && arithmeticDecodable) {
            Log.d(TAG, "Chose arithmetic ($arithmeticSize vs $bitcrushSize bits)")

            arithmetic.prepend(BitString.BitFragment(byteArrayOf(0x00), 1)) // prepend 0 bit

            return arithmetic
        }
        else {
            Log.d(TAG, "Chose BitCrush (${bitcrushSize} vs $arithmeticSize bits), arithmetic decodable: $arithmeticDecodable)")

            bitCrushed.prepend(BitString.BitFragment(byteArrayOf(0x80.toByte()), 1)) // prepend 1 bit

            return bitCrushed
        }
    }

    /**
     * Function to decompress a secret message from binary to string using adaptive decompression.
     * Identifies the decompression mode to use based on the `selector` bit prepended to the secret message.
     *
     * @param plainBits The binary representation of the secret message, prepended with a `selector` bit for the compression mode that was used.
     * @return The decompressed secret message.
     */
    override fun decompress(plainBits: BitString): String {
        val selector = plainBits.takeFew(1).toUByte().toInt() shr 7

        Log.d(TAG, "selector $selector")

        return when (selector) {
            0 -> ArithmeticCompression.decompress(plainBits)
            1 -> BitCrush.decompress(plainBits)
            else -> throw Exception("unreachable")
        }
    }
}