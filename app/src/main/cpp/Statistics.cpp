#include <cmath>
#include "Statistics.h"
#include "LlamaCpp.h"

double* Statistics::softmax(float* logits, const llama_model* model) {
    // Use "new" to allocate memory on heap instead of stack, so probabilities aren't deleted when function exits
    // => Now we need to call the destructor after softmax is used (default array destructor, no need to implement one)
    auto probabilities = new double[LlamaCpp::getVocabSize(model)];

    // Calculate normalization factor for denominator
    // Uses exponential function since it's strictly monotonous in growth, doesn't change ranking of tokens
    double denominator = 0.0;

    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        denominator += exp(logits[token]);
    }

    // Normalize logits to probabilities
    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        probabilities[token] = exp(logits[token]) / denominator;
    }

    return probabilities;
}
