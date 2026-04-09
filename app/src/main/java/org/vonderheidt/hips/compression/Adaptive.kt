package org.vonderheidt.hips.compression

import android.util.Log
import org.vonderheidt.hips.bitmage.BitString

private const val TAG = "Adaptive.kt"
private const val TEST_ARITHMETIC_DECOMPRESSION = false

object Adaptive : CompressionProvider {
    override fun compress(secretMessage: String): BitString {
        val bitCrushed = BitCrush.compress(secretMessage)
        val arithmetic = ArithmeticCompression.compress(secretMessage)

        val arithmeticSize = arithmetic.bitLength()
        val bitcrushSize = bitCrushed.bitLength()

        val arithmeticDecodable = if(TEST_ARITHMETIC_DECOMPRESSION) {
            try {
                val decompressed = ArithmeticCompression.decompress(arithmetic)
                decompressed.contentEquals(secretMessage)
            } catch(e: Exception) {
                false
            }
        } else {
            true
        }

        val bitCrushLossless = secretMessage.lowercase().contentEquals(BitCrush.decompress(bitCrushed.clone()))

        // all things being equal, prefer BitCrush for faster decoding
        if((bitcrushSize >= arithmeticSize || !bitCrushLossless) && arithmeticDecodable) {
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

    override fun decompress(plainBits: BitString): String {
        val selector = plainBits.takeFew(1).toUByte().toInt() shr 7

        Log.d(TAG, "selector $selector")

        return when(selector) {
            0 -> ArithmeticCompression.decompress(plainBits)
            1 -> BitCrush.decompress(plainBits)
            else -> throw Exception("unreachable")
        }
    }
}