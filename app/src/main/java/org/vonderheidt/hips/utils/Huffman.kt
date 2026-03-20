package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using Huffman encoding.
 */
object Huffman {
    private var lastInverseHuffmanCodes: MutableMap<String, Char>? = null

    /**
     * Function to get the inverse Huffman codes generated during compression of the last secret message (i.e. during last call of method `compress` of object `Huffman`).
     *
     * @return Inverse Huffman codes generated during compression of the last secret message.
     */
    fun getLastInverseHuffmanCodes(): MutableMap<String, Char> {
        return lastInverseHuffmanCodes!!
    }

    /**
     * Function to compress the secret message using Huffman encoding. Wrapper for function `compress` (and others) of class `HuffmanCoding`.
     * As a side effect, the inverse Huffman codes are stored in the corresponding attribute of the `Huffman` object.
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The compressed, 0-padded binary representation of the prepared secret message.
     */
    fun compress(preparedSecretMessage: String): ByteArray {
        // Initialize Huffman coding
        val huffmanCoding = HuffmanCoding<Char, Int>()

        // Count characters in secret message
        val charFrequencies = huffmanCoding.countCharFrequencies(preparedSecretMessage)

        // Construct Huffman tree
        huffmanCoding.buildHuffmanTree(charFrequencies)
        huffmanCoding.mergeHuffmanNodes()
        huffmanCoding.generateHuffmanCodes()        // Return value (root) is not needed here as Huffman tree is not traversed manually

        // Store inverse Huffman codes in attribute
        lastInverseHuffmanCodes = huffmanCoding.inverseHuffmanCodes

        // Compress secret message with Huffman codes
        val plainBitString = huffmanCoding.compress(preparedSecretMessage)

        // Add padding for ByteArray
        val paddedPlainBits = Format.asByteArrayWithPadding(plainBitString)

        return paddedPlainBits
    }

    /**
     * Function to decompress the secret message using Huffman decoding. Wrapper for function `decompress` of class `HuffmanCoding`.
     *
     * @param paddedPlainBits The compressed, 0-padded binary representation of a prepared secret message.
     * @param inverseHuffmanCodes Inverse mapping of Huffman codes to the corresponding characters.
     * @return The prepared secret message.
     */
    fun decompress(paddedPlainBits: ByteArray, inverseHuffmanCodes: MutableMap<String, Char>): String {
        // Remove padding of ByteArray
        val plainBitString = Format.asBitStringWithoutPadding(paddedPlainBits)

        // Initialize new Huffman coding
        val huffmanCoding = HuffmanCoding<Char, Int>()

        // Set inverse Huffman codes
        huffmanCoding.inverseHuffmanCodes = inverseHuffmanCodes

        // Decompress secret message with inverse Huffman codes
        val preparedSecretMessage = huffmanCoding.decompress(plainBitString)

        return preparedSecretMessage
    }

    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using Huffman encoding.
     *
     * Corresponds to Stegasuras method `encode_huffman` in `huffman_baseline.py`. Parameter `finish_sent` was removed (<=> is now hard coded to true).
     *
     * @param context The context to encode the secret message with.
     * @param cipherBits The encrypted binary representation of the secret message.
     * @return A cover text containing the secret message.
     */
    fun encode(context: String, cipherBits: ByteArray): String {
        val coverTextBytes = encode(
            context = context.toByteArray(charset = Charsets.UTF_8),
            cipherBits = cipherBits
        )

        val coverText = String(bytes = coverTextBytes, charset = Charsets.UTF_8)

        return coverText
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
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using Huffman encoding.
     *
     * Helper for the public `encode` function to bypass JNI errors with strings.
     *
     * Corresponds to Stegasuras method `encode_huffman` in `huffman_baseline.py`. Parameter `finish_sent` was removed (<=> is now hard coded to true).
     *
     * @param context The context to encode the secret message with (byte array storing UTF-8 encoded string to bypass JNI errors).
     * @param cipherBits The encrypted binary representation of the secret message.
     * @param bitsPerToken Number of bits to encode/decode per cover text token (= height of Huffman tree). Determined by Settings object.
     * @param ctx Memory address of the context.
     * @return A cover text containing the secret message (byte array storing UTF-8 encoded string to bypass JNI errors).
     */
    private external fun encode(context: ByteArray, cipherBits: ByteArray, bitsPerToken: Int = Settings.bitsPerToken, ctx: Long = LlamaCpp.getCtx()): ByteArray

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