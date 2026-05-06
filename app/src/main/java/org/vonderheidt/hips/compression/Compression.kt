package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Object (i.e. singleton class) that provides convenience wrappers for compression of the secret message from string to binary and vice versa.
 */
object Compression {
    /**
     * Function to compress a secret message from string to binary.
     *
     * @param secretMessage The secret message to compress.
     * @param compressionMode The compression mode to use.
     * @return The binary representation of the secret message.
     */
    fun compress(secretMessage: String, compressionMode: CompressionMode): BitString {
        val plainBits = when (compressionMode) {
            CompressionMode.Adaptive -> Adaptive.compress(secretMessage)
            CompressionMode.Arithmetic -> ArithmeticCompression.compress(secretMessage)
            CompressionMode.BitCrush -> BitCrush.compress(secretMessage)
            CompressionMode.UTF8 -> UTF8.compress(secretMessage)
        }

        return plainBits
    }

    /**
     * Function to decompress a secret message from binary to string.
     *
     * @param plainBits The binary representation of the secret message.
     * @param compressionMode The compression mode to use.
     * @return The decompressed secret message.
     */
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