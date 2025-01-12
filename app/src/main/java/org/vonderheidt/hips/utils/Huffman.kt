package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay
import org.vonderheidt.hips.data.Settings

/**
 * Object (i.e. singleton class) that represents steganography using Huffman encoding.
 */
object Huffman {
    /**
     * Function to encode (the encrypted binary representation of) the secret message into a cover text using Huffman encoding.
     *
     * Corresponds to Stegasuras method `encode_huffman` in `huffman_baseline.py`. Parameter `finish_sent` was removed (<=> is now hard coded to true).
     *
     * @param context The context to encode the secret message with.
     * @param cipherBits The encrypted binary representation of the secret message.
     * @return A cover text containing the secret message.
     */
    fun encode(context: String, cipherBits: ByteArray): String {
        // Tokenize context
        val contextTokens = LlamaCpp.tokenize(context)

        // Convert cipher bits to bit string
        val cipherBitString = Format.asBitString(cipherBits)

        // Initialize array to store cover text tokens
        var coverTextTokens = intArrayOf()

        // Initialize variables and flags for loop
        var i = 0
        var isLastSentenceFinished = false

        var isFirstRun = true                   // llama.cpp batch needs to store context tokens in first run, but only last sampled token in subsequent runs
        var sampledToken = -1                   // Will always be overwritten with last cover text token

        // Sample tokens until all of bits of secret message are encoded and last sentence is finished
        while (i < cipherBitString.length || !isLastSentenceFinished) {
            // Huffman sampling to encode bits of secret message into tokens
            if (i < cipherBitString.length) {
                // Call llama.cpp to calculate the logit matrix similar to https://github.com/ggerganov/llama.cpp/blob/master/examples/simple/simple.cpp:
                // Needs only next tokens to be processed to store in a batch, i.e. contextTokens in first run and last sampled token in subsequent runs, rest is managed internally in ctx
                // Only last row of logit matrix is needed as it contains logits corresponding to last token of the prompt
                val logits = LlamaCpp.getLogits(if (isFirstRun) contextTokens else intArrayOf(sampledToken)).last()

                // Get top 2^bitsPerToken logits for last token of prompt (= height of Huffman tree)
                val topLogits = getTopLogits(logits)

                // Construct Huffman tree from top logits
                val huffmanCoding = HuffmanCoding()
                huffmanCoding.buildHuffmanTree(topLogits)
                huffmanCoding.mergeHuffmanNodes()
                val root = huffmanCoding.generateHuffmanCodes()

                // Traverse Huffman tree based on bits of secret message to sample next token, therefore encoding information in it
                var currentNode = root

                // First nodes won't have a token as they were created during the merge step
                while (currentNode.token == null) {
                    // First condition is needed in case (length of cipher bits) % (bits per token) != 0
                    // In last loop of outer while, inner while can cause i to exceed cipherBitString.length
                    // Second condition is only checked if first condition is false, so IndexOutOfBoundsException can't happen
                    if (i >= cipherBitString.length || cipherBitString[i] == '0') {
                        // Asserting left and right child nodes to be not null is safe as Huffman tree isn't traversed further down than bitsPerToken levels
                        currentNode = currentNode.left!!
                    }
                    else {
                        currentNode = currentNode.right!!
                    }

                    // Every time a turn is made when traversing the Huffman tree, another bit is encoded
                    i++
                }

                // Token containing the right bitsPerToken bits of information in its path is now found
                sampledToken = currentNode.token!!

                // Update flag
                isFirstRun = false
            }
            // Greedy sampling to pick most likely token until last sentence is finished
            else {
                // llama.cpp greedy sampler is used for efficiency instead of manually sorting logits descending and picking the first one
                // Input is only last sampled token similar to else case of getLogits input above, as greedy sampling only over gets called after Huffman sampling
                sampledToken = LlamaCpp.sample(sampledToken)

                // Update flag
                isLastSentenceFinished = LlamaCpp.isEndOfSentence(sampledToken)
            }

            // Append last sampled token to cover text tokens
            coverTextTokens += sampledToken
        }

        // Detokenize cover text tokens into cover text to return it
        val coverText = LlamaCpp.detokenize(coverTextTokens)

        return coverText
    }

    /**
     * Function to decode a cover text into (the encrypted binary representation of) the secret message using Huffman decoding.
     *
     * Corresponds to Stegasuras method `decode_huffman` in `huffman_baseline.py`.
     */
    suspend fun decode(context: String, coverText: String, bitsPerToken: Int = Settings.bitsPerToken): ByteArray {
        // Wait 5 seconds
        delay(5000)

        // Return placeholder
        val cipherBits = ByteArray(size = 0)

        return cipherBits
    }

    /**
     * Function to get the top 2^bitsPerToken logits for the last token of the prompt. Keeps track of the corresponding token IDs in a map.
     *
     * Parameter `bits_per_word` from Stegasuras was renamed to `bitsPerToken`.
     *
     * @param logits Logits for the last token of the prompt (= last row of logits matrix).
     * @param bitsPerToken Number of bits to encode/decode per cover text token (= height of Huffman tree). Determined by Settings object.
     * @return Map of top 2^bitsPerToken logits and the corresponding token IDs.
     */
    private fun getTopLogits(logits: FloatArray, bitsPerToken: Int = Settings.bitsPerToken): Map<Int, Float> {
        val topLogits = logits
            .mapIndexed{ token, logit -> token to logit }   // Convert to List<Pair<Int, Float>> so token IDs won't get lost
            .sortedByDescending { it.second }               // Sort pairs descending based on logits
            .take(1 shl bitsPerToken)                    // Take top 2^bitsPerToken pairs
            .toMap()                                        // Convert to Map<Int, Float> for Huffman tree (ensures there can't be any duplicate token IDs)

        return topLogits
    }
}