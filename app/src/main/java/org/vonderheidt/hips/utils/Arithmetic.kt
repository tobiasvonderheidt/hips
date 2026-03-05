package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using arithmetic encoding.
 */
object Arithmetic {
    /**
     * Function to compress the secret message using arithmetic *decoding*. 
     */
    fun compress(preparedSecretMessage: String): ByteArray {
        return decode(
            context = "",
            coverText = preparedSecretMessage,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40,
            seed = 0 // No salting for compression
        )
    }

    /**
     * Function to decompress the secret message using arithmetic *encoding*.
     */
    fun decompress(paddedPlainBits: ByteArray): String {
        return encode(
            context = "",
            cipherBits = paddedPlainBits,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40,
            seed = 0 // No salting for decompression
        )
    }

    /**
     * Function to encode the secret message into a cover text.
     */
    external fun encode(
        context: String, 
        cipherBits: ByteArray, 
        temperature: Float = Settings.temperature, 
        topK: Int = Settings.topK, 
        precision: Int = Settings.precision, 
        seed: Int = 0, 
        ctx: Long = LlamaCpp.getCtx()
    ) : String

    /**
     * Function to decode a cover text into the secret message.
     */
    external fun decode(
        context: String, 
        coverText: String, 
        temperature: Float = Settings.temperature, 
        topK: Int = Settings.topK, 
        precision: Int = Settings.precision, 
        seed: Int = 0, 
        ctx: Long = LlamaCpp.getCtx()
    ) : ByteArray
}
