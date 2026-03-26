package org.vonderheidt.hips.compression

import bitmage.BitString
import bitmage.toUnicodeCodepoints

/**
 * Object (i.e. singleton class) that represents the binary conversion of the secret message using UTF-8 encoding.
 *
 * Renamed from `Unicode` in Stegasuras as UTF-8 is only one of many possible Unicode encodings.
 */
object BitCrush : CompressionProvider {

    private val lookup = mapOf(
        'a'.code to 1,
        'b'.code to 2,
        'c'.code to 3,
        'd'.code to 4,
        'e'.code to 5,
        'f'.code to 6,
        'g'.code to 7,
        'h'.code to 8,
        'i'.code to 9,
        'j'.code to 10,
        'k'.code to 11,
        'l'.code to 12,
        'm'.code to 13,
        'n'.code to 14,
        'o'.code to 15,
        'p'.code to 16,
        'q'.code to 17,
        'r'.code to 18,
        's'.code to 19,
        't'.code to 20,
        'u'.code to 21,
        'v'.code to 22,
        'w'.code to 23,
        'x'.code to 24,
        'y'.code to 25,
        '\''.code to 26,
        ' '.code to 27,
        '.'.code to 28,
        ','.code to 29,
        'z'.code to 30,
        '?'.code to 31,
    )

    private val secondStageLookup = mapOf(
        '!'.code to 1,
        '"'.code to 2,
        '&'.code to 3,
        '%'.code to 4,
        '('.code to 5,
        ')'.code to 6,
        '-'.code to 7,
        '/'.code to 8,
        ':'.code to 9,
        '@'.code to 10,
        '_'.code to 11,
        '0'.code to 12,
        '1'.code to 13,
        '2'.code to 14,
        '3'.code to 15,
        '4'.code to 16,
        '5'.code to 17,
        '6'.code to 18,
        '8'.code to 19,
        '9'.code to 20,
        '\n'.code to 21,
        // 22 unicode ref U+1F900..U+1F9FF
        // 23 unicode ref U+2700..U+276F, U+1FA70..U+1FAFF
        // 24 unicode ref U+0000..U+02AF (0b00)
        // 25 unicode ref U+0000..U+02AF (0b01)
        // 26 unicode ref U+0000..U+02AF (0b10)
        // 27 unicode ref U+1F300..U+1F64F (0b00)
        // 28 unicode ref U+1F300..U+1F64F (0b01)
        // 29 unicode ref U+1F300..U+1F64F (0b10)
        // 30 unicode ref U+1F300..U+1F64F (0b11)
        '~'.code to 31
    )

    private val inverseLookup = lookup.toList().associate { Pair(it.second, it.first) }
    private val inverseSecondLookup = secondStageLookup.toList().associate { Pair(it.second, it.first) }
    
    override fun compress(message: String): BitString {
        val encoded = BitString(byteArrayOf(), 0)
        val lower = message.lowercase().toUnicodeCodepoints()

        lower.forEach {
            val code = lookup[it]
            if(code != null)
                encoded.append(BitString.BitFragment(byteArrayOf((code shl 3).toByte()), 5))
            else {
                when {
                    // common ascii character present in second stage
                    secondStageLookup.containsKey(it) -> {
                        encoded.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))
                        val secondStageCode = secondStageLookup[it]
                        encoded.append(BitString.BitFragment(byteArrayOf((secondStageCode!! shl 3).toByte()), 5))
                    }
                    // latin script char not present in first or second stage
                    it in 0x000..0x02af -> {
                        encoded.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))
                        // top two bits get encoded into second stage code, remaining 8 follow
                        val bmpCode = 0xc0 or ((it shr 8) shl 3)
                        encoded.append(BitString.BitFragment(byteArrayOf(bmpCode.toByte()), 5))
                        val remainder = it and 0xff
                        encoded.append(BitString.BitFragment(byteArrayOf(remainder.toByte()), 8))
                    }
                    // support for Emoticons and Miscellaneous Symbols and Pictographs, main emoji blocks
                    it in 0x1F300..0x1F64F -> {
                        encoded.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))
                        // top two bits get encoded into second stage code, remaining 8 follow
                        val uniRefCode = (27 + ((it - 0x1F300) shr 8)) shl 3
                        val codepoint = (it - 0x1F300).toByte()
                        encoded.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))
                        encoded.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    // support for Supplemental Symbols and Pictographs
                    it in 0x1F900..0x1F9FF -> {
                        encoded.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))
                        val uniRefCode = 22 shl 3
                        val codepoint = (it - 0x1F900).toByte() // range can be encoded in 8b
                        encoded.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))
                        encoded.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    // partial support for Dingbats block, covers all emoji in it
                    it in 0x2700..0x276F -> {
                        encoded.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))
                        val uniRefCode = 23 shl 3
                        val codepoint = (it - 0x2700).toByte() // range can be encoded in 8b (will be 0x00-0x6F)
                        encoded.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))
                        encoded.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    // support for Symbols and Pictographs Extended-A block, contains recent emoji
                    it in 0x1FA70..0x1FAFF -> {
                        encoded.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))
                        val uniRefCode = 23 shl 3
                        val codepoint = (it - 0x1FA00).toByte() // range can be encoded in 8b (will be 0x70-0xFF)
                        encoded.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))
                        encoded.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    else -> {
                        println("dropping unsupported character: $it")
                    }
                }
            }
        }

        return encoded
    }

    override fun inflate(plainBits: BitString): String {
        var msg = ""
        while(plainBits.bitLength() > 0) {
            val codepoint = plainBits.takeFew(5).toUByte().toInt() shr 3
            if(inverseLookup.containsKey(codepoint)) {
                msg += inverseLookup[codepoint]!!.toChar()
            }
            else {
                val secondStage = plainBits.takeFew(5).toUByte().toInt() shr 3
                when {
                    inverseSecondLookup.containsKey(secondStage) -> msg += inverseSecondLookup[secondStage]!!.toChar()
                    secondStage == 22 -> {
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = unicodeBits + 0x1F900
                        msg += codepointToString(unicodePoint)
                    }
                    secondStage == 23 -> {
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = if(unicodeBits < 0x70) unicodeBits + 0x2700 else unicodeBits + 0x1FA00
                        msg += codepointToString(unicodePoint)

                        // special case for read heart with missing variant selector (we choose to support emoji hearts and not black text hearts)
                        if(unicodePoint == 0x2764)
                            msg += Char(0xFE0F)
                    }
                    secondStage in 24..26 -> {
                        val topBits = (secondStage - 24) shl 8
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = 0x0000 + (topBits or unicodeBits)
                        msg += codepointToString(unicodePoint)
                    }
                    secondStage in 27..30 -> {
                        val topBits = (secondStage - 27) shl 8
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = 0x1F300 + (topBits or unicodeBits)
                        msg += codepointToString(unicodePoint)
                    }
                    else -> println("unknown second stage payload: $secondStage")
                }
            }
        }

        return msg
    }

    private fun codepointToString(codepoint: Int): String {
        // single UTF-16 char
        return if(codepoint < 65536)
            codepoint.toChar().toString()
        // Surrogates
        else {
            val toEncode = codepoint - 0x10000
            val highSurrogate = 0xd800 + (toEncode shr 10)
            val lowSurrogate = 0xdc00 + (toEncode and 0x3FF)
            highSurrogate.toChar().toString() + lowSurrogate.toChar().toString()
        }
    }
}