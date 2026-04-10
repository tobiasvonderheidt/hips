package org.vonderheidt.hips.utils

import android.util.Log
import org.vonderheidt.hips.bitmage.BitString
import org.vonderheidt.hips.compression.Compression
import org.vonderheidt.hips.compression.CompressionMode
import org.vonderheidt.hips.crypto.Crypto
import org.vonderheidt.hips.data.Settings
import kotlin.time.measureTime

private const val TAG = "Steganography.kt"

/**
 * Object (i.e. singleton class) that represents steganography encoding and decoding.
 */
object Steganography {
    private val startSignal = BitString.BitFragment(byteArrayOf(0), 5)
    private val stopSignal = BitString.BitFragment(byteArrayOf(0x94.toByte()), 7)

    /**
     * Function to encode secret message into cover text using given context.
     *
     * @param context The context to encode the secret message with.
     * @param secretMessage The secret message to be encoded.
     * @param compressionMode Compression mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @return A cover text containing the secret message.
     */
    fun encode(
        context: String,
        secretMessage: String,
        compressionMode: CompressionMode = Settings.compressionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        Log.d(TAG, "encoding secret message: $secretMessage")

        // Step 0: Convert secret message to a (compressed) binary representation
        val plainBits: BitString

        val compressTime = measureTime {
            plainBits = Compression.compress(secretMessage, compressionMode)
        }

        Log.d(TAG, "compressed using $compressionMode to: ${plainBits.bitLength()}b, took $compressTime")

        // Step 1: Prepare secret message by prepending start and appending stop signal
        val preparedPlainBits = prepare(plainBits)

        Log.d(TAG, "padded with start, stop signals to: ${preparedPlainBits.bitLength()}b")

        // Step 2: Encrypt binary representation of secret message
        val cipherBits = Crypto.encrypt(preparedPlainBits)

        Log.d(TAG, "encrypted, payload for stego: $cipherBits")

        // Step 3: Encode encrypted binary representation of secret message into cover text
        LlamaCpp.resetInstance()

        val coverText: String

        val encodeTime = measureTime {
            coverText = when (steganographyMode) {
                SteganographyMode.Arithmetic -> { Arithmetic.encode(context, cipherBits, isResumed = false) }
                SteganographyMode.Huffman -> { Huffman.encode(context, cipherBits) }
            }
        }

        Log.d(TAG, "encoding took $encodeTime")

        return coverText
    }

    /**
     * Function to split a cover text into paragraphs.
     *
     * Uses a regular expression to only split when there are exactly 2 subsequent line breaks between paragraphs.
     * This avoids leading or trailing whitespaces that would be trimmed in instant messengers like WhatsApp or Signal.
     *
     * Example:
     * - Paragraphs separated by `\n\n` would be split.
     * - Paragraphs separated by `\n \n\n`, ` \n\n`, `\n \n`, etc. would not be split.
     *
     * @param coverText Cover text to split into paragraphs as a string.
     * @return Paragraphs of cover text as a list of strings.
     */
    fun split(coverText: String): List<String> {
        // Split cover text into paragraphs whenever there are 2 subsequent line breaks, not considering additional line breaks or whitespaces
        val paragraphsWithWhitespaces = coverText.split("\n\n").toMutableList()
        val paragraphsWithoutWhitespaces = mutableListOf<String>()

        // Regular expression to find 3 or more line breaks, with arbitrary number of whitespaces between them
        val regex = Regex("(\\s*\\n\\s*){3,}")

        // Concat adjacent paragraphs again to check for additional line breaks or whitespaces
        var i = 0

        while (i < paragraphsWithWhitespaces.size - 1) {
            val concat = paragraphsWithWhitespaces[i] + "\n\n" + paragraphsWithWhitespaces[i+1]

            // Replace split paragraphs that would cause leading or trailing whitespaces with their concatenation
            if (regex.containsMatchIn(concat)) {
                paragraphsWithoutWhitespaces.add(concat)

                // Skip next iteration as its paragraph is already added as part of concat
                i += 2
            }
            // Keep split paragraphs that would not cause leading or trailing whitespaces as is
            // Don't add paragraph at index i+1 here to avoid IndexOutOfBoundsException for last paragraph
            else {
                paragraphsWithoutWhitespaces.add(paragraphsWithWhitespaces[i])
                i++
            }
        }

        // Add last paragraph safely here:
        // If last iteration of loop executed if-case, last paragraph was already added as part of concat and we have i == size
        // Otherwise last iteration executed else-case, so last paragraph can be added safely as we have i == size-1
        if (i < paragraphsWithWhitespaces.size) {
            paragraphsWithoutWhitespaces.add(paragraphsWithWhitespaces[i])
        }

        return paragraphsWithoutWhitespaces
    }

    /**
     * Function to check if a message is the first of a split cover text. Partially decodes the cover text to see if it contains the start signal.
     *
     * @param context The context to decode the cover text with.
     * @param coverText The cover text containing a secret message.
     * @return Boolean that is true if the message is the first of a split cover text, false otherwise.
     */
    fun isFirstMessageOfSplit(
        context: String,
        coverText: String,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): Boolean {
        val numberOfCipherBits = startSignal.bitLength
        var isFirstMessageOfSplit: Boolean

        // Invert step 3
        LlamaCpp.resetInstance()

        Log.d(TAG, "checking '$coverText' for first message of split")

        // Wrap this in try-catch because decoding with wrong context is likely to throw exceptions
        val partialCipherBits: BitString

        try {
            partialCipherBits = when (steganographyMode) {
                SteganographyMode.Arithmetic -> { Arithmetic.decode(context, coverText, numberOfCipherBits) }
                SteganographyMode.Huffman -> { Huffman.decode(context, coverText, numberOfCipherBits) }
            }
        }
        catch (exception: Exception) {
            isFirstMessageOfSplit = false

            return isFirstMessageOfSplit
        }

        Log.d(TAG, "got partial cipher bits: $partialCipherBits, expecting $startSignal")

        // Invert step 2
        val partialPlainBits = Crypto.decrypt(partialCipherBits)

        // Check for start signal
        val firstBits = partialPlainBits.take(numberOfCipherBits)

        isFirstMessageOfSplit = startSignal == firstBits.toBitFragment()

        return isFirstMessageOfSplit
    }

    // TODO Downward concat of split cover text
    //  Function isLastMessageOfSplit checks for stop signal so we can terminate decoding as early as possible
    /**
     * Function to check if a message is the last of a split cover text. Takes a partially decoded cover text to see if it contains the stop signal.
     *
     * @param partialSecretMessage Partially decoded cover text.
     * @return Boolean that is true if the message is the last of a split cover text, false otherwise.
     */
    fun isLastMessageOfSplit(partialSecretMessage: String): Boolean {
        // Not .endsWith() because of possible noise following stop signal
        val isLastMessageOfSplit = partialSecretMessage.contains(LlamaCpp.getAsciiEtx())

        return isLastMessageOfSplit
    }

    // TODO Downward concat of split cover text
    //  Parameter isResumed in decode function is to differentiate first from subsequent calls of decode
    //  Save and restore of {decode,decompress}Ctx is to resume decoding from last call of decode
    /**
     * Function to decode secret message from cover text using given context.
     *
     * @param context The context to decode the cover text with.
     * @param coverText The cover text containing a secret message.
     * @param compressionMode Compression mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @param isResumed Boolean that is true if this call of the `decode` function resumes where the last call terminated, false otherwise.
     * @return The secret message.
     */
    fun decode(
        context: String,
        coverText: String,
        compressionMode: CompressionMode = Settings.compressionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode,
        isResumed: Boolean = false
    ): String {
        // Invert step 3
        if (isResumed) {
            // Restore ctx for decoding
            LlamaCpp.setCtx(ctx = LlamaCpp.getDecodeCtx())
        }
        else {
            // Reset ctx
            LlamaCpp.resetInstance()
        }

        val cipherBits = when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.decode(context, coverText, isResumed = isResumed) }
            SteganographyMode.Huffman -> { Huffman.decode(context, coverText, isResumed = isResumed) }
        }

        // Save ctx for decoding
        LlamaCpp.setDecodeCtx(decodeCtx = LlamaCpp.getCtx())

        Log.d(TAG, "decoded cipher bits: $cipherBits")

        // Invert step 2
        val preparedPlainBits = Crypto.decrypt(cipherBits)

        Log.d(TAG, "plaintext bits: $preparedPlainBits")

        // Invert step 1
        val plainBits = unprepare(preparedPlainBits)

        Log.d(TAG, "stripped bits: $plainBits")

        // Invert step 0
        val secretMessage = Compression.decompress(plainBits, compressionMode)

        Log.d(TAG, "decompressed message using $compressionMode: $secretMessage")

        return secretMessage
    }

    /**
     * Function to prepare the plain bits for steganography encoding.
     *
     * Appends both a start and a stop signal. Needed to decode split cover texts and remove artefacts from greedy sampling, respectively.
     *
     * @param plainBits The original plain bits.
     * @return The prepared plain bits.
     */
    private fun prepare(plainBits: BitString): BitString {
        plainBits.prepend(startSignal)
        plainBits.append(stopSignal)

        return plainBits
    }

    /**
     * Function to unprepare plain bits after steganography decoding.
     *
     * Strips both the start and the stop signal, and everything after the stop signal. Therefore removes any artefacts from greedy sampling, rendering the original plain bits.
     *
     * @param preparedPlainBits The prepared plain bits.
     * @return The original plain bits.
     */
    private fun unprepare(preparedPlainBits: BitString): BitString {
        // removing start signal is easy since it is always at the start
        val firstBits = preparedPlainBits.take(startSignal.bitLength).toBitFragment()

        check(firstBits == startSignal) { "start signal should be $startSignal, got $firstBits"}

        val matchIndex = preparedPlainBits.firstSubsequenceMatchFromEnd(BitString(stopSignal))

        if (matchIndex == -1) {
            throw Exception("no stop signal found")
        }

        Log.d(TAG, "found stop signal at bit-offset $matchIndex")

        val plainBits = preparedPlainBits.take(matchIndex)

        Log.d(TAG, "payload $plainBits, stop signal + tail: $preparedPlainBits")

        // TODO Stop signal is ignored for now

        return plainBits
    }
}