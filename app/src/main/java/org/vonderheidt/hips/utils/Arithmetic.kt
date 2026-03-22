package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using arithmetic encoding.
 */
object Arithmetic {
    /**
     * Function to compress the secret message using arithmetic *decoding*. Wrapper for function `decode` of object `Arithmetic`.
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The compressed, 0-padded binary representation of the prepared secret message.
     */
    fun compress(preparedSecretMessage: String): ByteArray {
        // Stegasuras:
        // Arithmetic compression is just decoding with empty context
        // Parameters temperature, topK and precision are not taken from settings, but hard-coded to use the unmodulated LLM
        // While topK is set to the vocabulary size of the LLM, precision is set as high as possible so (ideally) no tokens have probability < 1/2^precision
        return decode(
            context = "".toByteArray(charset = Charsets.UTF_8),
            coverText = preparedSecretMessage.toByteArray(charset = Charsets.UTF_8),
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40
        )
    }

    // TODO Downward concat of split cover text
    //  Parameter isResumed in all subsequent functions is to differentiate first from subsequent calls
    /**
     * Function to decompress the secret message using arithmetic *encoding*. Wrapper for function `encode` of object `Arithmetic`.
     *
     * @param paddedPlainBits The compressed, 0-padded binary representation of a prepared secret message.
     * @param isResumed Boolean that is true if this call of the `decompress` function resumes where the last call terminated, false otherwise.
     * @return The prepared secret message.
     */
    fun decompress(paddedPlainBits: ByteArray, isResumed: Boolean = false): String {
        // Stegasuras:
        // Arithmetic decompression is just encoding with empty context
        // Same parameters as compression
        val preparedSecretMessageBytes = encode(
            context = "".toByteArray(charset = Charsets.UTF_8),
            cipherBits = paddedPlainBits,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40,
            isResumed = isResumed
        )

        val preparedSecretMessage = String(bytes = preparedSecretMessageBytes, charset = Charsets.UTF_8)

        return preparedSecretMessage
    }

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
    fun encode(context: String, cipherBits: ByteArray, isResumed: Boolean = false): String {
        val coverTextBytes = encode(
            context = context.toByteArray(charset = Charsets.UTF_8),
            cipherBits = cipherBits,
            isResumed = isResumed
        )

        val coverText = String(bytes = coverTextBytes, charset = Charsets.UTF_8)

        return coverText
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
    fun decode(context: String, coverText: String, numberOfCipherBits: Int = -1, isResumed: Boolean = false): ByteArray {
        if (numberOfCipherBits > 0 && numberOfCipherBits % 8 != 0) {
            throw IllegalArgumentException("numberOfCipherBits has to be multiple of 8, but is $numberOfCipherBits")
        }

        return decode(
            context = context.toByteArray(charset = Charsets.UTF_8),
            coverText = coverText.toByteArray(charset = Charsets.UTF_8),
            numberOfCipherBits = numberOfCipherBits,
            isResumed = isResumed
        )
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
     * @param temperature The temperature parameter for token sampling. Determined by Settings object.
     * @param topK Number of most likely tokens to consider. Must be less than or equal to the vocabulary size `n_vocab` of the LLM. Determined by Settings object.
     * @param precision Number of bits to encode the top k tokens with. Determined by Settings object.
     * @param ctx Memory address of the context.
     * @param isResumed Boolean that is true if this call of the `encode` function resumes where the last call terminated, false otherwise.
     * @return A cover text containing the secret message (byte array storing UTF-8 encoded string to bypass JNI errors).
     */
    private external fun encode(context: ByteArray, cipherBits: ByteArray, temperature: Float = Settings.temperature, topK: Int = Settings.topK, precision: Int = Settings.precision, ctx: Long = LlamaCpp.getCtx(), isResumed: Boolean = false) : ByteArray

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
    private external fun decode(context: ByteArray, coverText: ByteArray, temperature: Float = Settings.temperature, topK: Int = Settings.topK, precision: Int = Settings.precision, ctx: Long = LlamaCpp.getCtx(), numberOfCipherBits: Int = -1, isResumed: Boolean = false) : ByteArray
}