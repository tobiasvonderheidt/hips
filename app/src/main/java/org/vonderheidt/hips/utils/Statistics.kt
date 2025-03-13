package org.vonderheidt.hips.utils

import kotlin.math.exp

/**
 * Object (i.e. singleton class) that represents statistical functions.
 */
object Statistics {
    /**
     * Function to normalize logits to probabilities.
     *
     * @param logits A vector of logits.
     * @return The vector of probabilities.
     */
    fun softmax(logits: FloatArray): FloatArray {
        // Initialize probability vector
        val probabilities = FloatArray(logits.size) { 0f }

        // Calculate normalization factor for denominator
        var denominator = 0.0

        for (token in logits.indices) {
            // Uses exponential function since it's strictly monotonous in growth, doesn't change ranking of tokens
            denominator += exp(logits[token].toDouble())
        }

        // Normalize logits and fill probability vector
        for (token in logits.indices) {
            probabilities[token] = (exp(logits[token].toDouble()) / denominator).toFloat()
        }

        return probabilities
    }
}