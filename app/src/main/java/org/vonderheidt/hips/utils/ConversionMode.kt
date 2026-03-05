package org.vonderheidt.hips.utils

/**
 * Class to enumerate all conversion modes.
 *
 * @param displayName Display name of the conversion mode.
 */
enum class ConversionMode(private val displayName: String) {
    Arithmetic("Arithmetic (recommended)"),
    /* Huffman("Huffman"), */
    UTF8("UTF-8");

    /**
     * Function to get the display name of the conversion mode.
     *
     * @return Display name of the conversion mode.
     */
    override fun toString(): String {
        return displayName
    }
}