package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography encoding and decoding.
 */
object Steganography {
    /**
     * Function to encode secret message into cover text using given context.
     *
     * @param context The context to encode the secret message with.
     * @param secretMessage The secret message to be encoded.
     * @param conversionMode Conversion mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @return A cover text containing the secret message.
     */
    fun encode(
        context: String,
        secretMessage: String,
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): String {
        // Step 0: Prepare secret message by appending ASCII NUL character
        val preparedSecretMessage = prepare(secretMessage)

        // Step 1: Convert secret message to a (compressed) binary representation
        LlamaCpp.resetInstance()

        val plainBits = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.compress(preparedSecretMessage) }
            ConversionMode.UTF8 -> { UTF8.encode(preparedSecretMessage) }
        }

        // Step 2: Encrypt binary representation of secret message
        val cipherBits = Crypto.encrypt(plainBits)

        // Step 3: Encode encrypted binary representation of secret message into cover text
        LlamaCpp.resetInstance()

        val coverText = when (steganographyMode) {
            SteganographyMode.Arithmetic -> { Arithmetic.encode(context, cipherBits) }
            SteganographyMode.Huffman -> { Huffman.encode(context, cipherBits) }
        }

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
        conversionMode: ConversionMode = Settings.conversionMode,
        steganographyMode: SteganographyMode = Settings.steganographyMode
    ): Boolean {
        // When using UTF-8 encoding, first byte is guaranteed to store first char, no more bytes to consider
        // But when using Arithmetic compression, padding length and padding are stored in first 2 bytes, so consider at least 3 bytes to find first char
        // => Trial-and-error showed that first 4 bytes need to be considered, otherwise Arithmetic decodes incomplete bit sequence to wrong char
        val numberOfCipherBits = if (conversionMode == ConversionMode.UTF8) 8 else 32
        var isFirstMessageOfSplit: Boolean

        // Invert step 3
        LlamaCpp.resetInstance()

        // Wrap this in try-catch because decoding with wrong context is likely to throw exceptions
        val partialCipherBits: ByteArray

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

        // Invert step 2
        val partialPlainBits = Crypto.decrypt(partialCipherBits)

        // Invert step 1
        LlamaCpp.resetInstance()

        val partialPreparedSecretMessage = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.decompress(partialPlainBits) }
            ConversionMode.UTF8 -> { UTF8.decode(partialPlainBits) }
        }

        // Don't invert step 0
        isFirstMessageOfSplit = partialPreparedSecretMessage.startsWith(LlamaCpp.getAsciiStx())

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
     * @param conversionMode Conversion mode, determined by Settings object.
     * @param steganographyMode Steganography mode, determined by Settings object.
     * @param isResumed Boolean that is true if this call of the `decode` function resumes where the last call terminated, false otherwise.
     * @return The secret message.
     */
    fun decode(
        context: String,
        coverText: String,
        conversionMode: ConversionMode = Settings.conversionMode,
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

        // Invert step 2
        val plainBits = Crypto.decrypt(cipherBits)

        // Invert step 1
        if (isResumed) {
            // Restore ctx for decompression
            LlamaCpp.setCtx(ctx = LlamaCpp.getDecompressCtx())
        }
        else {
            // Reset ctx
            LlamaCpp.resetInstance()
        }

        val preparedSecretMessage = when (conversionMode) {
            ConversionMode.Arithmetic -> { Arithmetic.decompress(plainBits, isResumed = isResumed) }
            ConversionMode.UTF8 -> { UTF8.decode(plainBits) }
        }

        // Save ctx for decompression
        LlamaCpp.setDecompressCtx(decompressCtx = LlamaCpp.getCtx())

        // Invert step 0
        val secretMessage = unprepare(preparedSecretMessage)

        return secretMessage
    }

    /**
     * Function to prepare a secret message for binary encoding.
     *
     * Appends the ASCII NUL character to the original secret message. Needed to remove artefacts from greedy sampling after binary decoding.
     *
     * @param secretMessage A secret message.
     * @return The prepared secret message.
     */
    private fun prepare(secretMessage: String): String {
        // Use ASCII {STX, ETX} as {start, stop} signal to avoid collisions with NUL-terminated strings in C++ and with each other when splitting a cover text
        val preparedSecretMessage = LlamaCpp.getAsciiStx() + secretMessage + LlamaCpp.getAsciiEtx()

        return preparedSecretMessage
    }

    /**
     * Function to unprepare a secret message after binary decoding.
     *
     * Strips the ASCII NUL character and everything after it. Therefore removes any artefacts from greedy sampling, rendering the original secret message.
     *
     * @param preparedSecretMessage A prepared secret message.
     * @return The secret message.
     */
    private fun unprepare(preparedSecretMessage: String): String {
        // Remove {start, stop} signal again
        // Split returns list that contains empty strings as first and possibly last elements, is intended behaviour because it interprets {start, stop} signal as delimiter between 2 strings
        val secretMessage = preparedSecretMessage
            .split(LlamaCpp.getAsciiStx(), LlamaCpp.getAsciiEtx())
            .get(1)

        return secretMessage
    }
}