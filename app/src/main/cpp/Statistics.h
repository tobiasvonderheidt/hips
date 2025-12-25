#ifndef STATISTICS_H
#define STATISTICS_H

/**
 * Class that represents statistical functions.
 */
class Statistics {
public:
    /**
     * Function to normalize logits to probabilities.
     *
     * @param logits An array of logits.
     * @param vocabSize Vocabulary size of the LLM.
     * @return The array of probabilities.
     */
    static float* softmax(float* logits, int32_t vocabSize);
};

#endif
