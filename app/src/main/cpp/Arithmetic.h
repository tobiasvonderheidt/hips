#ifndef ARITHMETIC_HPP
#define ARITHMETIC_HPP

#include <vector>
#include "llama.h"

class Arithmetic {
public:
    static int numberOfSameBitsFromBeginning(const std::vector<bool>& bitVector1, const std::vector<bool>& bitVector2);
    static llama_token getTopProbability(const float* probabilities, const llama_model* model);
};

#endif
