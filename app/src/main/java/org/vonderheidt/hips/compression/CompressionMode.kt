package org.vonderheidt.hips.compression

/**
 * Class to enumerate all compression modes.
 *
 * @param displayName Display name of the compression mode.
 */
enum class CompressionMode(private val displayName: String) {
    Adaptive("Adaptive (recommended)"),
    Arithmetic("Arithmetic"),
    BitCrush("BitCrush"),
    UTF8("UTF-8");

    /**
     * Function to get the display name of the compression mode.
     *
     * @return Display name of the compression mode.
     */
    override fun toString(): String {
        return displayName
    }
}