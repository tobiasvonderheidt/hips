package org.vonderheidt.hips.utils

/**
 * Object (i.e. singleton class) that represents various functions for string formatting.
 */
object Format {
    /**
     * Function to format a ByteArray as a bit string.
     *
     * @param byteArray A ByteArray.
     * @return The bit string.
     */
    fun asBitString(byteArray: ByteArray): String {
        val bitString = byteArray.joinToString(separator = "") { byte ->
            String.format(format = "%8s", Integer.toBinaryString(byte.toInt() and 0xFF)).replace(oldChar = ' ', newChar = '0')
        }

        return bitString
    }

    /**
     * Function to reverse formatting of a ByteArray as a bit string (i.e. to reverse `Format.asBitString(ByteArray)`).
     *
     * @param bitString A bit string.
     * @return The ByteArray.
     */
    fun asByteArray(bitString: String): ByteArray {
        // Don't assert string length to be a multiple of 8, causes error in Huffman encoding with 3 bits/token
        val byteArray = ByteArray(size = bitString.length / 8) { index ->
            val byteString = bitString.substring(startIndex = index * 8, endIndex = (index + 1) * 8)
            val byte = byteString.toInt(radix = 2).toByte()

            byte
        }

        return byteArray
    }
}