package org.vonderheidt.hips.utils

/**
 * Class that represents a node in a Huffman tree. Implements the Kotlin Comparable interface as the Huffman tree is a Java PriorityQueue.
 *
 * Corresponds to Stegasuras class `HeapNode` and its method `__init__` in `huffman.py`. Attribute `freq` (frequency) was renamed to `logit` to match attribute `token`.
 *
 * @param token Token ID.
 * @param logit Token logit.
 * @param left Left child node.
 * @param right Right child node.
 */
class HuffmanNode(
    val token: Int?,
    val logit: Float,
    val left: HuffmanNode? = null,
    val right: HuffmanNode? = null
) : Comparable<HuffmanNode> {
    /**
     * Function to compare nodes in a Huffman tree with each other based on their logits.
     *
     * Corresponds to Stegasuras methods `__lt__` and `__eq__` of class `HeapNode` in `huffman.py`.
     *
     * @param other Node to be compared to `this` node.
     * @return -1, 0 or 1 depending on whether `this.logit` is <, == or > `other.logit`.
     */
    override fun compareTo(other: HuffmanNode): Int {
        // Doesn't strictly have to return -1, 0 or 1
        // Negative value corresponds to <, zero corresponds to ==, positive value corresponds to >
        return this.logit.compareTo(other.logit)
    }
}