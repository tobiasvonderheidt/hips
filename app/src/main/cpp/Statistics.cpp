#include <cmath>
#include "Statistics.h"

float* Statistics::softmax(float* logits, int32_t vocabSize) {
    // Calculate normalization factor for denominator
    // Uses exponential function since it's strictly monotonous in growth, doesn't change ranking of tokens
    float denominator = 0.0;

    for (int32_t token = 0; token < vocabSize; token++) {
        denominator += exp(logits[token]);
    }

    // Normalize logits to probabilities
    for (int32_t token = 0; token < vocabSize; token++) {
        logits[token] = exp(logits[token]) / denominator;
    }

    return logits;
}
