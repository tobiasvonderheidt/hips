package org.vonderheidt.hips.compression

import org.vonderheidt.hips.bitmage.BitString

/**
 * Object (i.e. singleton class) that represents the binary conversion of the secret message using UTF-8 encoding.
 *
 * Renamed from `Unicode` in Stegasuras as UTF-8 is only one of many possible Unicode encodings.
 */
object UTF8 : CompressionProvider {

    override fun compress(secretMessage: String): BitString {
        val plainBits = (secretMessage).toByteArray(charset = Charsets.UTF_8)
        return BitString(plainBits, plainBits.size * 8)
    }


    override fun decompress(plainBits: BitString): String {
        check(plainBits.bitLength() % 8 == 0) { "UTF-8 expects byte-aligned input, got ${plainBits.bitLength()} bits"}

        val bytes = plainBits.toBitFragment().bytes
        return bytes.decodeToString()
    }
}