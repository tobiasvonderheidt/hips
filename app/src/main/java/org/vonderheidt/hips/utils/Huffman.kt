package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using Huffman encoding.
 */
object Huffman {
    private var lastInverseHuffmanCodes: MutableMap<String, Char>? = null

    /**
     * Function to get the inverse Huffman codes generated during compression of the last secret message.
     */
    fun getLastInverseHuffmanCodes(): MutableMap<String, Char> {
        return lastInverseHuffmanCodes!!
    }

    /**
     * Function to compress the secret message using Huffman encoding.
     */
    fun compress(preparedSecretMessage: String): ByteArray {
        val huffmanCoding = HuffmanCoding<Char, Int>()
        val charFrequencies = huffmanCoding.countCharFrequencies(preparedSecretMessage)
        huffmanCoding.buildHuffmanTree(charFrequencies)
        huffmanCoding.mergeHuffmanNodes()
        huffmanCoding.generateHuffmanCodes()
        lastInverseHuffmanCodes = huffmanCoding.inverseHuffmanCodes
        val plainBitString = huffmanCoding.compress(preparedSecretMessage)
        return Format.asByteArrayWithPadding(plainBitString)
    }

    /**
     * Function to decompress the secret message using Huffman decoding.
     */
    fun decompress(paddedPlainBits: ByteArray, inverseHuffmanCodes: MutableMap<String, Char>): String {
        val plainBitString = Format.asBitStringWithoutPadding(paddedPlainBits)
        val huffmanCoding = HuffmanCoding<Char, Int>()
        huffmanCoding.inverseHuffmanCodes = inverseHuffmanCodes
        return huffmanCoding.decompress(plainBitString)
    }

    /**
     * Function to encode the secret message into a cover text using Huffman encoding.
     */
    fun encode(context: String, cipherBits: ByteArray): String {
        val resultBytes = encodeNative(
            context = context.toByteArray(Charsets.UTF_8),
            cipherBits = cipherBits
        )
        return String(resultBytes, Charsets.UTF_8)
    }

    /**
     * Function to decode a cover text into the secret message using Huffman decoding.
     */
    fun decode(context: String, coverText: String): ByteArray {
        return decodeNative(
            context = context.toByteArray(Charsets.UTF_8),
            coverText = coverText.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Internal JNI function to encode the secret message into a cover text.
     */
    private external fun encodeNative(
        context: ByteArray, 
        cipherBits: ByteArray, 
        bitsPerToken: Int = Settings.bitsPerToken, 
        ctx: Long = LlamaCpp.getCtx()
    ): ByteArray

    /**
     * Internal JNI function to decode a cover text into the secret message.
     */
    private external fun decodeNative(
        context: ByteArray, 
        coverText: ByteArray, 
        bitsPerToken: Int = Settings.bitsPerToken, 
        ctx: Long = LlamaCpp.getCtx()
    ): ByteArray
}
