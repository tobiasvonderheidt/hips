package org.vonderheidt.hips.bitmage

import kotlin.math.ceil
import kotlin.math.min

class BitString(fragment: BitFragment) {

    constructor() : this(byteArrayOf(), 0)
    constructor(bytes: ByteArray, bitLength: Int) : this(BitFragment(bytes, bitLength))


    class BitFragment(val bytes: ByteArray, val bitLength: Int) {
        fun getBit(position: Int): Boolean {
            val byte = bytes[position/8].toUByte().toInt()
            return ((byte shr (7 - (position % 8))) and 0x01) == 1
        }

        override fun equals(other: Any?): Boolean {
            if(other !is BitFragment)
                return false

            return bitLength == other.bitLength && (0 until bitLength).all { other.getBit(it) == getBit(it) }
        }

        override fun hashCode(): Int {
            var result = bitLength
            result = 31 * result + bytes.contentHashCode()
            return result
        }

        override fun toString(): String {
            return "BitFragment(${bytes.hex()}, ${bitLength}b)"
        }
    }

    private val parts = mutableListOf<BitFragment>(fragment)

    fun append(data: BitFragment) {
        parts.add(data)
    }

    fun prepend(data: BitFragment) {
        parts.add(0, data)
    }

    fun takeFew(length: Int): Byte {
        if(length == 0)
            return 0

        check(length <= 8)
        val bits = take(length)
        return bits.toBitFragment().bytes[0]
    }

    fun take(length: Int): BitString {
        if(length == 0)
            return BitString(byteArrayOf(), 0)

        val output = BitString()
        var outLen = 0
        while(outLen < length) {
            val remaining = length - outLen
            val part = parts.removeAt(0)

            if(part.bitLength <= remaining) {
                output.append(part)
                outLen += part.bitLength
            }
            else {
                val bytesToTake = ceil(remaining.toDouble() / 8).toInt()
                val leftoverBits = 8 - (remaining % 8)


                val takenBytes = part.bytes.untilIndex(bytesToTake)
                val nonTakenBytes = part.bytes.fromIndex(bytesToTake)


                val restOfPart = BitString(nonTakenBytes, part.bitLength - takenBytes.size * 8)

                if(leftoverBits != 8) {
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

    fun bitLength() = parts.sumOf { it.bitLength }

    fun toBitFragment(): BitFragment {
        var bytes = byteArrayOf()
        var outputByte: Int = 0
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
                if(contentRemainingInPartByte >= bitsRemainingInOutputByte) {
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

        if(bitsRemainingInOutputByte != 8) {
            bytes += outputByte.toByte()
            return BitFragment(bytes, bytes.size * 8 - bitsRemainingInOutputByte)
        }
        else
            return BitFragment(bytes, bytes.size * 8)
    }

    fun firstSubsequenceMatchFromEnd(sequence: BitString): Int {
        val haystackFragment = this.toBitFragment()
        val haystackSize = haystackFragment.bitLength
        val needleFragment = sequence.toBitFragment()
        val needleSize = needleFragment.bitLength


        var searchIndex = haystackSize - needleSize
        var matchIndex = 0
        while(searchIndex > 0) {
            while(haystackFragment.getBit(searchIndex+matchIndex) == needleFragment.getBit(matchIndex)) {
                matchIndex += 1
                if(matchIndex == needleSize)
                    return searchIndex
            }
            searchIndex -= 1
        }

        return -1
    }

    fun findMaximumMatchFromEnd(sequence: BitString): Int {
        return 0
    }

    fun clone(): BitString {
        val fragment = toBitFragment()
        return BitString(fragment.bytes.clone(), fragment.bitLength)
    }

    override fun toString(): String {
        val fragment = toBitFragment()
        return "BitString(${fragment.bytes.hex()}, ${fragment.bitLength}b)"
    }
}