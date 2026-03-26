package bitmage

import android.util.Log
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
                //println("trying to take $remaining bits from ${part.bitLength} bit part")
                val bytesToTake = ceil(remaining.toDouble() / 8).toInt()
                val leftoverBits = 8 - (remaining % 8)

                //println("taking $bytesToTake bytes, leaving $leftoverBits in split byte")

                val takenBytes = part.bytes.untilIndex(bytesToTake)
                val nonTakenBytes = part.bytes.fromIndex(bytesToTake)

                //println("taken: ${takenBytes.hex()} nonTaken: ${nonTakenBytes.hex()}")

                val restOfPart = BitString(nonTakenBytes, part.bitLength - takenBytes.size * 8)

                if(leftoverBits != 8) {
                    val splitByte = part.bytes[bytesToTake-1].toUByte().toInt()
                    val bits = (splitByte shl (remaining % 8)) and 0xff
                    //println("split byte is ${splitByte.toString(16)}, leftover bits are ${bits.toString(2).padStart(8, '0')}")
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
            //println("\n\n")
            while (bitOffsetInPart < part.bitLength) {
                currentByte = part.bytes[bitOffsetInPart / 8].toUByte()
                bitsRemainingInPartByte = 8 - (bitOffsetInPart % 8)
                val contentRemainingInPartByte = min(bitsRemainingInPartByte, part.bitLength - bitOffsetInPart)

                //println("bitOffsetInPart $bitOffsetInPart bitsRemainingInPartByte $bitsRemainingInPartByte bitsRemainingInOutputByte $bitsRemainingInOutputByte")

                val inputBitMask = (1 shl bitsRemainingInPartByte) - 1
                val nextInputBits = (currentByte.toInt() and inputBitMask)
                //println("input byte ${currentByte.toString(2).padStart(8, '0')} mask ${inputBitMask.toString(2).padStart(8, '0')} input bits: ${nextInputBits.toString(2).padStart(8, '0')}")

                // fully pack the current output byte
                if(contentRemainingInPartByte >= bitsRemainingInOutputByte) {
                    val packableBits = nextInputBits shr (bitsRemainingInPartByte - bitsRemainingInOutputByte)
                    //println("packable bits: ${packableBits.toString(2).padStart(bitsRemainingInOutputByte, '0')}")

                    outputByte = outputByte or packableBits
                    bitOffsetInPart += bitsRemainingInOutputByte

                    //println("filled output byte: ${outputByte.toString(2).padStart(8, '0')}")
                    bytes += (outputByte and 0xff).toByte()
                    outputByte = 0
                    bitsRemainingInOutputByte = 8
                }
                // partially pack output byte with full remainder of input byte
                else {
                    val packableBits = nextInputBits shr (bitsRemainingInPartByte - contentRemainingInPartByte)
                    //println("packable bits: ${packableBits.toString(2).padStart(contentRemainingInPartByte, '0')}")
                    //println("output byte has ${8-bitsRemainingInOutputByte} bits occupied, we have $contentRemainingInPartByte bits to pack, shift left by ${(bitsRemainingInOutputByte - contentRemainingInPartByte)}")
                    outputByte = outputByte or (packableBits shl (bitsRemainingInOutputByte - contentRemainingInPartByte))
                    //println("partial output byte: ${outputByte.toString(2).padStart(8, '0')}")

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

        //Log.d("BitString", "searching for $needleSize b needle in $haystackSize b haystack")

        var searchIndex = haystackSize - needleSize
        var matchIndex = 0
        while(searchIndex > 0) {
            //Log.d("BitString", "searching at bit offset $searchIndex")
            while(haystackFragment.getBit(searchIndex+matchIndex) == needleFragment.getBit(matchIndex)) {
                matchIndex += 1
                //Log.d("BitString", "$matchIndex bit match")
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