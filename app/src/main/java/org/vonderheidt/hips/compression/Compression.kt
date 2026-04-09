package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Singleton that provides convenience wrappers for text compression
 */
object Compression {
    fun compress(secretMessage: String, compressionMode: CompressionMode): BitString {
        val compressedBits = when (compressionMode) {
            CompressionMode.Adaptive -> Adaptive.compress(secretMessage)
            CompressionMode.Arithmetic -> ArithmeticCompression.compress(secretMessage)
            CompressionMode.BitCrush -> BitCrush.compress(secretMessage)
            CompressionMode.UTF8 -> UTF8.compress(secretMessage)
        }

        return compressedBits
    }

    fun decompress(bits: BitString, compressionMode: CompressionMode): String {
        val uncompressed = when (compressionMode) {
            CompressionMode.Adaptive -> Adaptive.decompress(bits)
            CompressionMode.Arithmetic -> ArithmeticCompression.decompress(bits)
            CompressionMode.BitCrush -> BitCrush.decompress(bits)
            CompressionMode.UTF8 -> UTF8.decompress(bits)
        }

        return uncompressed
    }
}