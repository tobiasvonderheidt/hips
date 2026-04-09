package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Singleton that provides convenience wrappers for text compression
 */
object Compression {
    fun compress(message: String, mode: CompressionMode): BitString {
        val compressedBits = when (mode) {
            CompressionMode.Adaptive -> Adaptive.compress(message)
            CompressionMode.Arithmetic -> ArithmeticCompression.compress(message)
            CompressionMode.UTF8 -> UTF8.compress(message)
            CompressionMode.BitCrush -> BitCrush.compress(message)
            else -> throw Exception("unsupported compression mode: $mode")
        }

        return compressedBits
    }

    fun inflate(bits: BitString, mode: CompressionMode): String {
        val uncompressed = when(mode) {
            CompressionMode.Adaptive -> Adaptive.inflate(bits)
            CompressionMode.Arithmetic -> ArithmeticCompression.inflate(bits)
            CompressionMode.UTF8 -> UTF8.inflate(bits)
            CompressionMode.BitCrush -> BitCrush.inflate(bits)
            else -> throw Exception("unsupported compression mode: $mode")
        }

        return uncompressed
    }
}