package org.vonderheidt.hips.utils

import kotlin.math.ln
import kotlin.math.exp

/**
 * Object (i.e. singleton class) to measure naturalness of cover texts.
 *
 * Based on metrics from Ziegler et al. (2019) "Neural Linguistic Steganography"
 * and Shen et al. (2020) "Near-imperceptible Neural Linguistic Steganography".
 */
object SteganographyScoring {

    /**
     * Data class to hold naturalness metrics for a cover text.
     *
     * @param perplexity How surprised the LLM is by the text. Lower = more natural.
     * @param avgLogProbability Average log probability per token. Closer to 0 = more natural.
     * @param minProbability Lowest probability token picked. The weakest link.
     * @param avgRank Average rank of selected tokens. Rank 1 = most likely token.
     * @param maxRank Highest rank picked across all tokens.
     * @param tokenCount Number of tokens in the cover text.
     * @param bitsEncoded Number of secret bits encoded. -1 if not tracked.
     */
    data class NaturalnessScore(
        val perplexity: Double,
        val avgLogProbability: Double,
        val minProbability: Double,
        val avgRank: Double,
        val maxRank: Int,
        val tokenCount: Int,
        val bitsEncoded: Int = -1
    ) {
        // Only meaningful when bitsEncoded is set
        val bitsPerToken: Double
            get() = if (bitsEncoded > 0 && tokenCount > 0) {
                bitsEncoded.toDouble() / tokenCount
            } else {
                -1.0
            }

        /**
         * Overall naturalness score from 0 to 100.
         *
         * Uses perplexity rather than rank, as perplexity uses raw token probabilities
         * and is independent of temperature, allowing fair comparison across candidates
         * generated at different temperatures.
         */
        val overallScore: Double
            get() {
                // perplexity 1 -> 100, perplexity 5 -> 79, perplexity 10 -> 70,
                // perplexity 50 -> 49, perplexity 100 -> 40
                val perplexityScore = maxOf(0.0, 100.0 - 13.0 * ln(perplexity))

                // Punish longer cover texts
                val lengthPenalty = tokenCount * 0.2

                return maxOf(0.0, perplexityScore - lengthPenalty)
            }

        val rating: String
            get() = when {
                overallScore >= 80 -> "Excellent"
                overallScore >= 65 -> "Good"
                overallScore >= 50 -> "Fair"
                overallScore >= 35 -> "Poor"
                else -> "Bad"
            }

        /**
         * Function to return a formatted string with all metrics.
         *
         * @return Formatted string with all metrics.
         */
        override fun toString(): String {
            val sb = StringBuilder()
            sb.appendLine("=== Naturalness Score: ${String.format("%.1f", overallScore)}/100 ($rating) ===")
            sb.appendLine("Perplexity:       ${String.format("%.2f", perplexity)} (lower is better)")
            sb.appendLine("Avg Log Prob:     ${String.format("%.4f", avgLogProbability)}")
            sb.appendLine("Min Probability:  ${String.format("%.6f", minProbability)}")
            sb.appendLine("Avg Rank:         ${String.format("%.2f", avgRank)} (1 is best)")
            sb.appendLine("Max Rank:         $maxRank")
            sb.appendLine("Token Count:      $tokenCount")
            if (bitsEncoded > 0) {
                sb.appendLine("Bits Encoded:     $bitsEncoded")
                sb.appendLine("Bits/Token:       ${String.format("%.2f", bitsPerToken)}")
            }
            return sb.toString()
        }
    }

    /**
     * Function to score a cover text by running it through the LLM.
     *
     * Re-runs the model token by token to check how likely each token was, i.e. slower
     * than computeScore() but useful when stats from encoding time are not available.
     *
     * @param context The context used to generate the cover text.
     * @param coverText The cover text to score.
     * @return NaturalnessScore with all computed metrics.
     */
    fun scoreText(context: String, coverText: String): NaturalnessScore {
        if (coverText.isEmpty()) {
            return NaturalnessScore(
                perplexity = Double.MAX_VALUE,
                avgLogProbability = Double.NEGATIVE_INFINITY,
                minProbability = 0.0,
                avgRank = Double.MAX_VALUE,
                maxRank = Int.MAX_VALUE,
                tokenCount = 0
            )
        }

        // Tokenise context and cover text
        val contextTokens = LlamaCpp.tokenize(context)
        val coverTextTokens = LlamaCpp.tokenize(coverText)

        if (coverTextTokens.isEmpty()) {
            return NaturalnessScore(
                perplexity = Double.MAX_VALUE,
                avgLogProbability = Double.NEGATIVE_INFINITY,
                minProbability = 0.0,
                avgRank = Double.MAX_VALUE,
                maxRank = Int.MAX_VALUE,
                tokenCount = 0
            )
        }

        // Collect probability and rank for each token
        val probabilities = mutableListOf<Double>()
        val ranks = mutableListOf<Int>()

        var isFirstRun = true
        var lastToken = -1

        for (i in coverTextTokens.indices) {
            val actualToken = coverTextTokens[i]

            // Get LLM predictions for current position
            val logits = if (isFirstRun) {
                LlamaCpp.getLogits(contextTokens).last()
            } else {
                LlamaCpp.getLogits(intArrayOf(lastToken)).last()
            }

            // Convert logits to probabilities
            val probs = Statistics.softmax(logits)

            // Suppress special tokens and renormalise, same as during encoding
            LlamaCpp.suppressSpecialTokens(probs)

            val sum = probs.sum()
            if (sum > 0) {
                for (j in probs.indices) {
                    probs[j] /= sum
                }
            }

            // Clamp to avoid log(0) when computing perplexity
            val tokenProb = probs[actualToken].toDouble().coerceAtLeast(1e-10)
            probabilities.add(tokenProb)

            // Compute rank (1-indexed)
            val sortedIndices = probs.indices.sortedByDescending { probs[it] }
            val rank = sortedIndices.indexOf(actualToken) + 1
            ranks.add(rank)

            isFirstRun = false
            lastToken = actualToken
        }

        // Compute metrics
        val n = probabilities.size

        // Perplexity = exp(-1/N * sum(log(p)))
        val sumLogProb = probabilities.sumOf { ln(it) }
        val avgLogProb = sumLogProb / n
        val perplexity = exp(-avgLogProb)

        val minProb = probabilities.minOrNull() ?: 0.0
        val avgRank = ranks.average()
        val maxRank = ranks.maxOrNull() ?: 0

        return NaturalnessScore(
            perplexity = perplexity,
            avgLogProbability = avgLogProb,
            minProbability = minProb,
            avgRank = avgRank,
            maxRank = maxRank,
            tokenCount = n
        )
    }

    /**
     * Function to compute naturalness score from data collected during encoding.
     *
     * Faster than scoreText() as it avoids re-running the LLM. Call this at the end
     * of Arithmetic.encode() after collecting per-token stats.
     *
     * @param tokenProbabilities List of probabilities for each selected token.
     * @param tokenRanks List of ranks for each selected token (1-indexed).
     * @param bitsEncoded Total number of secret bits encoded.
     * @return NaturalnessScore with all computed metrics.
     */
    fun computeScore(
        tokenProbabilities: List<Float>,
        tokenRanks: List<Int>,
        bitsEncoded: Int = -1
    ): NaturalnessScore {
        if (tokenProbabilities.isEmpty()) {
            return NaturalnessScore(
                perplexity = Double.MAX_VALUE,
                avgLogProbability = Double.NEGATIVE_INFINITY,
                minProbability = 0.0,
                avgRank = Double.MAX_VALUE,
                maxRank = Int.MAX_VALUE,
                tokenCount = 0,
                bitsEncoded = bitsEncoded
            )
        }

        val n = tokenProbabilities.size

        // Clamp to avoid log(0) when computing perplexity
        val probs = tokenProbabilities.map { it.toDouble().coerceAtLeast(1e-10) }

        // Perplexity = exp(-1/N * sum(log(p)))
        val sumLogProb = probs.sumOf { ln(it) }
        val avgLogProb = sumLogProb / n
        val perplexity = exp(-avgLogProb)

        val minProb = probs.minOrNull() ?: 0.0
        val avgRank = tokenRanks.average()
        val maxRank = tokenRanks.maxOrNull() ?: 0

        return NaturalnessScore(
            perplexity = perplexity,
            avgLogProbability = avgLogProb,
            minProbability = minProb,
            avgRank = avgRank,
            maxRank = maxRank,
            tokenCount = n,
            bitsEncoded = bitsEncoded
        )
    }

    /**
     * Function to compare two cover texts.
     *
     * @param context The shared context used for both cover texts.
     * @param coverText1 First cover text to compare.
     * @param coverText2 Second cover text to compare.
     * @return Pair of naturalness scores for each cover text.
     */
    fun compare(context: String, coverText1: String, coverText2: String): Pair<NaturalnessScore, NaturalnessScore> {
        LlamaCpp.resetInstance()
        val score1 = scoreText(context, coverText1)

        LlamaCpp.resetInstance()
        val score2 = scoreText(context, coverText2)

        return Pair(score1, score2)
    }

    /**
     * Function to find the rank of a token in a probability distribution.
     *
     * @param tokenId The token to find.
     * @param sortedProbabilities List of (tokenId, probability) pairs sorted descending.
     * @return 1-indexed rank, or -1 if not found.
     */
    fun findRank(tokenId: Int, sortedProbabilities: List<Pair<Int, Float>>): Int {
        for ((index, pair) in sortedProbabilities.withIndex()) {
            if (pair.first == tokenId) {
                return index + 1
            }
        }
        return -1
    }
}