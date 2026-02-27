#ifndef HUFFMAN_NODE_H
#define HUFFMAN_NODE_H

#include "llama.h"

/**
 * Class that represents a node in a Huffman tree.
 *
 * Corresponds to Stegasuras class `HeapNode` in `huffman.py`.
 */
class HuffmanNode {
public:
    llama_token token = -1;
    double probability = 0.0;
    HuffmanNode* left = nullptr;
    HuffmanNode* right = nullptr;

    /**
     * Constructor for a Huffman node.
     *
     * Corresponds to Stegasuras method `__init__` of class `HeapNode` in `huffman.py`. Attribute `freq` was renamed to `probability`.
     *
     * @param token A token.
     * @param probability The probability of the token.
     * @param left Pointer to the left child node.
     * @param right Pointer to the right child node.
     */
    HuffmanNode(
        llama_token token,
        double probability,
        HuffmanNode* left,
        HuffmanNode* right
    );
};

#endif
