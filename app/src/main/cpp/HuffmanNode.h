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
    float logit = 0.0f;
    HuffmanNode* left = nullptr;
    HuffmanNode* right = nullptr;

    /**
     * Constructor for a Huffman node.
     *
     * Corresponds to Stegasuras method `__init__` of class `HeapNode` in `huffman.py`. Attribute `freq` was renamed to `logit`.
     *
     * @param token A token.
     * @param logit The logit of the token.
     * @param left Pointer to the left child node.
     * @param right Pointer to the right child node.
     */
    HuffmanNode(
        llama_token token,
        float logit,
        HuffmanNode* left,
        HuffmanNode* right
    );
};

#endif
