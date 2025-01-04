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
        // Step 1: Convert secret message to a (compressed) binary representation
        val plainBits = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.decode("", secretMessage) }   // Stegasuras: Arithmetic binary conversion is just decoding with empty context
            ConversionMode.UTF8 -> { UTF8.encode(secretMessage) }
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
        val secretMessage = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.encode("", plainBits) }   // Stegasuras: Arithmetic string conversion is just encoding with empty context
            ConversionMode.UTF8 -> { UTF8.decode(plainBits) }
        }

        return secretMessage
    }
}