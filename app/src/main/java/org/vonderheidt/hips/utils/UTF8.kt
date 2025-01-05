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
     * @param secretMessage Secret message.
     * @return Binary representation of the secret message.
     */
    fun encode(secretMessage: String): ByteArray {
        val plainBits = secretMessage.toByteArray(charset = Charsets.UTF_8)

        return plainBits
    }

    /**
     * Function to convert the binary representation of a string back to the string using UTF-8 decoding.
     *
     * @param plainBits Binary representation of the secret message.
     * @return Secret message.
     */
    fun decode(plainBits: ByteArray): String {
        val secretMessage = String(bytes = plainBits, charset = Charsets.UTF_8)

        return secretMessage
    }
}