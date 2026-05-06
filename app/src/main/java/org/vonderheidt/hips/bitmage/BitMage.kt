package org.vonderheidt.hips.bitmage

/**
 * Function to represent a byte array as a hex string.
 *
 * @return Representation of byte array as hex string.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArray.hex() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

/**
 * Function to copy all elements in a byte array from a given index.
 *
 * @param i Index from which to copy all elements.
 * @return Byte array containing copy of all elements from index `i`.
 */
fun ByteArray.fromIndex(i: Int) = sliceArray(i until size)

/**
 * Function to copy all elements in a byte array until a given index.
 *
 * @param i Index until which to copy all elements.
 * @return Byte array containing copy of all elements until index `i`.
 */
fun ByteArray.untilIndex(i: Int) = sliceArray(0 until i)

/**
 * Function to represent a string as list of the Unicode code points of its characters.
 *
 * @return List of the Unicode code points of the characters in the string.
 */
fun String.toUnicodeCodepoints(): List<Int> {
    var i = 0
    val codepoints = mutableListOf<Int>()

    while (i < this.length) {
        val codepoint = when (val unit = this[i++].code) {
            in Char.MIN_HIGH_SURROGATE.code..Char.MAX_HIGH_SURROGATE.code -> {
                if (i !in this.indices) {
                    throw CharacterCodingException() // unpaired high surrogate
                }

                val lowSurrogate = this[i++].code
                val highSurrogate = unit

                if (lowSurrogate !in Char.MIN_LOW_SURROGATE.code..Char.MAX_LOW_SURROGATE.code) {
                    throw CharacterCodingException() // unpaired high surrogate
                }

                val code = ((highSurrogate - 0xd800) shl 10) or (lowSurrogate - 0xdc00) + 0x10000

                if (code !in 0x010000..0x10FFFF) {
                    throw CharacterCodingException() // non-canonical encoding
                }

                code
            }

            in Char.MIN_LOW_SURROGATE.code..Char.MAX_LOW_SURROGATE.code -> {
                throw CharacterCodingException() // unpaired low surrogate
            }

            else -> unit
        }

        codepoints.add(codepoint)
    }

    return codepoints
}