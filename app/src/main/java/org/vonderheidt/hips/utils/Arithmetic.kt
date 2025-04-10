package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings
import kotlin.math.ceil
import kotlin.math.log2
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
        return decode(
            context = "",
            coverText = preparedSecretMessage,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = ceil(log2(LlamaCpp.getVocabSize().toFloat())).toInt()
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
        // Parameters temperature, topK and precision are not taken from settings, but hard-coded to use the unmodulated LLM
        return encode(
            context = "",
            cipherBits = paddedPlainBits,
            temperature = 1.0f,
            topK = LlamaCpp.getVocabSize(),
            precision = ceil(log2(LlamaCpp.getVocabSize().toFloat())).toInt(),
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
        // Similarly, an eog token also is appended to the secret message (passed via cover text parameter)
        // llama.cpp crashes with empty context anyway
        // UI doesn't allow empty context for steganography, so no collision possible when calling Arithmetic.{decode,encode} for binary conversion
        if (isDecompression) {
            contextTokens += LlamaCpp.getEndOfGeneration()
        }

        // Define initial interval as [0, maxVal) = [0, 2^precision)
        val maxVal = 1 shl precision
        val curInterval = intArrayOf(0, maxVal) // Stegasuras: "Bottom inclusive, top exclusive"

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
                val probsTemp = probabilities
                    .mapIndexed { token, probability -> token to probability/temperature }
                    .sortedByDescending { it.second }

                // Stegasuras: "Cut off low probabilities that would be rounded to 0"
                // curThreshold needs to be float as it will be compared to probabilities, float division happens implicitly in Python but explicitly in Kotlin
                val curIntRange = curInterval[1] - curInterval[0]
                val curThreshold = 1.0f / curIntRange

                // Invert logic of Stegasuras:
                // Stegasuras: Drop all tokens with probability < curThreshold
                // <=> HiPS: Keep all tokens with probability >= curThreshold

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
                        probsTemp.filter { it.second >= curThreshold }.size
                    ),
                    topK
                )

                // Keep tokens with top k (!= topK) probabilities
                // Stegasuras would use variable name probsTempInt here already, but requires overwriting one data type with another (List<Pair<Int, Float>> vs List<Pair<Int, Int>>)
                // Possible in Python, but not in Kotlin
                // Use topProbsTemp for now to be similar to decode, probsTempInt only after rounding probabilities from float to int below
                var topProbsTemp = probsTemp.take(k)

                // Stegasuras: "Rescale to correct range"
                // Top k probabilities sum up to something in [0,1), rescale to [0, maxVal)
                var topProbsTempSum = 0.0f

                for ((token, probability) in topProbsTemp) {
                    topProbsTempSum += probability
                }

                topProbsTemp = topProbsTemp.map {
                    Pair(it.first, it.second / topProbsTempSum * curIntRange)
                }

                // Stegasuras: "Round probabilities to integers given precision"
                // Variable name probsTempInt is appropriate now
                val probsTempInt = topProbsTemp.map {
                    Pair(it.first, it.second.roundToInt())
                }

                // Replace probability with cumulated probability
                // Probabilities that would round to 0 were cut off earlier, so all at least round to 1, no collisions possible
                var cumProbs = mutableListOf<Pair<Int, Int>>()
                var cumulatedProbability = 0

                for ((token, probability) in probsTempInt) {
                    cumulatedProbability += probability
                    cumProbs.add(Pair(token, cumulatedProbability))
                }

                // Stegasuras: "Remove any elements from the bottom if rounding caused the total prob to be too large"
                // Remove tokens with low probabilities if their cumulated probability is too large
                val overfillIndex = cumProbs.filter { it.second > curIntRange }

                if (overfillIndex.isNotEmpty()) {
                    cumProbs = cumProbs.dropLast(overfillIndex.size).toMutableList()
                }

                // Stegasuras: "Add any mass to the top if removing/rounding causes the total prob to be too small"
                // Removing tokens might have created a gap at the top, i.e. a sub-interval between cumulated probability of last token and top of current interval, that doesn't correspond to any token
                // Arithmetic coding only works when current interval is exactly filled, so close the gap by shifting all cumulated probabilities up by its size
                // Equivalent to first token having larger probability, shifting cumulated probabilities of all subsequent tokens
                cumProbs = cumProbs.map {
                    Pair(it.first, it.second + curIntRange - cumProbs.last().second)
                }.toMutableList()

                // Stegasuras: "Convert to position in range"
                // Shifts all cumulated probabilities up again by bottom of current interval
                cumProbs = cumProbs.map {
                    Pair(it.first, it.second + curInterval[0])
                }.toMutableList()

                // Stegasuras: "Get selected index based on binary fraction from message bits"
                // Process cipher bits in portions of size precision
                // Unlike Python, Kotlin doesn't handle "cipherBitString.substring(startIndex = i, endIndex = i + precision)" gracefully if i + precision is too large
                var messageBits = if (i + precision < cipherBitString.length) {
                    cipherBitString.substring(startIndex = i, endIndex = i + precision)
                }
                else {
                    cipherBitString.substring(startIndex = i)
                }

                // Append 0s to last cipher bits to make last portion of size precision as well
                if (i + precision > cipherBitString.length) {
                    messageBits += "0".repeat(i + precision - cipherBitString.length)
                }

                // Convert portion of cipher bits to integer for comparison with cumulated probabilities
                // Find position of first token with cumulated probability larger than this integer, i.e. find relevant sub-interval of current interval
                // => sampledToken is already determined here, next steps only calculate new interval
                val messageIdx = Format.asInteger(messageBits)                                 // Stegasuras would reverse messageBits, shouldn't be necessary here
                val selection = cumProbs.indexOfFirst { it.second > messageIdx }

                // Stegasuras: "Calculate new range as ints"
                // Calculate bottom and top of relevant sub-interval for next iteration
                // New bottom (inclusive) is top of preceding sub-interval (exclusive there) if relevant one is not the first one, old bottom otherwise
                // New top (exclusive) is top of relevant sub-interval
                val newIntBottom = if (selection > 0) cumProbs[selection-1].second else curInterval[0]
                val newIntTop = cumProbs[selection].second

                // Stegasuras: "Convert range to bits"
                val newIntBottomBitsInc = Format.asBitString(newIntBottom, precision)          // Again, reversing shouldn't be necessary here
                val newIntTopBitsInc = Format.asBitString(newIntTop - 1, precision)            // Stegasuras: "-1 here because upper bound is exclusive" (i.e. newIntTopBitsInc is inclusive)

                // Stegasuras: "Consume most significant bits which are now fixed and update interval"
                // Arithmetic coding encodes data into a number by iteratively narrowing initial interval defined earlier
                // Therefore most significant bits are fixed first (~ numSameFromBeg), determining the order of magnitude of the number, less significant bits are fixed later
                val numBitsEncoded = numSameFromBeg(newIntBottomBitsInc, newIntTopBitsInc)
                i += numBitsEncoded

                // New interval is determined by setting unfixed bits to 0 for bottom end, to 1 for top end
                // Interval boundaries can jump around because first numBitsEncoded bits are already processed and therefore cut off
                // Next portion of cipher bits in general doesn't narrow the interval
                val newIntBottomBits = newIntBottomBitsInc.substring(startIndex = numBitsEncoded) + "0".repeat(numBitsEncoded)
                val newIntTopBits = newIntTopBitsInc.substring(startIndex = numBitsEncoded) + "1".repeat(numBitsEncoded)

                curInterval[0] = Format.asInteger(newIntBottomBits)                            // Again, reversing shouldn't be necessary here
                curInterval[1] = Format.asInteger(newIntTopBits) + 1                           // Stegasuras: "+1 here because upper bound is exclusive"

                // Sample token as determined above
                sampledToken = cumProbs[selection].first

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
            coverTextTokens += LlamaCpp.getEndOfGeneration()
        }

        val maxVal = 1 shl precision
        val curInterval = intArrayOf(0, maxVal)

        // </Logic specific to arithmetic coding>

        // Initialize string to store cipher bits
        var cipherBitString = ""

        // Initialize variables and flags for loop
        var i = 0

        var isFirstRun = true
        var coverTextToken = -1

        // Decode every cover text token
        while (i < coverTextTokens.size) {
            // <Logic specific to arithmetic coding>

            // End loop when eog token appended to secret message (passed via cover text parameter) above is reached
            if (coverTextTokens[i] == LlamaCpp.getEndOfGeneration()) {
                break
            }

            // </Logic specific to arithmetic coding>

            // Calculate the logit matrix again initially from context tokens, then from last cover text token, and get last row
            val logits = LlamaCpp.getLogits(if (isFirstRun) contextTokens else intArrayOf(coverTextToken)).last()

            // Suppress special tokens
            LlamaCpp.suppressSpecialTokens(logits)

            // <Logic specific to arithmetic coding>

            // Similar to encode
            val probs = Statistics.softmax(logits)

            val probsTemp = probs
                .mapIndexed { token, probability -> token to probability/temperature }
                .sortedByDescending { it.second }

            // Stegasuras: "Cut off low probabilities that would be rounded to 0"
            val curIntRange = curInterval[1] - curInterval[0]
            val curThreshold = 1.0f / curIntRange

            var k = min(
                max(
                    2,
                    probsTemp.filter { it.second >= curThreshold }.size
                ),
                topK
            )

            // Don't reassign "probsTemp = probsTemp.take(k)" but introduce new variable topProbsTemp as decode needs probsTemp again later
            var topProbsTemp = probsTemp.take(k)

            // Stegasuras: "Rescale to correct range"
            var topProbsTempSum = 0.0f

            for ((token, probability) in topProbsTemp) {
                topProbsTempSum += probability
            }

            topProbsTemp = topProbsTemp.map {
                Pair(it.first, it.second / topProbsTempSum * curIntRange)
            }

            // Stegasuras: "Round probabilities to integers given precision"
            val probsTempInt = topProbsTemp.map {
                Pair(it.first, it.second.roundToInt())
            }

            var cumProbs = mutableListOf<Pair<Int, Int>>()
            var cumulatedProbability = 0

            for ((token, probability) in probsTempInt) {
                cumulatedProbability += probability
                cumProbs.add(Pair(token, cumulatedProbability))
            }

            // Stegasuras: "Remove any elements from the bottom if rounding caused the total prob to be too large"
            val overfillIndex = cumProbs.filter { it.second > curIntRange }

            if (overfillIndex.isNotEmpty()) {
                cumProbs = cumProbs.dropLast(overfillIndex.size).toMutableList()
                // Reassignment of k is new in decode, but not used here as possible BPE errors are ignored below
                // Logic of Stegasuras is somewhat inverted again
                // Stegasuras: overfill_index[0] = Index of first token with cumulated probability > cur_int_range
                //             = Number of tokens with cumulated probability <= cur_int_range
                //             = Size of cum_probs after it was overwritten there
                // HiPS: overfillIndex = List of tokens with cumulated probability > curIntRange
                //       != Size of cumProbs after it was overwritten here
                // Now "if (rank >= k) { ... }" from BPE fixes below makes sense
                k = cumProbs.size
            }

            // Stegasuras: "Add any mass to the top if removing/rounding causes the total prob to be too small"
            cumProbs = cumProbs.map {
                Pair(it.first, it.second + curIntRange - cumProbs.last().second)
            }.toMutableList()

            // Stegasuras: "Convert to position in range"
            cumProbs = cumProbs.map {
                Pair(it.first, it.second + curInterval[0])
            }.toMutableList()

            // Stegasuras: n/a
            // Determine rank of predicted token amongst all tokens based on its probability
            var rank = probsTemp.indexOfFirst { it.first == coverTextTokens[i] }

            // Stegasuras: "Handle most errors that could happen because of BPE with heuristic"
            // Rank can't exceed cumProbs indices
            if (rank >= k) {
                // Actual cover text token i
                val trueTokenText = LlamaCpp.detokenize(intArrayOf(coverTextTokens[i]))

                // Python control flow with else outside loop after if inside loop is not possible in Kotlin, introduce flag instead
                var isBpeFixed = false

                // Loop through predicted tokens
                for (rankIdx in 0 until k) {
                    // Predicted cover text token i
                    val propTokenText = LlamaCpp.detokenize(intArrayOf(probsTemp[rankIdx].first))

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
                        coverTextTokens[i] = probsTemp[rankIdx].first

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
            val selection = rank

            // Stegasuras: "Calculate new range as ints"
            val newIntBottom = if (selection > 0) cumProbs[selection-1].second else curInterval[0]
            val newIntTop = cumProbs[selection].second

            // Stegasuras: "Convert range to bits"
            val newIntBottomBitsInc = Format.asBitString(newIntBottom, precision)
            val newIntTopBitsInc = Format.asBitString(newIntTop - 1, precision)

            // Stegasuras: "Emit most significant bits which are now fixed and update interval"
            // Inline += operation to eliminate newBits variable
            val numBitsEncoded = numSameFromBeg(newIntBottomBitsInc, newIntTopBitsInc)

            cipherBitString += if (i == coverTextTokens.size - 1) {
                newIntBottomBitsInc
            }
            else {
                newIntTopBitsInc.substring(startIndex = 0, endIndex = numBitsEncoded)
            }

            val newIntBottomBits = newIntBottomBitsInc.substring(startIndex = numBitsEncoded) + "0".repeat(numBitsEncoded)
            val newIntTopBits = newIntTopBitsInc.substring(startIndex = numBitsEncoded) + "1".repeat(numBitsEncoded)

            curInterval[0] = Format.asInteger(newIntBottomBits)
            curInterval[1] = Format.asInteger(newIntTopBits) + 1

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
    private fun numSameFromBeg(bitString1: String, bitString2: String): Int {
        // Only edge case covered in Stegasuras
        if (bitString1.length != bitString2.length) {
            throw IllegalArgumentException("The bit strings are of different length")
        }

        var numSameFromBeg = 0

        for (i in bitString1.indices) {
            if (bitString1[i] != bitString2[i]) {
                break
            }

            numSameFromBeg++
        }

        return numSameFromBeg
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