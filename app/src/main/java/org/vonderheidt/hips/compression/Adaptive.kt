package org.vonderheidt.hips.compression

import android.util.Log
import org.vonderheidt.hips.bitmage.BitString

object Adaptive: CompressionProvider {
    private const val TEST_ARITHMETIC_DECOMPRESSION = false

    override fun compress(secretMessage: String): BitString {
        val bitCrushed = BitCrush.compress(secretMessage)
        val arithmetic = ArithmeticCompression.compress(secretMessage)

        val arithmeticSize = arithmetic.bitLength()
        val bitcrushSize = bitCrushed.bitLength()

        val arithmeticDecodable = if(TEST_ARITHMETIC_DECOMPRESSION) {
            try {
                val decompressed = ArithmeticCompression.inflate(arithmetic)
                decompressed.contentEquals(secretMessage)
            } catch(e: Exception) {
                false
            }
        } else {
            true
        }

        val bitCrushLossless = secretMessage.lowercase().contentEquals(BitCrush.inflate(bitCrushed.clone()))

        // all things being equal, prefer BitCrush for faster decoding
        if((bitcrushSize >= arithmeticSize || !bitCrushLossless) && arithmeticDecodable) {
            Log.d("AdaptiveComp", "chose arithmetic ($arithmeticSize vs $bitcrushSize)")
            arithmetic.prepend(BitString.BitFragment(byteArrayOf(0x00), 1)) // prepend 0 bit
            return arithmetic
        }
        else {
            Log.d("AdaptiveComp", "chose BitCrush (${bitcrushSize} vs $arithmeticSize arithmetic decodable: $arithmeticDecodable)")
            bitCrushed.prepend(BitString.BitFragment(byteArrayOf(0x80.toByte()), 1)) // prepend 1 bit
            return bitCrushed
        }
    }

    override fun inflate(plainBits: BitString): String {
        val selector = plainBits.takeFew(1).toUByte().toInt() shr 7
        Log.d("AdaptiveComp", "selector $selector")
        return when(selector) {
            0 -> ArithmeticCompression.inflate(plainBits)
            1 -> BitCrush.inflate(plainBits)
            else -> throw Exception("unreachable")
        }
    }
}