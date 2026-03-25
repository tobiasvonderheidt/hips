package org.vonderheidt.hips.compression

import bitmage.BitString

/**
 * Singleton that provides convenience wrappers for text compression
 */
object Compression {
    fun compress(message: String, mode: CompressionMode): BitString {
        val compressedBits = when (mode) {
            CompressionMode.Arithmetic -> { ArithmeticCompression.compress(message) }
            CompressionMode.UTF8 -> { UTF8.compress(message) }
            CompressionMode.BitCrush -> { BitCrush.compress(message) }
            else -> throw Exception("unsupported compression mode: $mode")
        }

        return compressedBits
    }

    fun inflate(bits: BitString, mode: CompressionMode, isResumed: Boolean): String {
        val uncompressed = when(mode) {
            CompressionMode.Arithmetic -> { ArithmeticCompression.inflate(bits, isResumed) }
            CompressionMode.UTF8 -> { UTF8.inflate(bits, isResumed) }
            CompressionMode.BitCrush -> { BitCrush.inflate(bits, isResumed) }
            else -> throw Exception("unsupported compression mode: $mode")
        }

        return uncompressed
    }
}