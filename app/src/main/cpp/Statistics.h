#ifndef STATISTICS_H
#define STATISTICS_H

#include "llama.h"

/**
 * Class that represents statistical functions.
 */
class Statistics {
public:
    /**
     * Function to normalize logits to probabilities.
     *
     * @param logits An array of logits.
     * @param model Memory address of the LLM.
     * @return The array of probabilities.
     */
    static float* softmax(float* logits, const llama_model* model);
};

#endif
