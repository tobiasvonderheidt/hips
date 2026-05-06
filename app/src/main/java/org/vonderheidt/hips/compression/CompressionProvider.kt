package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Interface that declares functions for compressing a secret message from string to binary and vice versa.
 */
interface CompressionProvider {
    /**
     * Function to compress a secret message from string to binary.
     *
     * @param secretMessage The secret message to compress.
     * @return The binary representation of the secret message.
     */
    fun compress(secretMessage: String): BitString

    /**
     * Function to decompress a secret message from binary to string.
     *
     * @param plainBits The binary representation of the secret message.
     * @return The decompressed secret message.
     */
    fun decompress(plainBits: BitString): String
}