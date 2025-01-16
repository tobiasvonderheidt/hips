package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography encoding and decoding.
 */
object Steganography {
    /**
     * Function to encode secret message into cover text using given context.
     */
    suspend fun encode(
        context: String,
        secretMessage: String,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        // Step 0: Prepare secret message by appending ASCII NUL character
        val preparedSecretMessage = prepare(secretMessage)

        // Step 1: Convert secret message to a (compressed) binary representation
        val plainBits = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.decode("", preparedSecretMessage) }   // Stegasuras: Arithmetic binary conversion is just decoding with empty context
            ConversionMode.UTF8 -> { UTF8.encode(preparedSecretMessage) }
        }

        // Step 2: Encrypt binary representation of secret message
        val cipherBits = Crypto.encrypt(plainBits)

        // Step 3: Encode encrypted binary representation of secret message into cover text
        val coverText = when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.encode(context, cipherBits) }
            SteganographyMode.Bins -> { Bins.encode(context, cipherBits) }
            SteganographyMode.Huffman -> { Huffman.encode(context, cipherBits) }
        }

        return coverText
    }

    /**
     * Function to decode secret message from cover text using given context.
     */
    suspend fun decode(
        context: String,
        coverText: String,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        // Invert step 3
        val cipherBits = when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.decode(context, coverText) }
            SteganographyMode.Bins -> { Bins.decode(context, coverText) }
            SteganographyMode.Huffman -> { Huffman.decode(context, coverText) }
        }

        // Invert step 2
        val plainBits = Crypto.decrypt(cipherBits)

        // Invert step 1
        val preparedSecretMessage = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.encode("", plainBits) }   // Stegasuras: Arithmetic string conversion is just encoding with empty context
            ConversionMode.UTF8 -> { UTF8.decode(plainBits) }
        }

        // Invert step 0
        val secretMessage = unprepare(preparedSecretMessage)

        return secretMessage
    }

    /**
     * Function to prepare a secret message for binary encoding.
     *
     * Appends the ASCII NUL character to the original secret message. Needed to remove artefacts from greedy sampling after binary decoding.
     *
     * @param secretMessage A secret message.
     * @return The prepared secret message.
     */
    private fun prepare(secretMessage: String): String {
        // Kotlin doesn't use NUL-terminated strings, instead makes them immutable and stores length
        // So using NUL as a character in a Kotlin string can't cause any collisions
        val preparedSecretMessage = secretMessage + '\u0000'

        return preparedSecretMessage
    }

    /**
     * Function to unprepare a secret message after binary decoding.
     *
     * Strips the ASCII NUL character and everything after it. Therefore removes any artefacts from greedy sampling, rendering the original secret message.
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The secret message.
     */
    private fun unprepare(preparedSecretMessage: String): String {
        val secretMessage = preparedSecretMessage
            .split('\u0000')
            .first()

        return secretMessage
    }
}