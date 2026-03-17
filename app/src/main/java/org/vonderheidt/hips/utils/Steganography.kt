package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography encoding and decoding.
 */
object Steganography {

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
     * Encodes the full message in one pass, then splits the cover text by sentence boundaries.
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
        val coverText = encodeSingle(context, secretMessage, conversionMode, steganographyMode)

        val splitTexts = splitBySentence(coverText)
        android.util.Log.d("HiPS", "COVER TEXT SPLIT: 1 cover text -> ${splitTexts.size} part(s)")

        return splitTexts
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

        android.util.Log.d("HiPS", "Starting multi-candidate encode: " +
                "${temperatures.size} temperatures, $numCandidates per temp")

        val candidates = mutableListOf<Triple<String, SteganographyScoring.NaturalnessScore, Float>>()

        for (temperature in temperatures) {
            Settings.temperature = temperature

            for (i in 0 until numCandidates) {
                // Encode the secret message into a cover text
                val coverText: String
                try {
                    coverText = encodeSingle(context, secretMessage, conversionMode, steganographyMode)
                } catch (e: Exception) {
                    android.util.Log.w("HiPS", "Candidate encoding failed at temp=$temperature: ${e.message}")
                    continue
                }

                // Use inline score from arithmetic encoding if available,
                // otherwise use a default score — separate scoring via getLogits
                // is unreliable after encoding exhausts the LLM context
                val score = Arithmetic.lastScore
                    ?: SteganographyScoring.NaturalnessScore(
                        perplexity = 0.0,
                        avgRank = 0.0,
                        avgLogProbability = 0.0,
                        maxRank = 0,
                        minProbability = 0.0,
                        tokenCount = 0
                    )

                android.util.Log.d("HiPS", "Candidate ${candidates.size + 1} at temp=$temperature: " +
                        "score=${String.format("%.1f", score.overallScore)} " +
                        "[perp=${String.format("%.1f", score.perplexity)}] " +
                        "text=$coverText")

                candidates.add(Triple(coverText, score, temperature))
            }
        }

        // If no candidates survived, fall back
        if (candidates.isEmpty()) {
            android.util.Log.w("HiPS", "All candidates failed, returning null to trigger fallback")
            Settings.temperature = originalTemperature
            return null
        }

        val bestCandidate = candidates.maxByOrNull { it.second.overallScore }

        if (bestCandidate == null || bestCandidate.second.overallScore < minScore) {
            android.util.Log.d("HiPS", "No candidate met minimum score")
            Settings.temperature = originalTemperature
            return null
        }

        // Set temperature to the winning candidate's temperature so decode uses the same distribution
        Settings.temperature = bestCandidate.third
        android.util.Log.d("HiPS", "Selected best: score=${String.format("%.1f", bestCandidate.second.overallScore)} temp=${bestCandidate.third}")
        lastCandidateScores = listOf(bestCandidate.second)
        lastScore = bestCandidate.second

        // Split the winning cover text by sentence boundaries
        val splitTexts = splitBySentence(bestCandidate.first)
        android.util.Log.d("HiPS", "COVER TEXT SPLIT: 1 cover text -> ${splitTexts.size} part(s)")

        return splitTexts
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
        // Rejoin split cover texts back into one for decoding
        val fullCoverText = coverTexts.joinToString("")
        android.util.Log.d("HiPS", "COVER TEXT REJOIN: ${coverTexts.size} part(s) -> 1 cover text")

        return decodeSingle(context, fullCoverText, inverseHuffmanCodes, conversionMode, steganographyMode)
    }

    /** Convenience overload for a single cover text. */
    suspend fun decode(
        context: String,
        coverText: String,
        inverseHuffmanCodes: MutableMap<String, Char>? = null,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        return decodeSingle(context, coverText, inverseHuffmanCodes, conversionMode, steganographyMode)
    }

    /**
     * Runs a single piece of text through the full encode pipeline.
     *
     * Uses adaptive compression (tries both arithmetic and UTF-8, picks the smaller result)
     * unless a specific conversion mode is explicitly set to a non-adaptive option.
     */
    private suspend fun encodeSingle(
        context: String,
        text: String,
        conversionMode: ConversionMode,
        steganographyMode: SteganographyMode
    ): String {
        // Step 0: Prepare secret message by appending ASCII NUL character
        // Kotlin doesn't use NUL-terminated strings, instead makes them immutable and stores length
        // So using NUL as a character in a Kotlin string can't cause any collisions
        val prepared = prepare(text)

        // Step 1: Convert secret message to a (compressed) binary representation
        LlamaCpp.resetInstance()

        val plainBits = when (conversionMode) {
            // Adaptive: try both methods, pick smaller, prepend flag byte for decoder
            ConversionMode.Arithmetic -> adaptiveCompress(prepared)
            // Explicit UTF-8 only
            ConversionMode.UTF8 -> byteArrayOf(FLAG_UTF8) + UTF8.encode(prepared)
        }

        // Step 2: Encrypt binary representation of secret message
        val cipherBits = Crypto.encrypt(plainBits)

        android.util.Log.d("HiPS", "encodeSingle: '${text}' -> ${cipherBits.size} cipher bytes")

        // Step 3: Encode encrypted binary representation of secret message into cover text
        LlamaCpp.resetInstance()

        val coverText = when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.encode(context, cipherBits) }
            /* SteganographyMode.Bins -> { Bins.encode(context, cipherBits) } */
            SteganographyMode.Huffman -> { Huffman.encode(context, cipherBits) }
        }

        // Log bits per token
        android.util.Log.d("HiPS", "Bits per token: ${String.format("%.2f", (cipherBits.size * 8).toDouble() / LlamaCpp.tokenize(coverText).size)} (${cipherBits.size * 8} bits / ${LlamaCpp.tokenize(coverText).size} tokens)")

        return coverText
    }

    /**
     * Runs a single cover text through the full decode pipeline.
     *
     * Reads the flag byte prepended during encoding to determine which decompression to use.
     */
    private suspend fun decodeSingle(
        context: String,
        coverText: String,
        inverseHuffmanCodes: MutableMap<String, Char>?,
        conversionMode: ConversionMode,
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

        val preparedSecretMessage = when (modeFlag) {
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
        return unprepare(preparedSecretMessage)
    }

    /**
     * Tries both compression methods, returns the smaller one with a flag byte prepended.
     * The flag byte tells the decoder which method was used.
     */
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

    /** Splits a cover text into groups of 2 sentences for more natural multi-message output. */
    private fun splitBySentence(coverText: String): List<String> {
        // Split after sentence-ending punctuation + space, without consuming any characters
        val sentences = coverText.split(Regex("(?<=[.!?] )(?=\\S)"))

        // Group into pairs of 2 sentences
        return if (sentences.size <= 2) {
            listOf(coverText)
        } else {
            sentences.chunked(2).map { it.joinToString("") }
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
        // Kotlin doesn't use NUL-terminated strings, instead makes them immutable and stores length
        // So using NUL as a character in a Kotlin string can't cause any collisions
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