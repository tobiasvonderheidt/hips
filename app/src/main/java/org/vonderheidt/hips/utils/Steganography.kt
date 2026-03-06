package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography encoding and decoding.
 */
object Steganography {

    /** Max characters per chunk. */
    private const val CHUNK_CHARS = 12

    /** Max cipher bytes before chunking kicks in. */
    private const val MAX_DIRECT_PAYLOAD = 14

    /** Score from the last encode. */
    var lastScore: SteganographyScoring.NaturalnessScore? = null
        private set

    /** Scores from all candidates in the last multi-candidate encode. */
    var lastCandidateScores: List<SteganographyScoring.NaturalnessScore> = emptyList()
        private set

    /** Flags to tell the decoder which compression was used. */
    private const val FLAG_UTF8: Byte       = 0x00
    private const val FLAG_ARITHMETIC: Byte = 0x01

    /**
     * Function to encode secret message into cover text(s) using given context.
     * Tries direct encoding first, chunks if the payload is too large.
     *
     * @param context The context to encode the secret message with.
     * @param secretMessage The secret message to be encoded.
     * @param conversionMode Conversion mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @return A list of cover texts containing the secret message.
     */
    suspend fun encode(
        context: String,
        secretMessage: String,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): List<String> {
        // Check if the whole message fits in one go
        LlamaCpp.resetInstance()
        val preparedFull = prepare(secretMessage)
        val plainBitsFull = adaptiveCompress(preparedFull)
        val cipherBitsFull = Crypto.encrypt(plainBitsFull)

        if (cipherBitsFull.size <= MAX_DIRECT_PAYLOAD) {
            android.util.Log.d("HiPS", "Direct encode: ${cipherBitsFull.size} cipher bytes")

            LlamaCpp.resetInstance()

            val coverText = when (steganographyMode) {
                SteganographyMode.Arithmetic -> { Arithmetic.encode(context, cipherBitsFull) }
                /* SteganographyMode.Bins -> { Bins.encode(context, cipherBitsFull) } */
                SteganographyMode.Huffman -> { Huffman.encode(context, cipherBitsFull) }
            }

            return listOf(coverText)
        }

        // Too large — split into chunks
        val chunks = secretMessage.chunked(CHUNK_CHARS)
        android.util.Log.d("HiPS", "Chunked encode: '${secretMessage}' -> ${chunks.size} chunk(s) of up to $CHUNK_CHARS chars")

        val coverTexts = mutableListOf<String>()

        for ((index, chunk) in chunks.withIndex()) {
            val coverText = encodeSingle(context, chunk, steganographyMode)
            android.util.Log.d("HiPS", "Chunk ${index + 1}/${chunks.size}: '${chunk}' -> cover text")
            coverTexts.add(coverText)
        }

        return coverTexts
    }

    /**
     * Encodes using multi-candidate selection.
     * Generates candidates at different temperatures, picks the best scoring one.
     *
     * @param context The context to encode the secret message with.
     * @param secretMessage The secret message to be encoded.
     * @param numCandidates Number of candidates to generate at each temperature.
     * @param temperatures List of temperatures to try.
     * @param minScore Minimum acceptable score.
     * @param conversionMode Conversion mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @return The best cover texts, or null if no candidate meets minimum score.
     */
    suspend fun encodeMultiCandidate(
        context: String,
        secretMessage: String,
        numCandidates: Int = 1,
        temperatures: List<Float> = listOf(0.8f, 0.9f, 1.0f, 1.1f),
        minScore: Double = 0.0,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): List<String>? {
        val originalTemperature = Settings.temperature

        // Check if chunking is needed
        LlamaCpp.resetInstance()
        val preparedFull = prepare(secretMessage)
        val plainBitsFull = adaptiveCompress(preparedFull)
        val cipherBitsFull = Crypto.encrypt(plainBitsFull)

        val textChunks = if (cipherBitsFull.size <= MAX_DIRECT_PAYLOAD) {
            listOf(secretMessage)
        } else {
            secretMessage.chunked(CHUNK_CHARS)
        }

        android.util.Log.d("HiPS", "Starting multi-candidate encode: ${textChunks.size} part(s), " +
                "${temperatures.size} temperatures, $numCandidates per temp")

        val coverTexts = mutableListOf<String>()
        val allScores = mutableListOf<SteganographyScoring.NaturalnessScore>()

        for ((chunkIndex, textChunk) in textChunks.withIndex()) {
            val candidates = mutableListOf<Pair<String, SteganographyScoring.NaturalnessScore>>()

            for (temperature in temperatures) {
                Settings.temperature = temperature

                for (i in 0 until numCandidates) {
                    val coverText = encodeSingle(context, textChunk, steganographyMode)

                    // Use inline score if available (arithmetic), otherwise score separately
                    val score = Arithmetic.lastScore ?: run {
                        LlamaCpp.resetInstance()
                        SteganographyScoring.scoreText(context, coverText)
                    }

                    val label = if (textChunks.size > 1) "Chunk ${chunkIndex + 1}/${textChunks.size}, candidate" else "Candidate"
                    android.util.Log.d("HiPS", "$label ${candidates.size + 1} at temp=$temperature: " +
                            "score=${String.format("%.1f", score.overallScore)} " +
                            "[perp=${String.format("%.1f", score.perplexity)}] " +
                            "text=$coverText")

                    candidates.add(Pair(coverText, score))
                }
            }

            val bestCandidate = candidates.maxByOrNull { it.second.overallScore }

            if (bestCandidate == null || bestCandidate.second.overallScore < minScore) {
                android.util.Log.d("HiPS", "Part ${chunkIndex + 1}: no candidate met minimum score")
                Settings.temperature = originalTemperature
                return null
            }

            android.util.Log.d("HiPS", "Selected best: score=${String.format("%.1f", bestCandidate.second.overallScore)}")
            coverTexts.add(bestCandidate.first)
            allScores.add(bestCandidate.second)
        }

        Settings.temperature = originalTemperature
        lastCandidateScores = allScores
        lastScore = allScores.lastOrNull()

        return coverTexts
    }

    /** Multi-candidate with a single temperature and minimum score threshold. */
    suspend fun encodeWithRetry(
        context: String,
        secretMessage: String,
        numCandidates: Int = 3,
        minScore: Double = 40.0
    ): List<String>? {
        return encodeMultiCandidate(
            context = context,
            secretMessage = secretMessage,
            numCandidates = numCandidates,
            temperatures = listOf(Settings.temperature),
            minScore = minScore
        )
    }

    /**
     * Function to decode secret message from one or more cover texts using given context.
     *
     * @param context The context to decode the cover text with.
     * @param coverTexts The cover texts containing the secret message (in order).
     * @param inverseHuffmanCodes Inverse mapping of Huffman codes to the corresponding characters (if applicable).
     * @param conversionMode Conversion mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @return The secret message.
     */
    suspend fun decode(
        context: String,
        coverTexts: List<String>,
        inverseHuffmanCodes: MutableMap<String, Char>? = null,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        val parts = mutableListOf<String>()

        for ((index, coverText) in coverTexts.withIndex()) {
            val part = decodeSingle(context, coverText, inverseHuffmanCodes, steganographyMode)
            android.util.Log.d("HiPS", "Decoded part ${index + 1}/${coverTexts.size}: '$part'")
            parts.add(part)
        }

        return parts.joinToString("")
    }

    /** Convenience overload for a single cover text. */
    suspend fun decode(
        context: String,
        coverText: String,
        inverseHuffmanCodes: MutableMap<String, Char>? = null,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        return decodeSingle(context, coverText, inverseHuffmanCodes, steganographyMode)
    }

    /** Runs a single piece of text through the full encode pipeline. */
    private suspend fun encodeSingle(
        context: String,
        text: String,
        steganographyMode: SteganographyMode
    ): String {
        val prepared = prepare(text)

        LlamaCpp.resetInstance()
        val plainBits = adaptiveCompress(prepared)
        val cipherBits = Crypto.encrypt(plainBits)

        android.util.Log.d("HiPS", "encodeSingle: '${text}' -> ${cipherBits.size} cipher bytes")

        LlamaCpp.resetInstance()

        return when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.encode(context, cipherBits) }
            /* SteganographyMode.Bins -> { Bins.encode(context, cipherBits) } */
            SteganographyMode.Huffman -> { Huffman.encode(context, cipherBits) }
        }
    }

    /** Runs a single cover text through the full decode pipeline. */
    private suspend fun decodeSingle(
        context: String,
        coverText: String,
        inverseHuffmanCodes: MutableMap<String, Char>?,
        steganographyMode: SteganographyMode
    ): String {
        // Invert step 3
        LlamaCpp.resetInstance()

        val cipherBits = when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.decode(context, coverText) }
            /* SteganographyMode.Bins -> { Bins.decode(context, coverText) } */
            SteganographyMode.Huffman -> { Huffman.decode(context, coverText) }
        }

        // Invert step 2
        val plainBitsWithFlag = Crypto.decrypt(cipherBits)

        // Invert step 1: Read flag byte to know which decompression to use
        LlamaCpp.resetInstance()

        val modeFlag = plainBitsWithFlag[0]
        val compressionBits = plainBitsWithFlag.drop(1).toByteArray()

        val preparedText = when (modeFlag) {
            FLAG_ARITHMETIC -> {
                android.util.Log.d("HiPS", "Decode: using arithmetic decompression")
                Arithmetic.decompress(compressionBits)
            }
            else -> {
                android.util.Log.d("HiPS", "Decode: using UTF-8 decoding")
                UTF8.decode(compressionBits)
            }
        }

        // Invert step 0
        return unprepare(preparedText)
    }

    /** Tries both compression methods, returns the smaller one with a flag byte. */
    private fun adaptiveCompress(preparedSecretMessage: String): ByteArray {
        val arithmeticBits = Arithmetic.compress(preparedSecretMessage)
        val utf8Bits = UTF8.encode(preparedSecretMessage)

        android.util.Log.d("HiPS", "Adaptive compression: arithmetic=${arithmeticBits.size} bytes, utf8=${utf8Bits.size} bytes")

        val useArithmetic = arithmeticBits.size <= utf8Bits.size

        android.util.Log.d("HiPS", "Adaptive compression chose: ${if (useArithmetic) "arithmetic" else "utf8"}")

        return if (useArithmetic) {
            byteArrayOf(FLAG_ARITHMETIC) + arithmeticBits
        } else {
            byteArrayOf(FLAG_UTF8) + utf8Bits
        }
    }

    /**
     * Function to prepare a secret message for binary encoding.
     *
     * Appends the ASCII NUL character to the original secret message. Needed to remove artefacts from greedy sampling after binary decoding.
     *
     * @param secretMessage A secret message.
     * @return The prepared secret message.
     */
    private fun prepare(secretMessage: String): String {
        val preparedSecretMessage = secretMessage + '\u0000'
        return preparedSecretMessage
    }

    /**
     * Function to unprepare a secret message after binary decoding.
     *
     * Strips the ASCII NUL character and everything after it. Therefore removes any artefacts from greedy sampling, rendering the original secret message.
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The secret message.
     */
    private fun unprepare(preparedSecretMessage: String): String {
        val secretMessage = preparedSecretMessage
            .split('\u0000')
            .first()
        return secretMessage
    }
}