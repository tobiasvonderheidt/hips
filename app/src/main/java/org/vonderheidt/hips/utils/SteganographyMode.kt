package org.vonderheidt.hips.utils

/**
 * Class to enumerate all steganography modes.
 *
 * @param displayName Display name of the steganography mode.
 */
enum class SteganographyMode(private val displayName: String) {
    Arithmetic("Arithmetic (recommended)"),
    /* Bins("Bins"), */
    Huffman("Huffman");

    /**
     * Function to get the display name of the steganography mode.
     *
     * @return Display name of the steganography mode.
     */
    override fun toString(): String {
        return displayName
    }
}