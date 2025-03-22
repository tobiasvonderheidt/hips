package org.vonderheidt.hips.utils

import java.util.PriorityQueue

/**
 * Class that represents the Huffman coding of a set of elements based on their frequencies.
 *
 * Corresponds to Stegasuras class `HuffmanCoding` and its method `__init__` in `huffman.py`. Attribute `heap` was renamed to `huffmanTree`, `codes` to `huffmanCodes` and `reverse_mapping` to `inverseHuffmanCodes`.
 *
 * @param huffmanTree A Huffman tree. Initialized as an empty Java PriorityQueue by default.
 * @param huffmanCodes Mapping of elements to the corresponding Huffman codes. Initialized as an empty map by default.
 * @param inverseHuffmanCodes Inverse mapping of Huffman codes to the corresponding elements. Initialized as an empty map by default.
 */
class HuffmanCoding<Element, Frequency : Number>(
    private var huffmanTree: PriorityQueue<HuffmanNode<Element, Frequency>> = PriorityQueue(),
    var huffmanCodes: MutableMap<Element, String> = mutableMapOf(),
    var inverseHuffmanCodes: MutableMap<String, Element> = mutableMapOf()
) {
    /**
     * Function to count the frequency of each character in a given string.
     *
     * Corresponds to method `make_frequency_dict` in huffman.py of [github.com/bhrigu123/huffman-coding](https://github.com/bhrigu123/huffman-coding), which Stegasuras references.
     * Only needed for binary conversion, therefore not generic.
     *
     * @param string A string whose characters are to be counted.
     * @return A map of each character and its frequency in the string.
     */
    fun countCharFrequencies(string: String): MutableMap<Char, Int> {
        val charFrequencies = mutableMapOf<Char, Int>()

        for (char in string) {
            if (char !in charFrequencies) {
                charFrequencies[char] = 0
            }

            charFrequencies[char] = charFrequencies[char]!! + 1
        }

        return charFrequencies
    }

    /**
     * Function to build the Huffman tree, given a map of elements and their frequencies.
     *
     * Corresponds to Stegasuras method `make_heap` of class `HuffmanCoding` in `huffman.py`. Parameter `frequency` was renamed to `elementFrequencies`.
     *
     * @param elementFrequencies Map of elements and their frequencies.
     */
    fun buildHuffmanTree(elementFrequencies: Map<Element, Frequency>) {
        // Loop through the map
        for ((element, frequency) in elementFrequencies) {
            // Create a new node for every entry
            val huffmanNode = HuffmanNode(element, frequency)

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

            // Create a new parent node for them, combining their frequencies
            val mergedHuffmanNode = HuffmanNode(null, (left!!.frequency.toDouble() + right!!.frequency.toDouble()) as Frequency, left, right)

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
    fun generateHuffmanCodes(): HuffmanNode<Element, Frequency> {
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
    private fun generateHuffmanCodesRecursively(currentHuffmanNode: HuffmanNode<Element, Frequency>?, currentHuffmanCode: String) {
        // If the current node doesn't exist, there is nothing to do (i.e. recursion ends at the bottom of the Huffman tree)
        if (currentHuffmanNode == null) {
            return
        }

        // If the current node exists and has an element (i.e. is not one of the nodes inserted during merging), set its Huffman code to the current Huffman code
        if (currentHuffmanNode.element != null) {
            huffmanCodes[currentHuffmanNode.element] = currentHuffmanCode
            inverseHuffmanCodes[currentHuffmanCode] = currentHuffmanNode.element

            return
        }

        // Traverse left and right subtrees of the current node, appending 0 or 1 respectively to set Huffman codes there
        generateHuffmanCodesRecursively(currentHuffmanNode.left, currentHuffmanCode + "0")
        generateHuffmanCodesRecursively(currentHuffmanNode.right, currentHuffmanCode + "1")
    }

    /**
     * Function to compress a string using Huffman encoding.
     *
     * Corresponds to Stegasuras method `get_encoded_tokens` of class `HuffmanCoding` in `huffman.py`. Parameter `token_list` was renamed to `string`.
     * Only needed for binary conversion, therefore not generic.
     *
     * @param string A string to be compressed.
     * @return Huffman encoding of the string.
     */
    fun compress(string: String): String {
        var bitString = ""

        for (char in string) {
            bitString += huffmanCodes[char as Element]
        }

        return bitString
    }

    /**
     * Function to decompress a bit string using Huffman decoding.
     *
     * Corresponds to Stegasuras method `decode_text` (not `decompress`) of class `HuffmanCoding` in `huffman.py`. Parameter `encoded_text` was renamed to `bitString`.
     * Only needed for binary conversion, therefore not generic.
     *
     * @param bitString A bit string compressed with Huffman encoding.
     * @return Huffman decoding of the bit string.
     */
    fun decompress(bitString: String): String {
        var currentHoffmanCode = ""
        var string = ""

        for (bit in bitString) {
            currentHoffmanCode += bit

            if (currentHoffmanCode in inverseHuffmanCodes) {
                val char = inverseHuffmanCodes[currentHoffmanCode]
                string += char
                currentHoffmanCode = ""
            }
        }

        return string
    }
}