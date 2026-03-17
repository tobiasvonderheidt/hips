package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using arithmetic encoding.
 *
 * Core arithmetic coding logic is delegated to native (C/C++) implementations via JNI
 * for performance. The Kotlin layer handles parameter resolution, string/byte conversions,
 * and scoring bookkeeping.
 */
object Arithmetic {
    /** Score from the last encode, computed inline so we don't need a second LLM pass. */
    var lastScore: SteganographyScoring.NaturalnessScore? = null
        private set

    /**
     * Function to compress the secret message using arithmetic *decoding*. Wrapper for function [decodeNative].
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The compressed, 0-padded binary representation of the prepared secret message.
     */
    fun compress(preparedSecretMessage: String): ByteArray {
        return decodeNative(
            context = "".toByteArray(Charsets.UTF_8),
            coverText = preparedSecretMessage.toByteArray(Charsets.UTF_8),
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40,
            seed = 0,
            ctx = LlamaCpp.getCtx()
        )
    }

    /**
     * Function to decompress the secret message using arithmetic *encoding*. Wrapper for function [encodeNative].
     *
     * @param paddedPlainBits The compressed, 0-padded binary representation of a prepared secret message.
     * @return The prepared secret message.
     */
    fun decompress(paddedPlainBits: ByteArray): String {
        val resultBytes = encodeNative(
            context = "".toByteArray(Charsets.UTF_8),
            cipherBits = paddedPlainBits,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40,
            seed = 0,
            ctx = LlamaCpp.getCtx()
        )
        return String(resultBytes, Charsets.UTF_8)
    }

    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using arithmetic encoding.
     *
     * Corresponds to Stegasuras method `encode_arithmetic` in `arithmetic.py`. Parameter `finish_sent` was removed (<=> is now hard coded to true).
     *
     * @param context The context to encode the secret message with.
     * @param cipherBits The encrypted binary representation of the secret message.
     * @param temperature The temperature parameter for token sampling. Determined by Settings object.
     * @param topK Number of most likely tokens to consider. Must be less than or equal to the vocabulary size `n_vocab` of the LLM. Determined by Settings object.
     * @param precision Number of bits to encode the top k tokens with. Determined by Settings object.
     * @return A cover text containing the secret message.
     */
    fun encode(
        context: String,
        cipherBits: ByteArray,
        temperature: Float = Settings.temperature,
        topK: Int = Settings.topK,
        precision: Int = Settings.precision
    ): String {
        val resultBytes = encodeNative(
            context = context.toByteArray(Charsets.UTF_8),
            cipherBits = cipherBits,
            temperature = temperature,
            topK = topK,
            precision = precision,
            seed = 0,
            ctx = LlamaCpp.getCtx()
        )
        val coverText = String(resultBytes, Charsets.UTF_8)

        // Scoring disabled — getLogits is unreliable after encoding exhausts the LLM context
        lastScore = null

        return coverText
    }

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using arithmetic decoding.
     *
     * Corresponds to Stegasuras method `decode_arithmetic` in `arithmetic.py`.
     *
     * @param context The context to decode the cover text with.
     * @param coverText The cover text containing a secret message.
     * @param temperature The temperature parameter for token sampling. Determined by Settings object.
     * @param topK Number of most likely tokens to consider. Must be less than or equal to the vocabulary size `n_vocab` of the LLM. Determined by Settings object.
     * @param precision Number of bits to encode the top k tokens with. Determined by Settings object.
     * @return The encrypted binary representation of the secret message.
     */
    fun decode(
        context: String,
        coverText: String,
        temperature: Float = Settings.temperature,
        topK: Int = Settings.topK,
        precision: Int = Settings.precision
    ): ByteArray {
        return decodeNative(
            context = context.toByteArray(Charsets.UTF_8),
            coverText = coverText.toByteArray(Charsets.UTF_8),
            temperature = temperature,
            topK = topK,
            precision = precision,
            seed = 0,
            ctx = LlamaCpp.getCtx()
        )
    }

    // ---- JNI native methods ----

    private external fun encodeNative(
        context: ByteArray,
        cipherBits: ByteArray,
        temperature: Float,
        topK: Int,
        precision: Int,
        seed: Int,
        ctx: Long
    ): ByteArray

    private external fun decodeNative(
        context: ByteArray,
        coverText: ByteArray,
        temperature: Float,
        topK: Int,
        precision: Int,
        seed: Int,
        ctx: Long
    ): ByteArray
}