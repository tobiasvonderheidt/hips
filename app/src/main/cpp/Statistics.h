#ifndef STATISTICS_H
#define STATISTICS_H

#include <cstdint>
#include "llama.h"

/**
 * Class that represents statistical functions.
 */
class Statistics {
public:
    /**
     * Function to calculate the LogSumExp of an array of logits.
     * 
     * @param logits An array of logits.
     * @param n_vocab Size of the vocabulary.
     * @return The LogSumExp.
     */
    static float logSumExp(const float* logits, int32_t n_vocab);

    /**
     * Function to normalize logits to probabilities.
     *
     * @param logits An array of logits.
     * @param model Memory address of the LLM.
     * @return The array of probabilities.
     */
    static float* softmax(float* logits, const llama_model* model);

    /**
     * Function to calculate the Shannon entropy of a probability distribution.
     *
     * @param probabilities A probability distribution.
     * @param n_vocab Size of the distribution.
     * @return The Shannon entropy in bits.
     */
    static float calculateEntropy(const float* probabilities, int32_t n_vocab);
};

#endif
