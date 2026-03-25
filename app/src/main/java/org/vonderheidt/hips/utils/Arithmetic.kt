package org.vonderheidt.hips.utils

import bitmage.BitString
import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using arithmetic encoding.
 */
object Arithmetic {
    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using arithmetic encoding.
     *
     * Corresponds to Stegasuras method `encode_arithmetic` in `arithmetic.py`. Parameter `finish_sent` was removed (i.e. is now hard coded to true for encoding, false for decompression).
     *
     * @param context The context to encode the secret message with.
     * @param cipherBits The encrypted binary representation of the secret message.
     * @param isResumed Boolean that is true if this call of the `encode` function resumes where the last call terminated, false otherwise.
     * @return A cover text containing the secret message.
     */
    fun encode(context: String, cipherBits: BitString, isResumed: Boolean): String {
        val bits = cipherBits.toBitFragment()
        val coverTextBytes = encode(
            context = context.encodeToByteArray(),
            cipherBits = bits.bytes,
            bitLength = bits.bitLength,
            isResumed = isResumed
        )

        return coverTextBytes.decodeToString()
    }

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using arithmetic decoding.
     *
     * Corresponds to Stegasuras method `decode_arithmetic` in `arithmetic.py`.
     *
     * @param context The context to decode the cover text with.
     * @param coverText The cover text containing a secret message.
     * @param numberOfCipherBits Desired number of cipher bits to return. Only needed when searching for start signal in split cover text. Has to be multiple of 8 for decryption.
     * @param isResumed Boolean that is true if this call of the `decode` function resumes where the last call terminated, false otherwise.
     * @return The encrypted binary representation of the secret message.
     * @throws IllegalArgumentException If `numberOfCipherBits` is not a multiple of 8.
     * @throws IllegalArgumentException If a cover text token could not be predicted (e.g. partial decoding with wrong context when trying to find start signal in split cover text).
     */
    fun decode(context: String, coverText: String, numberOfCipherBits: Int = -1, isResumed: Boolean = false): BitString {
        if (numberOfCipherBits > 0 && numberOfCipherBits % 8 != 0) {
            throw IllegalArgumentException("numberOfCipherBits has to be multiple of 8, but is $numberOfCipherBits")
        }

        val bytes = decode(
            context = context.toByteArray(charset = Charsets.UTF_8),
            coverText = coverText.toByteArray(charset = Charsets.UTF_8),
            numberOfCipherBits = numberOfCipherBits,
            isResumed = isResumed
        )

        return BitString(bytes, bytes.size * 8)
    }

    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using arithmetic encoding.
     *
     * Helper for the public `encode` function to bypass JNI errors with strings.
     *
     * Corresponds to Stegasuras method `encode_arithmetic` in `arithmetic.py`. Parameter `finish_sent` was removed (i.e. is now hard coded to true for encoding, false for decompression).
     *
     * @param context The context to encode the secret message with (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param cipherBits The encrypted binary representation of the secret message.
     * @param bitLength Number of bits in the cipherBits byte array to encode.
     * @param temperature The temperature parameter for token sampling. Determined by Settings object.
     * @param topK Number of most likely tokens to consider. Must be less than or equal to the vocabulary size `n_vocab` of the LLM. Determined by Settings object.
     * @param precision Number of bits to encode the top k tokens with. Determined by Settings object.
     * @param ctx Memory address of the context.
     * @param isResumed Boolean that is true if this call of the `encode` function resumes where the last call terminated, false otherwise.
     * @return A cover text containing the secret message (byte array storing UTF-8 encoded string to bypass JNI errors).
     */
    external fun encode(context: ByteArray, cipherBits: ByteArray, bitLength: Int, temperature: Float = Settings.temperature, topK: Int = Settings.topK, precision: Int = Settings.precision, ctx: Long = LlamaCpp.getCtx(), isResumed: Boolean = false) : ByteArray

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using arithmetic decoding.
     *
     * Helper for the public `decode` function to bypass JNI errors with strings.
     *
     * Corresponds to Stegasuras method `decode_arithmetic` in `arithmetic.py`.
     *
     * @param context The context to decode the cover text with (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param coverText The cover text containing a secret message (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param temperature The temperature parameter for token sampling. Determined by Settings object.
     * @param topK Number of most likely tokens to consider. Must be less than or equal to the vocabulary size `n_vocab` of the LLM. Determined by Settings object.
     * @param precision Number of bits to encode the top k tokens with. Determined by Settings object.
     * @param ctx Memory address of the context.
     * @param numberOfCipherBits Desired number of cipher bits to return. Only needed when searching for start signal in split cover text. Has to be multiple of 8 for decryption.
     * @param isResumed Boolean that is true if this call of the `decode` function resumes where the last call terminated, false otherwise.
     * @return The encrypted binary representation of the secret message.
     * @throws IllegalArgumentException If a cover text token could not be predicted (e.g. partial decoding with wrong context when trying to find start signal in split cover text).
     */
    external fun decode(context: ByteArray, coverText: ByteArray, temperature: Float = Settings.temperature, topK: Int = Settings.topK, precision: Int = Settings.precision, ctx: Long = LlamaCpp.getCtx(), numberOfCipherBits: Int = -1, isResumed: Boolean = false) : ByteArray
}