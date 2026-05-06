package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Object (i.e. singleton class) that represents the binary conversion of the secret message using UTF-8 encoding.
 *
 * Renamed from `Unicode` in Stegasuras as UTF-8 is only one of many possible Unicode encodings.
 */
object UTF8 : CompressionProvider {
    /**
     * Function to convert a string into its binary representation using UTF-8 encoding.
     *
     * @param secretMessage A secret message.
     * @return The binary representation of the secret message.
     */
    override fun compress(secretMessage: String): BitString {
        val plainBits = secretMessage.toByteArray(Charsets.UTF_8).let { BitString(it, it.size * 8) }

        return plainBits
    }

    /**
     * Function to convert the binary representation of a string back to the string using UTF-8 decoding.
     *
     * @param plainBits The binary representation of a secret message.
     * @return The secret message.
     */
    override fun decompress(plainBits: BitString): String {
        check(plainBits.bitLength() % 8 == 0) { "UTF-8 expects byte-aligned input, got ${plainBits.bitLength()} bits"}

        val secretMessage = plainBits.toBitFragment().bytes.decodeToString()

        return secretMessage
    }
}