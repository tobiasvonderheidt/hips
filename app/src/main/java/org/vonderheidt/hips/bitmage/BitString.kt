package org.vonderheidt.hips.bitmage

import kotlin.math.ceil
import kotlin.math.min

/**
 * Class that represents a bit string. Bit strings consist of one or multiple bit fragments.
 *
 * @param fragment A bit fragment.
 */
class BitString(fragment: BitFragment) {
    /**
     * Constructor for a bit string. Does not take any parameters as it creates an empty bit string.
     */
    constructor() : this(byteArrayOf(), 0)

    /**
     * Constructor for a bit string. Creates a bit string of length `bitLength` from byte array `bytes`.
     * If `bitLength` is less than `bytes.size * 8`, any excess bits in the last bytes are ignored.
     *
     * @param bytes The byte array to construct a bit string from.
     * @param bitLength The desired length of the bit string.
     */
    constructor(bytes: ByteArray, bitLength: Int) : this(BitFragment(bytes, bitLength))

    /**
     * Class that represents a bit fragment. One or multiple bit fragments make up a bit string.
     *
     * @param bytes The byte array to construct a bit fragment from.
     * @param bitLength The desired length of the bit fragment.
     */
    class BitFragment(val bytes: ByteArray, val bitLength: Int) {
        /**
         * Function to get the bit at the given position in the bit fragment.
         * Position `0` corresponds to the most significant bit of `bytes[0]`.
         *
         * @param position Position of the bit to get.
         * @return Boolean that is true if the bit at `position` is `1`, false otherwise.
         */
        fun getBit(position: Int): Boolean {
            val byte = bytes[position/8].toUByte().toInt()

            return ((byte shr (7 - (position % 8))) and 0x01) == 1
        }

        /**
         * Function to check two bit fragments for equality based on identical length and bit values.
         *
         * @param other Bit fragment to compare to `this`.
         * @return Boolean that is true if `other` is equal to `this`, false otherwise.
         */
        override fun equals(other: Any?): Boolean {
            if (other !is BitFragment) {
                return false
            }

            return bitLength == other.bitLength && (0 until bitLength).all { other.getBit(it) == getBit(it) }
        }

        /**
         * Function to calculate a hash of the bit fragment based on its length and underlying byte array.
         *
         * @return Hash of the bit fragment.
         */
        override fun hashCode(): Int {
            var result = bitLength

            result = 31 * result + bytes.contentHashCode()

            return result
        }

        /**
         * Function to represent the bit fragment as a human-readable string.
         *
         * @return Human-readable string representation of the bit fragment.
         */
        override fun toString(): String {
            return "BitFragment(${bytes.hex()}, ${bitLength}b)"
        }
    }

    private val parts = mutableListOf<BitFragment>(fragment)

    /**
     * Function to append a bit fragment to the bit string.
     *
     * @param data Bit fragment to append to the bit string.
     */
    fun append(data: BitFragment) {
        parts.add(data)
    }

    /**
     * Function to prepend a bit fragment to the bit string.
     *
     * @param data Bit fragment to prepend to the bit string.
     */
    fun prepend(data: BitFragment) {
        parts.add(0, data)
    }

    /**
     * Function to take the up to 8 most significant bits from the bit string.
     *
     * @param length Desired number of most significant bits to take. Has to be in [0, 8].
     * @return Byte containing the `length` most significant bits of the bit string.
     */
    fun takeFew(length: Int): Byte {
        if (length == 0) {
            return 0
        }

        check(length <= 8)
        val bits = take(length)

        return bits.toBitFragment().bytes[0]
    }

    /**
     * Function to take the desired number of most significant bits from the bit string.
     *
     * @param length Desired number of most significant bits to take.
     * @return Bit substring containing the `length` most significant bits of the bit string.
     */
    fun take(length: Int): BitString {
        if (length == 0) {
            return BitString(byteArrayOf(), 0)
        }

        val output = BitString()
        var outLen = 0

        while (outLen < length) {
            val remaining = length - outLen
            val part = parts.removeAt(0)

            if (part.bitLength <= remaining) {
                output.append(part)
                outLen += part.bitLength
            }
            else {
                val bytesToTake = ceil(remaining.toDouble() / 8).toInt()
                val leftoverBits = 8 - (remaining % 8)

                val takenBytes = part.bytes.untilIndex(bytesToTake)
                val nonTakenBytes = part.bytes.fromIndex(bytesToTake)

                val restOfPart = BitString(nonTakenBytes, part.bitLength - takenBytes.size * 8)

                if (leftoverBits != 8) {
                    val splitByte = part.bytes[bytesToTake-1].toUByte().toInt()
                    val bits = (splitByte shl (remaining % 8)) and 0xff

                    restOfPart.prepend(BitFragment(byteArrayOf(bits.toByte()), leftoverBits))
                }

                parts.add(0, restOfPart.toBitFragment())
                output.append(BitFragment(takenBytes, remaining))
                outLen += remaining
            }
        }

        return output
    }

    /**
     * Function to calculate the length of the bit string (i.e. the total number of bits it stores).
     *
     * @return Length of the bit string.
     */
    fun bitLength() = parts.sumOf { it.bitLength }

    /**
     * Function to flatten a bit string consisting of multiple bit fragments into a single bit fragment.
     *
     * @return Single bit fragment containing the entire bit string.
     */
    fun toBitFragment(): BitFragment {
        var bytes = byteArrayOf()
        var outputByte = 0
        var bitsRemainingInOutputByte = 8

        parts.forEach { part ->
            var bitOffsetInPart = 0
            var currentByte: UByte
            var bitsRemainingInPartByte: Int

            while (bitOffsetInPart < part.bitLength) {
                currentByte = part.bytes[bitOffsetInPart / 8].toUByte()
                bitsRemainingInPartByte = 8 - (bitOffsetInPart % 8)

                val contentRemainingInPartByte = min(bitsRemainingInPartByte, part.bitLength - bitOffsetInPart)

                val inputBitMask = (1 shl bitsRemainingInPartByte) - 1
                val nextInputBits = (currentByte.toInt() and inputBitMask)

                // fully pack the current output byte
                if (contentRemainingInPartByte >= bitsRemainingInOutputByte) {
                    val packableBits = nextInputBits shr (bitsRemainingInPartByte - bitsRemainingInOutputByte)

                    outputByte = outputByte or packableBits
                    bitOffsetInPart += bitsRemainingInOutputByte

                    bytes += (outputByte and 0xff).toByte()
                    outputByte = 0
                    bitsRemainingInOutputByte = 8
                }
                // partially pack output byte with full remainder of input byte
                else {
                    val packableBits = nextInputBits shr (bitsRemainingInPartByte - contentRemainingInPartByte)

                    outputByte = outputByte or (packableBits shl (bitsRemainingInOutputByte - contentRemainingInPartByte))

                    bitsRemainingInOutputByte -= contentRemainingInPartByte

                    bitOffsetInPart += contentRemainingInPartByte
                }
            }
        }

        if (bitsRemainingInOutputByte != 8) {
            bytes += outputByte.toByte()

            return BitFragment(bytes, bytes.size * 8 - bitsRemainingInOutputByte)
        }
        else
            return BitFragment(bytes, bytes.size * 8)
    }

    /**
     * Function to search for the first occurrence of a given bit sequence in the bit string.
     * Searches from the least towards the most significant bits of the bit string.
     *
     * @param sequence Bit sequence to search for.
     * @return Index where first occurrence of `sequence` in bit string starts. `-1` if no occurrence is found.
     */
    fun searchFromEnd(sequence: BitFragment): Int {
        val haystackFragment = this.toBitFragment()
        val haystackSize = haystackFragment.bitLength
        val needleFragment = sequence
        val needleSize = needleFragment.bitLength

        var searchIndex = haystackSize - needleSize
        var matchIndex = 0

        while (searchIndex > 0) {
            while (haystackFragment.getBit(searchIndex+matchIndex) == needleFragment.getBit(matchIndex)) {
                matchIndex += 1

                if (matchIndex == needleSize) {
                    return searchIndex
                }
            }

            searchIndex -= 1
        }

        return -1
    }

    /**
     * Function to create a deep copy of the bit string.
     *
     * @return Deep copy of the bit string.
     */
    fun clone(): BitString {
        val fragment = toBitFragment()

        return BitString(fragment.bytes.clone(), fragment.bitLength)
    }

    /**
     * Function to represent the bit string as a human-readable string.
     *
     * @return Human-readable string representation of the bit string.
     */
    override fun toString(): String {
        val fragment = toBitFragment()

        return "BitString(${fragment.bytes.hex()}, ${fragment.bitLength}b)"
    }
}