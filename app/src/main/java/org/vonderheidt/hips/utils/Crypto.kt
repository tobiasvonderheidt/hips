package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay

/**
 * Object (i.e. singleton class) that represents encryption and decryption.
 */
object Crypto {
    /**
     * Function to encrypt plain bits into cipher bits.
     */
    suspend fun encrypt(plainBits: ByteArray): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val cipherBits = ByteArray(size = 0)

        return cipherBits
    }

    /**
     * Function to decrypt cipher bits into plain bits.
     */
    suspend fun decrypt(cipherBits: ByteArray): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val plainBits = ByteArray(size = 0)

        return plainBits
    }
}