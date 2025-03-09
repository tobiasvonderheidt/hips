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
     * @throws IllegalArgumentException If `bitString` contains anything other than 1s and 0s.
     */
    fun asByteArray(bitString: String): ByteArray {
        // Check integrity of the bit string
        if (!isBitString(bitString)) {
            throw IllegalArgumentException("Bit string can only contain 0 and 1")
        }

        // Don't assert string length to be a multiple of 8, causes error in Huffman encoding with 3 bits/token
        val byteArray = ByteArray(size = bitString.length / 8) { index ->
            val byteString = bitString.substring(startIndex = index * 8, endIndex = (index + 1) * 8)
            val byte = byteString.toInt(radix = 2).toByte()

            byte
        }

        return byteArray
    }

    /**
     * Function to check integrity of a bit string, i.e. if it only contains 1s and 0s.
     *
     * @param bitString A bit string.
     * @return Boolean that is true if `bitString` only contains 1s and 0s, false otherwise.
     */
    private fun isBitString(bitString: String): Boolean {
        return bitString.all { bit -> bit == '0' || bit == '1' }
    }
}