package org.vonderheidt.hips.utils

/**
 * Object (i.e. singleton class) that represents encryption and decryption.
 */
object Crypto {
    /**
     * Function to encrypt plain bits into cipher bits.
     */
    fun encrypt(plainBits: ByteArray): ByteArray {
        // Skip encryption for now
        val cipherBits = plainBits

        return cipherBits
    }

    /**
     * Function to decrypt cipher bits into plain bits.
     */
    fun decrypt(cipherBits: ByteArray): ByteArray {
        // Skip decryption for now
        val plainBits = cipherBits

        return plainBits
    }
}