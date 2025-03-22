package org.vonderheidt.hips.utils

/**
 * Object (i.e. singleton class) that represents various functions for string formatting.
 */
object Format {
    /**
     * Function to format a ByteArray as a bit string.
     * Doesn't remove any padding, so length of bit string will be multiple of 8.
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
     * Doesn't add any padding, assumes that length of bit string already is multiple of 8.
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
     * Function to format a ByteArray as a bit string. Assumes that the ByteArray is 0-padded and that the first byte stores the length of the padding in bits.
     * Removes both the padding length and the padding.
     *
     * @param byteArray A ByteArray, 0-padded with length of padding in bits stored in first byte.
     * @return The bit string, with padding removed.
     */
    fun asBitStringWithoutPadding(byteArray: ByteArray): String {
        // Convert ByteArray to bit string as is
        val paddedBitString = byteArray.joinToString(separator = "") { byte ->
            String.format(format = "%8s", Integer.toBinaryString(byte.toInt() and 0xFF)).replace(oldChar = ' ', newChar = '0')
        }

        // Remove padding length and padding from bit string
        val paddingLength = paddedBitString.substring(startIndex = 0, endIndex = 8).toInt(radix = 2)
        val bitString = paddedBitString.substring(startIndex = 8 + paddingLength)   // Add 8 to not forget padding length itself

        return bitString
    }

    /**
     * Function to reverse formatting of a padded ByteArray as a bit string (i.e. to reverse `Format.asBitStringWithoutPadding(ByteArray)`).
     * Adds 0-padding at the start so that length of bit string is multiple of 8. Prepends a byte that stores length of padding in bits.
     *
     * @param bitString A bit string.
     * @return The ByteArray, 0-padded with length of padding in bits stored in first byte.
     * @throws IllegalArgumentException If `bitString` contains anything other than 1s and 0s.
     */
    fun asByteArrayWithPadding(bitString: String): ByteArray {
        // Check integrity of the bit string
        if (!isBitString(bitString)) {
            throw IllegalArgumentException("Bit string can only contain 0 and 1")
        }

        // Pad bit string to length multiple of 8
        val paddingLength = (8 - (bitString.length % 8)) % 8    // Outer % is for case that length of bit string is already multiple of 8
        val paddedBitString = bitString.padStart(bitString.length + paddingLength, '0')

        // Create ByteArray with extra byte
        val byteArray = ByteArray(size = 1 + (paddedBitString.length / 8)).apply {
            // First byte stores padding length
            this[0] = paddingLength.toByte()

            // Subsequent bytes store bytes from padded bit string
            for (i in 0 until paddedBitString.length / 8) {
                this[i + 1] = paddedBitString.substring(startIndex = i * 8, endIndex = (i + 1) * 8).toInt(radix = 2).toByte()   // toByte(radix = 2) apparently is not equivalent
            }
        }

        return byteArray
    }

    /**
     * Function to format an integer as a bit string of desired length. Pads the bit string with leading 0s if needed.
     *
     * Corresponds to Stegasuras method `int2bits` in `utils.py`. Parameter `inp` was renamed to `integer`, `num_bits` to `numberOfBits`.
     *
     * @param integer An integer.
     * @param numberOfBits The desired length of the bit string.
     * @return The bit string.
     */
    fun asBitString(integer: Int, numberOfBits: Int): String {
        // Only edge case covered in Stegasuras
        if (numberOfBits == 0) {
            return ""
        }

        // Convert integer to bit string of minimum necessary length and pad it to desired length
        val bitString = Integer
            .toBinaryString(integer)
            .padStart(numberOfBits, '0')

        return bitString
    }

    /**
     * Function to reverse formatting of an integer as a bit string (i.e. to reverse `Format.asBitString(Int, Int)`).
     *
     * Corresponds to Stegasuras method `bits2int` in `utils.py`. Parameter `bits` was renamed to `bitString`.
     *
     * @param bitString A bit string containing an integer.
     * @return The integer.
     */
    fun asInteger(bitString: String): Int {
        val integer = bitString.toInt(radix = 2)

        return integer
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