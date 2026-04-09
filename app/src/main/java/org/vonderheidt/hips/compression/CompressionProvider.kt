package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

interface CompressionProvider {
    /**
     * Compresses plaintext to minimum number of bits before steganography
     *
     * @param message A plaintext message to compress
     * @return The binary representation of the prepared secret message.
     */
    fun compress(message: String): BitString

    /**
     * Inflates compressed plaintext bits to readable message
     *
     * @param plainBits Binary representation of compressed message.
     * @return Uncompressed plaintext message.
     */
    fun inflate(plainBits: BitString): String
}