package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) to benchmark steganography quality.
 */
object SteganographyBenchmark {

    /**
     * Data class to hold results from a single benchmark run.
     *
     * @param context The context used for encoding.
     * @param secretMessage The secret message that was encoded.
     * @param coverText The generated cover text.
     * @param score The naturalness score for this cover text.
     * @param encodingTimeMs How long encoding took in milliseconds.
     */
    data class BenchmarkResult(
        val context: String,
        val secretMessage: String,
        val coverText: String,
        val score: SteganographyScoring.NaturalnessScore,
        val encodingTimeMs: Long
    ) {
        /**
         * Function to return a formatted string with all metrics.
         *
         * @return Formatted string with all metrics.
         */
        override fun toString(): String {
            val sb = StringBuilder()
            sb.appendLine("--- Benchmark Result ---")
            sb.appendLine("Context: \"${context.take(50)}${if (context.length > 50) "..." else ""}\"")
            sb.appendLine("Secret:  \"$secretMessage\"")
            sb.appendLine("Cover:   \"${coverText.take(80)}${if (coverText.length > 80) "..." else ""}\"")
            sb.appendLine("Time:    ${encodingTimeMs}ms")
            sb.appendLine(score.toString())
            return sb.toString()
        }
    }

    /**
     * Data class to hold aggregated results from multiple benchmark runs.
     *
     * @param results List of individual benchmark results.
     * @param avgPerplexity Average perplexity across all runs.
     * @param avgScore Average overall naturalness score (0-100).
     * @param avgEncodingTimeMs Average encoding time in milliseconds.
     * @param worstResult The result with the lowest naturalness score.
     * @param bestResult The result with the highest naturalness score.
     */
    data class BenchmarkSummary(
        val results: List<BenchmarkResult>,
        val avgPerplexity: Double,
        val avgScore: Double,
        val avgEncodingTimeMs: Long,
        val worstResult: BenchmarkResult?,
        val bestResult: BenchmarkResult?
    ) {
        val rating: String
            get() = when {
                avgScore >= 80 -> "Excellent"
                avgScore >= 65 -> "Good"
                avgScore >= 50 -> "Fair"
                avgScore >= 35 -> "Poor"
                else -> "Bad"
            }

        /**
         * Function to return a formatted string with all metrics.
         *
         * @return Formatted string with all metrics.
         */
        override fun toString(): String {
            val sb = StringBuilder()
            sb.appendLine("========== BENCHMARK SUMMARY ==========")
            sb.appendLine("Total runs:        ${results.size}")
            sb.appendLine("Average score:     ${String.format("%.1f", avgScore)}/100 ($rating)")
            sb.appendLine("Average perplexity: ${String.format("%.2f", avgPerplexity)}")
            sb.appendLine("Average time:      ${avgEncodingTimeMs}ms")
            sb.appendLine()
            if (bestResult != null) {
                sb.appendLine("BEST result (score ${String.format("%.1f", bestResult.score.overallScore)}):")
                sb.appendLine("  Secret: \"${bestResult.secretMessage}\"")
                sb.appendLine("  Cover:  \"${bestResult.coverText.take(60)}...\"")
            }
            if (worstResult != null) {
                sb.appendLine()
                sb.appendLine("WORST result (score ${String.format("%.1f", worstResult.score.overallScore)}):")
                sb.appendLine("  Secret: \"${worstResult.secretMessage}\"")
                sb.appendLine("  Cover:  \"${worstResult.coverText.take(60)}...\"")
            }
            sb.appendLine("========================================")
            return sb.toString()
        }
    }

    // Default test contexts — casual conversation messages
    private val defaultContexts = listOf(
        "Hey, how was your weekend?",
        "Did you see the news today?",
        "What are you up to later?",
        "I was thinking about getting lunch.",
        "Have you watched any good movies lately?"
    )

    // Default test secret messages — short phrases of varying lengths
    private val defaultSecretMessages = listOf(
        "hi",
        "yes",
        "meet at 5",
        "call me",
        "ok sounds good",
        "see you tomorrow"
    )

    /**
     * Function to run a single benchmark test.
     *
     * @param context The context to use for encoding.
     * @param secretMessage The secret message to encode.
     * @return BenchmarkResult with the cover text and its naturalness score.
     */
    suspend fun runSingle(context: String, secretMessage: String): BenchmarkResult {
        // Reset LLM state
        LlamaCpp.resetInstance()

        // Measure encoding time
        val startTime = System.currentTimeMillis()

        // Encode the secret message
        val coverTexts = Steganography.encode(context, secretMessage)
        val coverText = coverTexts.joinToString(" ")

        val encodingTime = System.currentTimeMillis() - startTime

        // Score the cover text
        LlamaCpp.resetInstance()
        val score = SteganographyScoring.scoreText(context, coverText)

        return BenchmarkResult(
            context = context,
            secretMessage = secretMessage,
            coverText = coverText,
            score = score,
            encodingTimeMs = encodingTime
        )
    }

    /**
     * Function to run the full benchmark with default test cases.
     *
     * @return BenchmarkSummary with averaged metrics and best/worst results.
     */
    suspend fun runAll(): BenchmarkSummary {
        val results = mutableListOf<BenchmarkResult>()

        for (context in defaultContexts) {
            for (secretMessage in defaultSecretMessages) {
                val result = runSingle(context, secretMessage)
                results.add(result)
            }
        }

        return summarize(results)
    }

    /**
     * Function to run the benchmark with custom test cases.
     *
     * @param testCases List of (context, secretMessage) pairs to test.
     * @return BenchmarkSummary with averaged metrics.
     */
    suspend fun runCustom(testCases: List<Pair<String, String>>): BenchmarkSummary {
        val results = mutableListOf<BenchmarkResult>()

        for ((context, secretMessage) in testCases) {
            val result = runSingle(context, secretMessage)
            results.add(result)
        }

        return summarize(results)
    }

    /**
     * Function to run a quick benchmark with a subset of test cases.
     *
     * @return BenchmarkSummary with averaged metrics.
     */
    suspend fun runQuick(): BenchmarkSummary {
        val quickTests = listOf(
            Pair("Hey, what's up?", "hi"),
            Pair("How was your day?", "meet at 5"),
            Pair("Any plans for tonight?", "call me")
        )

        return runCustom(quickTests)
    }

    /**
     * Function to compare two steganography modes by running benchmarks on both.
     *
     * @param context The context to use for both tests.
     * @param secretMessage The secret message to encode.
     * @param mode1 First steganography mode to test.
     * @param mode2 Second steganography mode to test.
     * @return Pair of (result1, result2) for comparison.
     */
    suspend fun compareModes(
        context: String,
        secretMessage: String,
        mode1: SteganographyMode,
        mode2: SteganographyMode
    ): Pair<BenchmarkResult, BenchmarkResult> {
        // Save current mode
        val originalMode = Settings.steganographyMode

        // Test mode 1
        Settings.steganographyMode = mode1
        val result1 = runSingle(context, secretMessage)

        // Test mode 2
        Settings.steganographyMode = mode2
        val result2 = runSingle(context, secretMessage)

        // Restore original mode
        Settings.steganographyMode = originalMode

        return Pair(result1, result2)
    }

    /**
     * Function to aggregate a list of benchmark results into a summary.
     *
     * @param results List of individual benchmark results.
     * @return BenchmarkSummary with averaged metrics.
     */
    private fun summarize(results: List<BenchmarkResult>): BenchmarkSummary {
        if (results.isEmpty()) {
            return BenchmarkSummary(
                results = emptyList(),
                avgPerplexity = 0.0,
                avgScore = 0.0,
                avgEncodingTimeMs = 0,
                worstResult = null,
                bestResult = null
            )
        }

        val avgPerplexity = results.map { it.score.perplexity }.average()
        val avgScore = results.map { it.score.overallScore }.average()
        val avgTime = results.map { it.encodingTimeMs }.average().toLong()

        val bestResult = results.maxByOrNull { it.score.overallScore }
        val worstResult = results.minByOrNull { it.score.overallScore }

        return BenchmarkSummary(
            results = results,
            avgPerplexity = avgPerplexity,
            avgScore = avgScore,
            avgEncodingTimeMs = avgTime,
            worstResult = worstResult,
            bestResult = bestResult
        )
    }

    /**
     * Function to print a detailed report of all results.
     *
     * @param summary The benchmark summary to print.
     */
    fun printDetailedReport(summary: BenchmarkSummary) {
        println(summary.toString())
        println()
        println("========== DETAILED RESULTS ==========")
        for ((index, result) in summary.results.withIndex()) {
            println("--- Test ${index + 1} ---")
            println(result.toString())
        }
    }
}