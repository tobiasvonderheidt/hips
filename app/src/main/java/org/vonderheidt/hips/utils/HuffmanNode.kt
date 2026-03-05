package org.vonderheidt.hips.utils

/**
 * Class that represents a node in a Huffman tree. Implements the Kotlin Comparable interface as the Huffman tree is a Java PriorityQueue.
 *
 * Corresponds to Stegasuras class `HeapNode` and its method `__init__` in `huffman.py`. Attribute `token` was renamed to `element`, `freq` to `frequency`.
 *
 * @param element An element.
 * @param frequency The frequency of the element.
 * @param left Left child node.
 * @param right Right child node.
 */
class HuffmanNode<Element, Frequency : Number>(
    val element: Element?,
    val frequency: Frequency,
    val left: HuffmanNode<Element, Frequency>? = null,
    val right: HuffmanNode<Element, Frequency>? = null
) : Comparable<HuffmanNode<Element, Frequency>> {
    /**
     * Function to compare nodes in a Huffman tree with each other based on their frequencies.
     *
     * Corresponds to Stegasuras methods `__lt__` and `__eq__` of class `HeapNode` in `huffman.py`.
     *
     * @param other Node to be compared to `this` node.
     * @return -1, 0 or 1 depending on whether `this.frequency` is <, == or > `other.frequency`.
     */
    override fun compareTo(other: HuffmanNode<Element, Frequency>): Int {
        // Doesn't strictly have to return -1, 0 or 1
        // Negative value corresponds to <, zero corresponds to ==, positive value corresponds to >

        // Cast to Double (wrapper class of double) because it is large enough to store any number and is comparable
        return this.frequency.toDouble().compareTo(other.frequency.toDouble())
    }
}