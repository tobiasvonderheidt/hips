package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Singleton that provides convenience wrappers for text compression
 */
object Compression {
    fun compress(secretMessage: String, mode: CompressionMode): BitString {
        val compressedBits = when (mode) {
            CompressionMode.Adaptive -> Adaptive.compress(secretMessage)
            CompressionMode.Arithmetic -> ArithmeticCompression.compress(secretMessage)
            CompressionMode.UTF8 -> UTF8.compress(secretMessage)
            CompressionMode.BitCrush -> BitCrush.compress(secretMessage)
            else -> throw Exception("unsupported compression mode: $mode")
        }

        return compressedBits
    }

    fun decompress(bits: BitString, mode: CompressionMode): String {
        val uncompressed = when(mode) {
            CompressionMode.Adaptive -> Adaptive.decompress(bits)
            CompressionMode.Arithmetic -> ArithmeticCompression.decompress(bits)
            CompressionMode.UTF8 -> UTF8.decompress(bits)
            CompressionMode.BitCrush -> BitCrush.decompress(bits)
            else -> throw Exception("unsupported compression mode: $mode")
        }

        return uncompressed
    }
}