package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Singleton that provides convenience wrappers for text compression
 */
object Compression {
    fun compress(secretMessage: String, compressionMode: CompressionMode): BitString {
        val plainBits = when (compressionMode) {
            CompressionMode.Adaptive -> Adaptive.compress(secretMessage)
            CompressionMode.Arithmetic -> ArithmeticCompression.compress(secretMessage)
            CompressionMode.BitCrush -> BitCrush.compress(secretMessage)
            CompressionMode.UTF8 -> UTF8.compress(secretMessage)
        }

        return plainBits
    }

    fun decompress(plainBits: BitString, compressionMode: CompressionMode): String {
        val secretMessage = when (compressionMode) {
            CompressionMode.Adaptive -> Adaptive.decompress(plainBits)
            CompressionMode.Arithmetic -> ArithmeticCompression.decompress(plainBits)
            CompressionMode.BitCrush -> BitCrush.decompress(plainBits)
            CompressionMode.UTF8 -> UTF8.decompress(plainBits)
        }

        return secretMessage
    }
}