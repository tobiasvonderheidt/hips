package org.vonderheidt.hips.utils

/**
 * Object (i.e. singleton class) that represents the binary conversion of the secret message using UTF-8 encoding.
 *
 * Renamed from `Unicode` in Stegasuras as UTF-8 is only one of many possible Unicode encodings.
 */
object UTF8 {
    /**
     * Function to convert a string into its binary representation using UTF-8 encoding.
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The binary representation of the prepared secret message.
     */
    fun encode(preparedSecretMessage: String): ByteArray {
        val plainBits = (preparedSecretMessage).toByteArray(charset = Charsets.UTF_8)

        return plainBits
    }

    /**
     * Function to convert the binary representation of a string back to the string using UTF-8 decoding.
     *
     * @param plainBits The binary representation of a prepared secret message.
     * @return The prepared secret message.
     */
    fun decode(plainBits: ByteArray): String {
        val preparedSecretMessage = String(bytes = plainBits, charset = Charsets.UTF_8)

        return preparedSecretMessage
    }
}