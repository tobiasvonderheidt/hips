package org.vonderheidt.hips.utils

import org.vonderheidt.hips.bitmage.BitString
import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using Huffman encoding.
 */
object Huffman {
    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using Huffman encoding.
     *
     * Corresponds to Stegasuras method `encode_huffman` in `huffman_baseline.py`. Parameter `finish_sent` was removed (<=> is now hard coded to true).
     *
     * @param context The context to encode the secret message with.
     * @param cipherBits The encrypted binary representation of the secret message.
     * @return A cover text containing the secret message.
     */
    fun encode(context: String, cipherBits: BitString): String {
        val bits = cipherBits.toBitFragment()
        val coverTextBytes = encode(
            context = context.encodeToByteArray(),
            cipherBits = bits.bytes,
            bitLength = bits.bitLength
        )

        return coverTextBytes.decodeToString()
    }

    // TODO Downward concat of split cover text
    //  Parameter isResumed in public and private decode functions is to differentiate first from subsequent calls of decode
    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using Huffman decoding.
     *
     * Corresponds to Stegasuras method `decode_huffman` in `huffman_baseline.py`.
     *
     * @param context The context to decode the cover text with.
     * @param coverText The cover text containing a secret message.
     * @param numberOfCipherBits Desired number of cipher bits to return. Only needed when searching for start signal in split cover text. Has to be multiple of 8 for decryption.
     * @param isResumed Boolean that is true if this call of the `decode` function resumes where the last call terminated, false otherwise.
     * @return The encrypted binary representation of the secret message.
     * @throws IllegalArgumentException If `numberOfCipherBits` is not a multiple of 8.
     */
    fun decode(context: String, coverText: String, numberOfCipherBits: Int = -1, isResumed: Boolean = false): BitString {
        val bytes = decode(
            context = context.toByteArray(charset = Charsets.UTF_8),
            coverText = coverText.toByteArray(charset = Charsets.UTF_8),
            numberOfCipherBits = numberOfCipherBits,
            isResumed = isResumed
        )

        val bits = BitString(bytes, bytes.size * 8)
        val paddingLen = bits.takeFew(8).toInt()
        bits.takeFew(paddingLen)

        return bits
    }

    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using Huffman encoding.
     *
     * Helper for the public `encode` function to bypass JNI errors with strings.
     *
     * Corresponds to Stegasuras method `encode_huffman` in `huffman_baseline.py`. Parameter `finish_sent` was removed (<=> is now hard coded to true).
     *
     * @param context The context to encode the secret message with (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param cipherBits The encrypted binary representation of the secret message.
     * @param bitLength Number of bits in the cipherBits byte array to encode.
     * @param bitsPerToken Number of bits to encode/decode per cover text token (= height of Huffman tree). Determined by Settings object.
     * @param ctx Memory address of the context.
     * @return A cover text containing the secret message (byte array storing UTF-8 encoded string to bypass JNI errors).
     */
    private external fun encode(context: ByteArray, cipherBits: ByteArray, bitLength: Int, bitsPerToken: Int = Settings.bitsPerToken, ctx: Long = LlamaCpp.getCtx()): ByteArray

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using Huffman decoding.
     *
     * Helper for the public `decode` function to bypass JNI errors with strings.
     *
     * Corresponds to Stegasuras method `decode_huffman` in `huffman_baseline.py`.
     *
     * @param context The context to decode the cover text with (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param coverText The cover text containing a secret message (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param bitsPerToken Number of bits to encode/decode per cover text token (= height of Huffman tree). Determined by Settings object.
     * @param ctx Memory address of the context.
     * @param numberOfCipherBits Desired number of cipher bits to return. Only needed when searching for start signal in split cover text. Has to be multiple of 8 for decryption.
     * @param isResumed Boolean that is true if this call of the `decode` function resumes where the last call terminated, false otherwise.
     * @return The encrypted binary representation of the secret message.
     */
    private external fun decode(context: ByteArray, coverText: ByteArray, bitsPerToken: Int = Settings.bitsPerToken, ctx: Long = LlamaCpp.getCtx(), numberOfCipherBits: Int = -1, isResumed: Boolean = false): ByteArray
}