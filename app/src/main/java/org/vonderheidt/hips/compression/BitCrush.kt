package org.vonderheidt.hips.compression

import android.util.Log
import org.vonderheidt.hips.bitmage.BitString
import org.vonderheidt.hips.bitmage.toUnicodeCodepoints

private const val TAG = "BitCrush.kt"

/**
 * Object (i.e. singleton class) that represents BitCrush compression.
 */
object BitCrush : CompressionProvider {
    private val firstStage = mapOf(
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
        '?'.code to 31
    )

    private val secondStage = mapOf(
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

    private val inverseFirstStage = firstStage.toList().associate { Pair(it.second, it.first) }

    private val inverseSecondStage = secondStage.toList().associate { Pair(it.second, it.first) }

    /**
     * Function to compress a secret message from string to binary using BitCrush compression.
     *
     * @param secretMessage The secret message to compress.
     * @return The binary representation of the secret message.
     */
    override fun compress(secretMessage: String): BitString {
        val plainBits = BitString(byteArrayOf(), 0)

        secretMessage.lowercase().toUnicodeCodepoints().forEach {
            val code = firstStage[it]

            if (code != null) {
                plainBits.append(BitString.BitFragment(byteArrayOf((code shl 3).toByte()), 5))
            }
            else {
                when {
                    // common ascii character present in second stage
                    secondStage.containsKey(it) -> {
                        plainBits.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))

                        val secondStageCode = secondStage[it]
                        plainBits.append(BitString.BitFragment(byteArrayOf((secondStageCode!! shl 3).toByte()), 5))
                    }
                    // latin script char not present in first or second stage
                    it in 0x000..0x02af -> {
                        plainBits.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))

                        // top two bits get encoded into second stage code, remaining 8 follow
                        val bmpCode = 0xc0 or ((it shr 8) shl 3)
                        plainBits.append(BitString.BitFragment(byteArrayOf(bmpCode.toByte()), 5))

                        val remainder = it and 0xff
                        plainBits.append(BitString.BitFragment(byteArrayOf(remainder.toByte()), 8))
                    }
                    // support for Emoticons and Miscellaneous Symbols and Pictographs, main emoji blocks
                    it in 0x1F300..0x1F64F -> {
                        plainBits.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))

                        // top two bits get encoded into second stage code, remaining 8 follow
                        val uniRefCode = (27 + ((it - 0x1F300) shr 8)) shl 3
                        plainBits.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))

                        val codepoint = (it - 0x1F300).toByte()
                        plainBits.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    // support for Supplemental Symbols and Pictographs
                    it in 0x1F900..0x1F9FF -> {
                        plainBits.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))

                        val uniRefCode = 22 shl 3
                        plainBits.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))

                        val codepoint = (it - 0x1F900).toByte() // range can be encoded in 8b
                        plainBits.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    // partial support for Dingbats block, covers all emoji in it
                    it in 0x2700..0x276F -> {
                        plainBits.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))

                        val uniRefCode = 23 shl 3
                        plainBits.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))

                        val codepoint = (it - 0x2700).toByte() // range can be encoded in 8b (will be 0x00-0x6F)
                        plainBits.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    // support for Symbols and Pictographs Extended-A block, contains recent emoji
                    it in 0x1FA70..0x1FAFF -> {
                        plainBits.append(BitString.BitFragment(byteArrayOf(0.toByte()), 5))

                        val uniRefCode = 23 shl 3
                        plainBits.append(BitString.BitFragment(byteArrayOf(uniRefCode.toByte()), 5))

                        val codepoint = (it - 0x1FA00).toByte() // range can be encoded in 8b (will be 0x70-0xFF)
                        plainBits.append(BitString.BitFragment(byteArrayOf(codepoint), 8))
                    }
                    else -> {
                        Log.w(TAG, "Dropping unsupported character: $it")
                    }
                }
            }
        }

        return plainBits
    }

    /**
     * Function to decompress a secret message from binary to string using BitCrush decompression.
     *
     * @param plainBits The binary representation of the secret message.
     * @return The decompressed secret message.
     */
    override fun decompress(plainBits: BitString): String {
        var secretMessage = ""

        while (plainBits.bitLength() > 0) {
            val codepoint = plainBits.takeFew(5).toUByte().toInt() shr 3

            if (inverseFirstStage.containsKey(codepoint)) {
                secretMessage += inverseFirstStage[codepoint]!!.toChar()
            }
            else {
                val secondStage = plainBits.takeFew(5).toUByte().toInt() shr 3

                when {
                    inverseSecondStage.containsKey(secondStage) -> {
                        secretMessage += inverseSecondStage[secondStage]!!.toChar()
                    }
                    secondStage == 22 -> {
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = unicodeBits + 0x1F900

                        secretMessage += codepointToString(unicodePoint)
                    }
                    secondStage == 23 -> {
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = if (unicodeBits < 0x70) unicodeBits + 0x2700 else unicodeBits + 0x1FA00

                        secretMessage += codepointToString(unicodePoint)

                        // special case for read heart with missing variant selector (we choose to support emoji hearts and not black text hearts)
                        if (unicodePoint == 0x2764) {
                            secretMessage += Char(0xFE0F)
                        }
                    }
                    secondStage in 24..26 -> {
                        val topBits = (secondStage - 24) shl 8
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = 0x0000 + (topBits or unicodeBits)

                        secretMessage += codepointToString(unicodePoint)
                    }
                    secondStage in 27..30 -> {
                        val topBits = (secondStage - 27) shl 8
                        val unicodeBits = plainBits.takeFew(8).toUByte().toInt()
                        val unicodePoint = 0x1F300 + (topBits or unicodeBits)

                        secretMessage += codepointToString(unicodePoint)
                    }
                    else -> {
                        Log.w(TAG, "Unknown second stage payload: $secondStage")
                    }
                }
            }
        }

        return secretMessage
    }

    /**
     * Function to convert a Unicode code point into a string.
     *
     * @param codepoint A Unicode code point.
     * @return The string conversion of `codepoint`.
     */
    private fun codepointToString(codepoint: Int): String {
        // single UTF-16 char
        return if (codepoint < 65536)
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