#ifndef HUFFMAN_CODING_H
#define HUFFMAN_CODING_H

#include <queue>
#include <unordered_map>
#include <vector>
#include "HuffmanNode.h"

/**
 * Struct to define comparison logic for nodes in a Huffman tree based on their logits.
 */
struct Compare {
    /**
     * Operator to compare nodes in a Huffman tree with each other based on their logits.
     *
     * Corresponds to Stegasuras methods `__lt__` and `__eq__` of class `HeapNode` in `huffman.py`.
     *
     * @param left Pointer to a Huffman node.
     * @param right Pointer to another Huffman node.
     * @return Boolean that is true if the logit of `left` is less than the logit of `right`, false otherwise.
     */
    bool operator()(HuffmanNode* left, HuffmanNode* right) {
        return left->logit < right->logit;
    }
};

/**
 * Class that represents the Huffman coding of a set of tokens based on their logits.
 *
 * Corresponds to Stegasuras class `HuffmanCoding` in `huffman.py`. Attribute `heap` was renamed to `huffmanTree`, `codes` to `huffmanCodes`.
 */
class HuffmanCoding {
private:
    std::priority_queue<HuffmanNode*, std::vector<HuffmanNode*>, Compare> huffmanTree;

    /**
     * Helper function for generateHuffmanCodes. Traverses the Huffman tree recursively and stores the Huffman code for every node.
     *
     * Corresponds to Stegasuras method `make_codes_helper` of class `HuffmanCoding` in `huffman.py`. Parameter `root` was renamed to `currentHuffmanNode`, `current_code` to `currentHuffmanCode`.
     *
     * @param currentHuffmanNode Initially, root node of the Huffman tree. In recursive calls, left and right child nodes of itself.
     * @param currentHuffmanCode Huffman code of the current Huffman node.
     */
    void generateHuffmanCodesRecursively(HuffmanNode* currentHuffmanNode, std::vector<bool> currentHuffmanCode);

    /**
     * Helper function for the destructor. Traverses the Huffman tree recursively to delete left and right child nodes first before deleting the given Huffman node.
     *
     * @param huffmanNode Pointer to a Huffman node to be deleted.
     */
    void deleteHuffmanNode(HuffmanNode* huffmanNode);

public:
    std::unordered_map<llama_token, std::vector<bool>> huffmanCodes;

    /**
     * Constructor for a Huffman coding. Does not take any parameters as attributes `huffmanTree` and `huffmanCodes` are initialized as empty by default.
     */
    HuffmanCoding();

    /**
     * Destructor for a Huffman coding.
     */
    ~HuffmanCoding();

    /**
     * Function to build the Huffman tree, given a mapping of tokens and their logits.
     *
     * Corresponds to Stegasuras method `make_heap` of class `HuffmanCoding` in `huffman.py`. Parameter `frequency` was renamed to `tokenLogits`.
     *
     * @param tokenLogits Mapping of tokens and their logits.
     */
    void buildHuffmanTree(const std::vector<std::pair<llama_token, float>>& tokenLogits);

    /**
     * Function to merge all nodes in a Huffman tree.
     *
     * Corresponds to Stegasuras method `merge_nodes` of class `HuffmanCoding` in `huffman.py`.
     */
    void mergeHuffmanNodes();

    /**
     * Function to generate Huffman codes on a given Huffman tree.
     *
     * Corresponds to Stegasuras method `make_codes` of class `HuffmanCoding` in `huffman.py`.
     *
     * @return Pointer to root node of the Huffman tree.
     */
    HuffmanNode* generateHuffmanCodes();
};

#endif
