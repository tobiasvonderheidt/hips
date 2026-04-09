package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString
import org.vonderheidt.hips.utils.Arithmetic
import org.vonderheidt.hips.utils.LlamaCpp

/**
 * Object (i.e. singleton class) that represents arithmetic compression.
 */
object ArithmeticCompression : CompressionProvider {
    /**
     * Function to compress the secret message using arithmetic *decoding*. Wrapper for function `decode` of object `Arithmetic`.
     *
     * @param secretMessage A secret message.
     * @return The compressed binary representation of the secret message.
     */
    override fun compress(secretMessage: String): BitString {
        LlamaCpp.resetInstance()
        // Stegasuras:
        // Arithmetic compression is just decoding with empty context
        // Parameters temperature, topK and precision are not taken from settings, but hard-coded to use the unmodulated LLM
        // While topK is set to the vocabulary size of the LLM, precision is set as high as possible so (ideally) no tokens have probability < 1/2^precision
        val bytes = Arithmetic.decode(
            context = "".toByteArray(charset = Charsets.UTF_8),
            coverText = secretMessage.toByteArray(charset = Charsets.UTF_8),
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40
        )

        val bits = BitString(bytes, bytes.size * 8)
        val paddingLen = bits.takeFew(8).toInt()
        bits.takeFew(paddingLen)

        return bits
    }

    /**
     * Function to decompress the secret message using arithmetic *encoding*. Wrapper for function `encode` of object `Arithmetic`.
     *
     * @param plainBits The compressed binary representation of a secret message.
     * @return The secret message.
     */
    override fun decompress(plainBits: BitString): String {

        // Reset ctx
        LlamaCpp.resetInstance()

        // Stegasuras:
        // Arithmetic decompression is just encoding with empty context
        // Same parameters as compression
        val compressedBytes = plainBits.toBitFragment()

        val decodedBytes = Arithmetic.encode(
            context = "".toByteArray(charset = Charsets.UTF_8),
            cipherBits = compressedBytes.bytes,
            bitLength = compressedBytes.bitLength,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40,
            isResumed = false
        )

        return decodedBytes.decodeToString()
    }
}