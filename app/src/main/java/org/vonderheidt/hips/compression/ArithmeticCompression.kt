package org.vonderheidt.hips.compression

import android.util.Log
import bitmage.BitString
import org.vonderheidt.hips.utils.Arithmetic
import org.vonderheidt.hips.utils.LlamaCpp

/**
 * Object (i.e. singleton class) that represents the binary conversion of the secret message using UTF-8 encoding.
 *
 * Renamed from `Unicode` in Stegasuras as UTF-8 is only one of many possible Unicode encodings.
 */
object ArithmeticCompression : CompressionProvider {

    override fun compress(message: String): BitString {
        LlamaCpp.resetInstance()
        // Stegasuras:
        // Arithmetic compression is just decoding with empty context
        // Parameters temperature, topK and precision are not taken from settings, but hard-coded to use the unmodulated LLM
        // While topK is set to the vocabulary size of the LLM, precision is set as high as possible so (ideally) no tokens have probability < 1/2^precision
        val bytes = Arithmetic.decode(
            context = "".toByteArray(charset = Charsets.UTF_8),
            coverText = message.toByteArray(charset = Charsets.UTF_8),
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 40
        )

        val bits = BitString(bytes, bytes.size * 8)
        val paddingLen = bits.takeFew(8).toInt()
        bits.takeFew(paddingLen)

        return bits
    }


    // TODO Downward concat of split cover text
    //  Parameter isResumed in all subsequent functions is to differentiate first from subsequent calls
    override fun inflate(plainBits: BitString, isResumed: Boolean): String {

        if (isResumed) {
            // Restore ctx for decompression
            LlamaCpp.setCtx(ctx = LlamaCpp.getDecompressCtx())
        }
        else {
            // Reset ctx
            LlamaCpp.resetInstance()
        }


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
            isResumed = isResumed
        )

        // Save ctx for decompression
        LlamaCpp.setDecompressCtx(decompressCtx = LlamaCpp.getCtx())

        return decodedBytes.decodeToString()
    }
}