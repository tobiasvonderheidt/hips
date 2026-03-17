#include <cmath>
#include <algorithm>
#include <vector>
#include "Statistics.h"
#include "LlamaCpp.h"

float Statistics::logSumExp(const float* logits, int32_t n_vocab) {
    float max_logit = -INFINITY;
    for (int32_t i = 0; i < n_vocab; ++i) {
        if (logits[i] > max_logit) max_logit = logits[i];
    }
    
    if (max_logit == -INFINITY) return -INFINITY;

    double sum = 0.0;
    for (int32_t i = 0; i < n_vocab; ++i) {
        sum += exp(logits[i] - max_logit);
    }
    
    return max_logit + static_cast<float>(log(sum));
}

float* Statistics::softmax(float* logits, const llama_model* model) {
    int32_t n_vocab = LlamaCpp::getVocabSize(model);
    float lse = logSumExp(logits, n_vocab);

    // Normalize logits to probabilities in a numerically stable way
    for (int32_t token = 0; token < n_vocab; token++) {
        logits[token] = exp(logits[token] - lse);
    }

    return logits;
}

float Statistics::calculateEntropy(const float* probabilities, int32_t n_vocab) {
    double entropy = 0.0;
    for (int32_t i = 0; i < n_vocab; ++i) {
        if (probabilities[i] > 1e-12) { // Avoid log(0)
            entropy -= static_cast<double>(probabilities[i]) * (log(static_cast<double>(probabilities[i])) / log(2.0));
        }
    }
    return static_cast<float>(entropy);
}
