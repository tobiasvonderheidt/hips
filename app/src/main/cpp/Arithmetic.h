#ifndef ARITHMETIC_HPP
#define ARITHMETIC_HPP

#include <vector>
#include "llama.h"

/**
 * Class that represents steganography using arithmetic encoding.
 */
class Arithmetic {
public:
    /**
     * Function to determine number of bits that are the same from the beginning of two bit vectors.
     *
     * Corresponds to Stegasuras method `num_same_from_beg` in `utils.py`. Parameter `bits1` was renamed to `bitVector1`, `bits2` to `bitVector2`.
     *
     * @param bitString1 A bit vector.
     * @param bitString2 Another bit vector.
     * @return Number of bits that are the same from the beginning of the bit vectors.
     * @throws std::invalid_argument If the two bit vectors are of different length.
     */
    static int numberOfSameBitsFromBeginning(const std::vector<bool>& bitVector1, const std::vector<bool>& bitVector2);

    /**
     * Function to get the top probability for the last token of the prompt.
     *
     * @param probabilities Probabilities for the last token of the prompt (= last row of logits matrix after normalization).
     * @param model Memory address of the LLM.
     * @return ID of the most likely token.
     */
    static llama_token getTopProbability(float* probabilities, const llama_model* model);
};

#endif
