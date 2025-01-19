package org.vonderheidt.hips.utils

import java.util.PriorityQueue

/**
 * Class that represents the Huffman coding of a set of token IDs based on their logits.
 *
 * Corresponds to Stegasuras class `HuffmanCoding` and its method `__init__` in `huffman.py`. Attribute `heap` was renamed to `huffmanTree`, `codes` to `huffmanCodes`. `reverse_mapping` was dropped.
 *
 * @param huffmanTree A Huffman tree. Initialized as an empty Java PriorityQueue by default.
 * @param huffmanCodes Mapping of token IDs to the corresponding Huffman codes. Initialized as an empty map by default.
 */
class HuffmanCoding(
    private var huffmanTree: PriorityQueue<HuffmanNode> = PriorityQueue(),
    var huffmanCodes: MutableMap<Int, String> = mutableMapOf()
) {
    /**
     * Function to build the Huffman tree, given a map of token IDs and their logits.
     *
     * Corresponds to Stegasuras method `make_heap` of class `HuffmanCoding` in `huffman.py`. Parameter `frequency` was renamed to `tokenLogits`.
     *
     * @param tokenLogits Map of token IDs and their logits.
     */
    fun buildHuffmanTree(tokenLogits: Map<Int, Float>) {
        // Loop through the map
        for ((token, logit) in tokenLogits) {
            // Create a new node for every entry
            val huffmanNode = HuffmanNode(token, logit)

            // Insert it into the Huffman tree
            huffmanTree.offer(huffmanNode)
        }
    }

    /**
     * Function to merge all nodes in a Huffman tree.
     *
     * Corresponds to Stegasuras method `merge_nodes` of class `HuffmanCoding` in `huffman.py`.
     */
    fun mergeHuffmanNodes() {
        // Run merge until only 1 node is left
        while (huffmanTree.size > 1) {
            // Poll two nodes from the Huffman tree
            val left = huffmanTree.poll()
            val right = huffmanTree.poll()

            // Create a new parent node for them, combining their logits
            val mergedHuffmanNode = HuffmanNode(null, left!!.logit + right!!.logit, left, right)

            // Insert the new node into the Huffman tree
            huffmanTree.offer(mergedHuffmanNode)
        }
    }

    /**
     * Function to generate Huffman codes on a given Huffman tree.
     *
     * Corresponds to Stegasuras method `make_codes` of class `HuffmanCoding` in `huffman.py`.
     *
     * @return Root node of the Huffman tree.
     */
    fun generateHuffmanCodes(): HuffmanNode {
        // Poll the Huffman tree once to get the root node
        val root = huffmanTree.poll()

        // Initialize Huffman codes as an empty string
        val currentHuffmanCode = ""

        // Call helper function to traverse Huffman tree and store Huffman code for every node
        generateHuffmanCodesRecursively(root, currentHuffmanCode)

        // Return the root node polled above
        return root!!
    }

    /**
     * Helper function for generateHuffmanCodes. Traverses the Huffman tree recursively and stores the Huffman code for every node.
     *
     * Corresponds to Stegasuras method `make_codes_helper` of class `HuffmanCoding` in `huffman.py`. Parameter `root` was renamed to `currentHuffmanNode`, `current_code` to `currentHuffmanCode`.
     *
     * @param currentHuffmanNode Initially, root node of the Huffman tree. In recursive calls, left and right child nodes of itself.
     * @param currentHuffmanCode Huffman code of the current Huffman node.
     */
    private fun generateHuffmanCodesRecursively(currentHuffmanNode: HuffmanNode?, currentHuffmanCode: String) {
        // If the current node doesn't exist, there is nothing to do (i.e. recursion ends at the bottom of the Huffman tree)
        if (currentHuffmanNode == null) {
            return
        }

        // If the current node exists and has a token (i.e. is not one of the nodes inserted during merging), set its Huffman code to the current Huffman code
        if (currentHuffmanNode.token != null) {
            huffmanCodes[currentHuffmanNode.token] = currentHuffmanCode

            return
        }

        // Traverse left and right subtrees of the current node, appending 0 or 1 respectively to set Huffman codes there
        generateHuffmanCodesRecursively(currentHuffmanNode.left, currentHuffmanCode + "0")
        generateHuffmanCodesRecursively(currentHuffmanNode.right, currentHuffmanCode + "1")
    }
}