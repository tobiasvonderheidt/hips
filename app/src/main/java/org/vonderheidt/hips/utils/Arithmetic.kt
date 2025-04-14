package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
            context = "",
            coverText = preparedSecretMessage,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 30
        )
    }

    /**
     * Function to decompress the secret message using arithmetic *encoding*. Wrapper for function `encode` of object `Arithmetic`.
     *
     * @param paddedPlainBits The compressed, 0-padded binary representation of a prepared secret message.
     * @return The prepared secret message.
     */
    fun decompress(paddedPlainBits: ByteArray): String {
        // Stegasuras:
        // Arithmetic decompression is just encoding with empty context
        // Same parameters as compression
        return encode(
            context = "",
            cipherBits = paddedPlainBits,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = 30
        )
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
    fun encode(context: String, cipherBits: ByteArray, temperature: Float = Settings.temperature, topK: Int = Settings.topK, precision: Int = Settings.precision): String {
        // Tokenize context
        var contextTokens = LlamaCpp.tokenize(context)

        // Convert cipher bits to bit string
        val isDecompression = contextTokens.isEmpty()

        val cipherBitString = if (isDecompression) Format.asBitStringWithoutPadding(cipherBits) else Format.asBitString(cipherBits)

        // Initialize array to store cover text tokens
        var coverTextTokens = intArrayOf()

        // <Logic specific to arithmetic coding>

        // Stegasuras paper says that binary conversion happens with empty context, but code actually uses a single end-of-generation (eog) token as context
        // llama.cpp crashes with empty context anyway
        // UI doesn't allow empty context for steganography, so no collision possible when calling Arithmetic.{decode,encode} for binary conversion
        if (isDecompression) {
            contextTokens += LlamaCpp.getEndOfGeneration()
        }

        // Define initial interval as [0, 2^precision)
        // Stegasuras variable "max_val" is redundant
        val currentInterval = intArrayOf(0, 1 shl precision) // Stegasuras: "Bottom inclusive, top exclusive"

        // </Logic specific to arithmetic coding>

        // Initialize variables and flags for loop
        var i = 0
        var isLastSentenceFinished = false

        var isFirstRun = true                   // llama.cpp batch needs to store context tokens in first run, but only last sampled token in subsequent runs
        var sampledToken = -1                   // Will always be overwritten with last cover text token

        // Sample tokens until all bits of secret message are encoded and last sentence is finished
        while (i < cipherBitString.length || !isLastSentenceFinished) {
            // Call llama.cpp to calculate the logit matrix similar to https://github.com/ggerganov/llama.cpp/blob/master/examples/simple/simple.cpp:
            // Needs only next tokens to be processed to store in a batch, i.e. contextTokens in first run and last sampled token in subsequent runs, rest is managed internally in ctx
            // Only last row of logit matrix is needed as it contains logits corresponding to last token of the prompt
            val logits = LlamaCpp.getLogits(if (isFirstRun) contextTokens else intArrayOf(sampledToken)).last()

            // Suppress special tokens to avoid early termination before all bits of secret message are encoded
            LlamaCpp.suppressSpecialTokens(logits)

            // <Logic specific to arithmetic coding>

            // Normalize logits to probabilities
            val probabilities = Statistics.softmax(logits)

            // </Logic specific to arithmetic coding>

            // Arithmetic sampling to encode bits of secret message into tokens
            if (i < cipherBitString.length) {
                // <Logic specific to arithmetic coding>

                // Scale probabilities with 1/temperature and sort descending
                val scaledProbabilities = probabilities
                    .mapIndexed { token, probability -> token to probability/temperature }
                    .sortedByDescending { it.second }
                    .toMutableList()

                // Stegasuras: "Cut off low probabilities that would be rounded to 0"
                // currentThreshold needs to be float as it will be compared to probabilities, float division happens implicitly in Python but explicitly in Kotlin
                val currentIntervalRange = currentInterval[1] - currentInterval[0]
                val currentThreshold = 1.0f / currentIntervalRange

                // Invert logic of Stegasuras:
                // Stegasuras: Drop all tokens with probability < currentThreshold
                // <=> HiPS: Keep all tokens with probability >= currentThreshold

                // Minimum ensures that k doesn't exceed topK
                // Maximum ensures that at least the tokens with the top 2 probabilities are considered
                // => Maximum is relevant if next token is practically certain (e.g. "Albert Einstein was a renowned theoretical" will be continued with " physicist" with > 99.5% probability)
                //    Probability of second most likely token will already be rounded to 0
                // => Loop can go through runs that don't encode any information (i.e. secret message bits) because a token is certain, but next token won't be certain and will encode information again
                //    Not possible with Huffman, where every token encodes bitsPerToken bits of information
                // => Matches entropy: Events that are certain don't contain any information (<=> Events that are very uncertain contain a lot of information)
                val k = min(
                    max(
                        2,
                        scaledProbabilities.filter { it.second >= currentThreshold }.size
                    ),
                    topK
                )

                // Keep tokens with top k (!= topK) probabilities
                // Stegasuras would use variable name roundedScaledProbabilities here already, but requires overwriting one data type with another (List<Pair<Int, Float>> vs List<Pair<Int, Int>>)
                // Possible in Python, but not in Kotlin
                // Use topScaledProbabilities for now to be similar to decode, roundedScaledProbabilities only after rounding probabilities from float to int below
                var topScaledProbabilities = scaledProbabilities.take(k)

                // Stegasuras: "Rescale to correct range"
                // Top k probabilities sum up to something in [0,1), rescale to [0, 2^precision)
                var sum = 0.0f

                for ((token, probability) in topScaledProbabilities) {
                    sum += probability
                }

                topScaledProbabilities = topScaledProbabilities.map {
                    Pair(it.first, it.second / sum * currentIntervalRange)
                }

                // Stegasuras: "Round probabilities to integers given precision"
                // Variable name roundedScaledProbabilities is appropriate now
                val roundedScaledProbabilities = topScaledProbabilities.map {
                    Pair(it.first, it.second.roundToInt())
                }

                // Replace probability with cumulated probability
                // Probabilities that would round to 0 were cut off earlier, so all at least round to 1, no collisions possible
                var cumulatedProbabilities = mutableListOf<Pair<Int, Int>>()
                var cumulatedProbability = 0

                for ((token, probability) in roundedScaledProbabilities) {
                    cumulatedProbability += probability
                    cumulatedProbabilities.add(Pair(token, cumulatedProbability))
                }

                // Stegasuras: "Remove any elements from the bottom if rounding caused the total prob to be too large"
                // Remove tokens with low probabilities if their cumulated probability is too large
                val overfill = cumulatedProbabilities.filter { it.second > currentIntervalRange }

                if (overfill.isNotEmpty()) {
                    cumulatedProbabilities = cumulatedProbabilities.dropLast(overfill.size).toMutableList()
                }

                // Stegasuras: "Add any mass to the top if removing/rounding causes the total prob to be too small"
                // Removing tokens might have created a gap at the top, i.e. a sub-interval between cumulated probability of last token and top of current interval, that doesn't correspond to any token
                // Arithmetic coding only works when current interval is exactly filled, so close the gap by shifting all cumulated probabilities up by its size
                // Equivalent to first token having larger probability, shifting cumulated probabilities of all subsequent tokens
                cumulatedProbabilities = cumulatedProbabilities.map {
                    Pair(it.first, it.second + currentIntervalRange - cumulatedProbabilities.last().second)
                }.toMutableList()

                // Stegasuras: "Convert to position in range"
                // Shifts all cumulated probabilities up again by bottom of current interval
                cumulatedProbabilities = cumulatedProbabilities.map {
                    Pair(it.first, it.second + currentInterval[0])
                }.toMutableList()

                // Replace token of last sub-interval with ASCII NUL character so it can be sampled during decompression
                // Similar to explanation at https://www.youtube.com/watch?v=RFWJM8JMXBs
                if (isDecompression) {
                    scaledProbabilities[cumulatedProbabilities.lastIndex] = Pair(LlamaCpp.getAsciiNul(), scaledProbabilities[cumulatedProbabilities.lastIndex].second)
                    cumulatedProbabilities[cumulatedProbabilities.lastIndex] = Pair(LlamaCpp.getAsciiNul(), cumulatedProbabilities[cumulatedProbabilities.lastIndex].second)
                }

                // Stegasuras: "Get selected index based on binary fraction from message bits"
                // Process cipher bits in portions of size precision
                // Unlike Python, Kotlin doesn't handle "cipherBitString.substring(startIndex = i, endIndex = i + precision)" gracefully if i + precision is too large
                var cipherBitSubstring = if (i + precision < cipherBitString.length) {
                    cipherBitString.substring(startIndex = i, endIndex = i + precision)
                }
                else {
                    cipherBitString.substring(startIndex = i)
                }

                // Append 0s to last cipher bits to make last portion of size precision as well
                if (i + precision > cipherBitString.length) {
                    cipherBitSubstring += "0".repeat(i + precision - cipherBitString.length)
                }

                // Convert portion of cipher bits to integer for comparison with cumulated probabilities
                // Find position of first token with cumulated probability larger than this integer, i.e. find relevant sub-interval of current interval
                // => sampledToken is already determined here, next steps only calculate new interval
                val messageIdx = Format.asInteger(cipherBitSubstring)                                 // Stegasuras would reverse cipherBitSubstring, shouldn't be necessary here
                val selectedSubinterval = cumulatedProbabilities.indexOfFirst { it.second > messageIdx }

                // Stegasuras: "Calculate new range as ints"
                // Calculate bottom and top of relevant sub-interval for next iteration
                // New bottom (inclusive) is top of preceding sub-interval (exclusive there) if relevant one is not the first one, old bottom otherwise
                // New top (exclusive) is top of relevant sub-interval
                val newIntervalBottom = if (selectedSubinterval > 0) cumulatedProbabilities[selectedSubinterval-1].second else currentInterval[0]
                val newIntervalTop = cumulatedProbabilities[selectedSubinterval].second

                // Stegasuras: "Convert range to bits"
                val newIntervalBottomBitsInclusive = Format.asBitString(newIntervalBottom, precision)          // Again, reversing shouldn't be necessary here
                val newIntervalTopBitsInclusive = Format.asBitString(newIntervalTop - 1, precision)            // Stegasuras: "-1 here because upper bound is exclusive" (i.e. newIntervalTopBitsInclusive is inclusive)

                // Stegasuras: "Consume most significant bits which are now fixed and update interval"
                // Arithmetic coding encodes data into a number by iteratively narrowing initial interval defined earlier
                // Therefore most significant bits are fixed first (~ numberOfSameBitsFromBeginning), determining the order of magnitude of the number, less significant bits are fixed later
                val numberOfEncodedBits = numberOfSameBitsFromBeginning(newIntervalBottomBitsInclusive, newIntervalTopBitsInclusive)
                i += numberOfEncodedBits

                // New interval is determined by setting unfixed bits to 0 for bottom end, to 1 for top end
                // Interval boundaries can jump around because first numberOfEncodedBits bits are already processed and therefore cut off
                // Next portion of cipher bits in general doesn't narrow the interval
                val newIntervalBottomBits = newIntervalBottomBitsInclusive.substring(startIndex = numberOfEncodedBits) + "0".repeat(numberOfEncodedBits)
                val newIntervalTopBits = newIntervalTopBitsInclusive.substring(startIndex = numberOfEncodedBits) + "1".repeat(numberOfEncodedBits)

                currentInterval[0] = Format.asInteger(newIntervalBottomBits)                            // Again, reversing shouldn't be necessary here
                currentInterval[1] = Format.asInteger(newIntervalTopBits) + 1                           // Stegasuras: "+1 here because upper bound is exclusive"

                // Sample token as determined above
                sampledToken = cumulatedProbabilities[selectedSubinterval].first

                // </Logic specific to arithmetic coding>

                // Update flag
                isFirstRun = false
            }
            // Greedy sampling to pick most likely token until last sentence is finished
            else {
                // Get most likely token
                sampledToken = getTopProbability(probabilities)

                // Update flag
                isLastSentenceFinished = LlamaCpp.isEndOfSentence(sampledToken)
            }

            // Append last sampled token to cover text tokens
            coverTextTokens += sampledToken

            // Stegasuras: "For text->bits->text"
            // Variable "partial" not needed here as cover text isn't appended to context
            if (coverTextTokens.last() == LlamaCpp.getAsciiNul()) {
                break
            }
        }

        // Detokenize cover text tokens into cover text to return it
        val coverText = LlamaCpp.detokenize(coverTextTokens)

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
    fun decode(context: String, coverText: String, temperature: Float = Settings.temperature, topK: Int = Settings.topK, precision: Int = Settings.precision): ByteArray {
        // Tokenize context and cover text
        var contextTokens = LlamaCpp.tokenize(context)
        var coverTextTokens = LlamaCpp.tokenize(coverText)

        // <Logic specific to arithmetic coding>

        // Similar to encode
        val isCompression = contextTokens.isEmpty()

        if (isCompression) {
            contextTokens += LlamaCpp.getEndOfGeneration()

            // During compression, Stegasuras appends eog token ('<eos>') to secret message passed via cover text parameter
            // Not done here as ASCII NUL is used instead (see translation of "partial" variable in encode)
        }

        val currentInterval = intArrayOf(0, 1 shl precision)

        // </Logic specific to arithmetic coding>

        // Initialize string to store cipher bits
        var cipherBitString = ""

        // Initialize variables and flags for loop
        var i = 0

        var isFirstRun = true
        var coverTextToken = -1

        // Decode every cover text token
        while (i < coverTextTokens.size) {
            // Calculate the logit matrix again initially from context tokens, then from last cover text token, and get last row
            val logits = LlamaCpp.getLogits(if (isFirstRun) contextTokens else intArrayOf(coverTextToken)).last()

            // Suppress special tokens
            LlamaCpp.suppressSpecialTokens(logits)

            // <Logic specific to arithmetic coding>

            // Similar to encode
            val probabilities = Statistics.softmax(logits)

            val scaledProbabilities = probabilities
                .mapIndexed { token, probability -> token to probability/temperature }
                .sortedByDescending { it.second }
                .toMutableList()

            // Stegasuras: "Cut off low probabilities that would be rounded to 0"
            val currentIntervalRange = currentInterval[1] - currentInterval[0]
            val currentThreshold = 1.0f / currentIntervalRange

            var k = min(
                max(
                    2,
                    scaledProbabilities.filter { it.second >= currentThreshold }.size
                ),
                topK
            )

            // Don't reassign "scaledProbabilities = scaledProbabilities.take(k)" but introduce new variable topScaledProbabilities as decode needs scaledProbabilities again later
            var topScaledProbabilities = scaledProbabilities.take(k)

            // Stegasuras: "Rescale to correct range"
            var sum = 0.0f

            for ((token, probability) in topScaledProbabilities) {
                sum += probability
            }

            topScaledProbabilities = topScaledProbabilities.map {
                Pair(it.first, it.second / sum * currentIntervalRange)
            }

            // Stegasuras: "Round probabilities to integers given precision"
            val roundedScaledProbabilities = topScaledProbabilities.map {
                Pair(it.first, it.second.roundToInt())
            }

            var cumulatedProbabilities = mutableListOf<Pair<Int, Int>>()
            var cumulatedProbability = 0

            for ((token, probability) in roundedScaledProbabilities) {
                cumulatedProbability += probability
                cumulatedProbabilities.add(Pair(token, cumulatedProbability))
            }

            // Stegasuras: "Remove any elements from the bottom if rounding caused the total prob to be too large"
            val overfill = cumulatedProbabilities.filter { it.second > currentIntervalRange }

            if (overfill.isNotEmpty()) {
                cumulatedProbabilities = cumulatedProbabilities.dropLast(overfill.size).toMutableList()
                // Reassignment of k is new in decode, but not used here as possible BPE errors are ignored below
                // Logic of Stegasuras is somewhat inverted again
                // Stegasuras: overfill_index[0] = Index of first token with cumulated probability > cur_int_range
                //             = Number of tokens with cumulated probability <= cur_int_range
                //             = Size of cum_probs after it was overwritten there
                // HiPS: overfill = List of tokens with cumulated probability > currentIntervalRange
                //       != Size of cumulatedProbabilities after it was overwritten here
                // Now "if (rank >= k) { ... }" from BPE fixes below makes sense
                k = cumulatedProbabilities.size
            }

            // Stegasuras: "Add any mass to the top if removing/rounding causes the total prob to be too small"
            cumulatedProbabilities = cumulatedProbabilities.map {
                Pair(it.first, it.second + currentIntervalRange - cumulatedProbabilities.last().second)
            }.toMutableList()

            // Stegasuras: "Convert to position in range"
            cumulatedProbabilities = cumulatedProbabilities.map {
                Pair(it.first, it.second + currentInterval[0])
            }.toMutableList()

            // Replace token of last sub-interval with ASCII NUL character so it can be sampled during compression
            // Similar to explanation at https://www.youtube.com/watch?v=RFWJM8JMXBs
            if (isCompression) {
                scaledProbabilities[cumulatedProbabilities.lastIndex] = Pair(LlamaCpp.getAsciiNul(), scaledProbabilities[cumulatedProbabilities.lastIndex].second)
                cumulatedProbabilities[cumulatedProbabilities.lastIndex] = Pair(LlamaCpp.getAsciiNul(), cumulatedProbabilities[cumulatedProbabilities.lastIndex].second)
            }

            // Stegasuras: n/a
            // Determine rank of predicted token amongst all tokens based on its probability
            var rank = scaledProbabilities.indexOfFirst { it.first == coverTextTokens[i] }

            // Stegasuras: "Handle most errors that could happen because of BPE with heuristic"
            // Rank can't exceed cumulatedProbabilities indices
            if (rank >= k) {
                // Actual cover text token i
                val trueTokenText = LlamaCpp.detokenize(intArrayOf(coverTextTokens[i]))

                // Python control flow with else outside loop after if inside loop is not possible in Kotlin, introduce flag instead
                var isBpeFixed = false

                // Loop through predicted tokens
                for (rankIdx in 0 until k) {
                    // Predicted cover text token i
                    val propTokenText = LlamaCpp.detokenize(intArrayOf(scaledProbabilities[rankIdx].first))

                    // Stegasuras: "Is there a more likely prefix token that could be the actual token generated?"
                    // E.g. secret message "Albert Einstein was a renowned theoretical physicist" is tokenized to ["Albert", " Einstein", ...], but "A" is more likely prefix to "Albert"
                    // Tokenization should therefore changed to e.g. ["A", "lbert", ...], i.e. split unlikely tokens into more likely tokens
                    // => Relevant when decode is called for compression as first tokens of secret message are hard to predict
                    if (propTokenText.length <= trueTokenText.length && propTokenText == trueTokenText.substring(startIndex = 0, endIndex = propTokenText.length)) {
                        // Update rank, i.e. token to be sampled
                        rank = rankIdx

                        // Tokenize suffix, e.g. "lbert" into ["l", "bert"]
                        val suffix = trueTokenText.substring(startIndex = propTokenText.length)
                        val suffixTokens = LlamaCpp.tokenize(suffix)

                        // Replace unlikely cover text token with its more likely prefix
                        coverTextTokens[i] = scaledProbabilities[rankIdx].first

                        // Insert suffix after prefix to restore cover text
                        coverTextTokens = coverTextTokens
                            .toMutableList()
                            .apply { addAll(i + 1, suffixTokens.toMutableList()) } // Stegasuras: "Insert suffix tokens into list"
                            .toIntArray()

                        isBpeFixed = true
                        break
                    }
                }
                // Stegasuras: "Unable to fix BPE error"
                if (!isBpeFixed) {
                    rank = 0
                }
            }

            // Sample token at (corrected) rank
            val selectedSubinterval = rank

            // Stegasuras: "Calculate new range as ints"
            val newIntervalBottom = if (selectedSubinterval > 0) cumulatedProbabilities[selectedSubinterval-1].second else currentInterval[0]
            val newIntervalTop = cumulatedProbabilities[selectedSubinterval].second

            // Stegasuras: "Convert range to bits"
            val newIntervalBottomBitsInclusive = Format.asBitString(newIntervalBottom, precision)
            val newIntervalTopBitsInclusive = Format.asBitString(newIntervalTop - 1, precision)

            // Stegasuras: "Emit most significant bits which are now fixed and update interval"
            // Inline += operation to eliminate newBits variable
            val numberOfEncodedBits = numberOfSameBitsFromBeginning(newIntervalBottomBitsInclusive, newIntervalTopBitsInclusive)

            cipherBitString += if (i == coverTextTokens.size - 1) {
                newIntervalBottomBitsInclusive
            }
            else {
                newIntervalTopBitsInclusive.substring(startIndex = 0, endIndex = numberOfEncodedBits)
            }

            val newIntervalBottomBits = newIntervalBottomBitsInclusive.substring(startIndex = numberOfEncodedBits) + "0".repeat(numberOfEncodedBits)
            val newIntervalTopBits = newIntervalTopBitsInclusive.substring(startIndex = numberOfEncodedBits) + "1".repeat(numberOfEncodedBits)

            currentInterval[0] = Format.asInteger(newIntervalBottomBits)
            currentInterval[1] = Format.asInteger(newIntervalTopBits) + 1

            // </Logic specific to arithmetic coding>

            // Update loop variables and flags
            coverTextToken = coverTextTokens[i]
            isFirstRun = false

            i++
        }

        // Create ByteArray from bit string to return cipher bits
        val cipherBits = if (isCompression) Format.asByteArrayWithPadding(cipherBitString) else Format.asByteArray(cipherBitString)

        return cipherBits
    }

    /**
     * Function to determine number of bits that are the same from the beginning of two bit strings.
     *
     * Corresponds to Stegasuras method `num_same_from_beg` in `utils.py`. Parameter `bits1` was renamed to `bitString1`, `bits2` to `bitString2`.
     *
     * @param bitString1 A bit string.
     * @param bitString2 Another bit string.
     * @return Number of bits that are the same from the beginning of the bit strings.
     * @throws IllegalArgumentException If the two bit strings are of different length.
     */
    private fun numberOfSameBitsFromBeginning(bitString1: String, bitString2: String): Int {
        // Only edge case covered in Stegasuras
        if (bitString1.length != bitString2.length) {
            throw IllegalArgumentException("The bit strings are of different length")
        }

        var numberOfSameBitsFromBeginning = 0

        for (i in bitString1.indices) {
            if (bitString1[i] != bitString2[i]) {
                break
            }

            numberOfSameBitsFromBeginning++
        }

        return numberOfSameBitsFromBeginning
    }

    /**
     * Function to get the top probability for the last token of the prompt.
     *
     * @param probabilities Probabilities for the last token of the prompt (= last row of logits matrix after normalization).
     * @return ID of the most likely token.
     */
    private fun getTopProbability(probabilities: FloatArray): Int {
        val sampledToken = probabilities
            .mapIndexed { token, logit -> token to logit }  // Convert to List<Pair<Int, Float>> so token IDs won't get lost
            .sortedByDescending { it.second }               // Sort pairs descending based on probabilities
            .first().first                                  // Get ID of most likely token

        return sampledToken
    }
}