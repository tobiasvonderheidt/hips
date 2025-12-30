#include "HuffmanCoding.h"

HuffmanCoding::HuffmanCoding() { }

void HuffmanCoding::buildHuffmanTree(const std::vector<std::pair<llama_token, float>>& tokenLogits) {
    // Loop through the mapping
    for (const auto& [token, logit] : tokenLogits) {
        // Create a new node for every entry
        auto huffmanNode = new HuffmanNode(token, logit, nullptr, nullptr);

        // Insert it into the Huffman tree
        huffmanTree.push(huffmanNode);
    }
}

void HuffmanCoding::mergeHuffmanNodes() {
    // Run merge until only 1 node is left
    while (huffmanTree.size() > 1) {
        // Poll two nodes from the Huffman tree
        HuffmanNode* left = huffmanTree.top();
        huffmanTree.pop();

        HuffmanNode* right = huffmanTree.top();
        huffmanTree.pop();

        // Create a new parent node for them, combining their logits
        auto mergedHuffmanNode = new HuffmanNode(-1, left->logit + right->logit, left, right);

        // Insert the new node into the Huffman tree
        huffmanTree.push(mergedHuffmanNode);
    }
}

HuffmanNode* HuffmanCoding::generateHuffmanCodes() {
    // Poll the Huffman tree once to get the root node
    HuffmanNode* root = huffmanTree.top();
    huffmanTree.pop();

    // Initialize Huffman codes as an empty bit vector
    std::vector<bool> currentHuffmanCode;

    // Call helper function to traverse Huffman tree and store Huffman code for every node
    generateHuffmanCodesRecursively(root, currentHuffmanCode);

    // Return the root node polled above
    return root;
}

void HuffmanCoding::generateHuffmanCodesRecursively(HuffmanNode* currentHuffmanNode, std::vector<bool> currentHuffmanCode) {
    // If the current node doesn't exist, there is nothing to do (i.e. recursion ends at the bottom of the Huffman tree)
    if (currentHuffmanNode == nullptr) {
        return;
    }

    // If the current node exists and has an element (i.e. is not one of the nodes inserted during merging), set its Huffman code to the current Huffman code
    if (currentHuffmanNode->token != -1) {
        huffmanCodes[currentHuffmanNode->token] = currentHuffmanCode;

        return;
    }

    // Traverse left and right subtrees of the current node, appending 0 or 1 respectively to set Huffman codes there
    currentHuffmanCode.push_back(false); // Append 0
    generateHuffmanCodesRecursively(currentHuffmanNode->left, currentHuffmanCode);
    currentHuffmanCode.pop_back();

    currentHuffmanCode.push_back(true); // Append 1
    generateHuffmanCodesRecursively(currentHuffmanNode->right, currentHuffmanCode);
    currentHuffmanCode.pop_back();
}
