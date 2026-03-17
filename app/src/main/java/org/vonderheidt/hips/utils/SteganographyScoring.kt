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
        val bitsPerToken: Double
            get() = if (bitsEncoded > 0 && tokenCount > 0) {
                bitsEncoded.toDouble() / tokenCount
            } else {
                -1.0
            }

        val overallScore: Double
            get() {
                val perplexityScore = maxOf(0.0, 100.0 - 13.0 * ln(perplexity))
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
     * FIXED: Now correctly increments nPast to avoid KV cache conflicts that caused llama_decode error -1.
     */
    fun scoreText(context: String, coverText: String): NaturalnessScore {
        if (coverText.isEmpty()) {
            return NaturalnessScore(Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, Double.MAX_VALUE, Int.MAX_VALUE, 0)
        }

        val contextTokens = LlamaCpp.tokenize(context)
        val coverTextTokens = LlamaCpp.tokenize(coverText)

        if (coverTextTokens.isEmpty()) {
            return NaturalnessScore(Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, Double.MAX_VALUE, Int.MAX_VALUE, 0)
        }

        val probabilities = mutableListOf<Double>()
        val ranks = mutableListOf<Int>()

        var isFirstRun = true
        var currentTokenArray = contextTokens
        var nPast = 0

        for (i in coverTextTokens.indices) {
            val actualToken = coverTextTokens[i]

            // FIXED: Passing correct nPast to getLogits
            val logits = LlamaCpp.getLogits(currentTokenArray, nPast).last()
            
            val probs = Statistics.softmax(logits)
            LlamaCpp.suppressSpecialTokens(probs)

            val sum = probs.sum()
            if (sum > 0) {
                for (j in probs.indices) probs[j] /= sum
            }

            val tokenProb = probs[actualToken].toDouble().coerceAtLeast(1e-10)
            probabilities.add(tokenProb)

            val sortedIndices = probs.indices.sortedByDescending { probs[it] }
            val rank = sortedIndices.indexOf(actualToken) + 1
            ranks.add(rank)

            // Update state for next iteration
            nPast += currentTokenArray.size
            currentTokenArray = intArrayOf(actualToken)
            isFirstRun = false
        }

        val n = probabilities.size
        val sumLogProb = probabilities.sumOf { ln(it) }
        val avgLogProb = sumLogProb / n
        val perplexity = exp(-avgLogProb)

        return NaturalnessScore(
            perplexity = perplexity,
            avgLogProbability = avgLogProb,
            minProbability = probabilities.minOrNull() ?: 0.0,
            avgRank = ranks.average(),
            maxRank = ranks.maxOrNull() ?: 0,
            tokenCount = n
        )
    }

    fun computeScore(
        tokenProbabilities: List<Float>,
        tokenRanks: List<Int>,
        bitsEncoded: Int = -1
    ): NaturalnessScore {
        if (tokenProbabilities.isEmpty()) {
            return NaturalnessScore(Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, Double.MAX_VALUE, Int.MAX_VALUE, 0, bitsEncoded)
        }

        val n = tokenProbabilities.size
        val probs = tokenProbabilities.map { it.toDouble().coerceAtLeast(1e-10) }
        val sumLogProb = probs.sumOf { ln(it) }
        val avgLogProb = sumLogProb / n
        val perplexity = exp(-avgLogProb)

        return NaturalnessScore(
            perplexity = perplexity,
            avgLogProbability = avgLogProb,
            minProbability = probs.minOrNull() ?: 0.0,
            avgRank = tokenRanks.average(),
            maxRank = tokenRanks.maxOrNull() ?: 0,
            tokenCount = n,
            bitsEncoded = bitsEncoded
        )
    }

    fun compare(context: String, coverText1: String, coverText2: String): Pair<NaturalnessScore, NaturalnessScore> {
        LlamaCpp.resetInstance()
        val score1 = scoreText(context, coverText1)
        LlamaCpp.resetInstance()
        val score2 = scoreText(context, coverText2)
        return Pair(score1, score2)
    }

    fun findRank(tokenId: Int, sortedProbabilities: List<Pair<Int, Float>>): Int {
        for ((index, pair) in sortedProbabilities.withIndex()) {
            if (pair.first == tokenId) return index + 1
        }
        return -1
    }
}