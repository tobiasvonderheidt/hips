package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Message
import org.vonderheidt.hips.data.Settings
import org.vonderheidt.hips.data.User

/**
 * Object (i.e. singleton class) to declare Kotlin external functions corresponding to llama.cpp functions.
 */
object LlamaCpp {
    private val path = LLM.getPath()

    // Annotate pointers to the LLM, its context and sampler as volatile so that r/w to them is atomic and immediately visible to all threads
    // Avoids race conditions, i.e. multiple threads trying to load/unload the LLM, its context or sampler simultaneously
    @Volatile
    private var model = 0L

    @Volatile
    private var ctx = 0L

    @Volatile
    private var smpl = 0L

    /**
     * Function to check if LLM has already been loaded into memory.
     *
     * @return Boolean that is true if the LLM is in memory, false otherwise.
     */
    fun isInMemory(): Boolean {
        return model != 0L
                && ctx != 0L
                && smpl != 0L
    }

    /**
     * Function to start the instance of the LLM in a thread-safe manner.
     *
     * Does not return a pointer to the LLM as the LlamaCpp object already stores this internally.
     */
    fun startInstance() {
        // If the LLM is already loaded, there is nothing to do
        if (isInMemory()) {
            return
        }

        // Otherwise, load the LLM and store its memory address
        // Synchronized allows only one thread to execute the code inside {...}, so other threads can't load the LLM simultaneously
        synchronized(lock = this) {
            if (!isInMemory()) {
                model = loadModel()
                ctx = loadCtx()
                smpl = loadSmpl()
            }
        }
    }

    /**
     * Function to stop the instance of the LLM in a thread-safe manner.
     */
    fun stopInstance() {
        // Mirrors startInstance
        if (!isInMemory()) {
            return
        }

        synchronized(lock = this) {
            if (isInMemory()) {
                unloadSmpl()
                smpl = 0L

                // Unload context first as LLM is needed for context
                unloadCtx()
                ctx = 0L

                unloadModel()
                model = 0L
            }
        }
    }

    /**
     * Function to reset the instance of the LLM in a thread-safe manner.
     *
     * Needed to ensure reproducible results when switching between encode/decode mode or when encoding/decoding multiple times sequentially.
     */
    fun resetInstance() {
        // Mirrors startInstance
        if (ctx == 0L) {
            return
        }

        synchronized(lock = this) {
            if (ctx != 0L) {
                unloadCtx()
                ctx = 0L

                ctx = loadCtx()
            }
        }
    }

    /**
     * Wrapper for the `common_detokenize` function of llama.cpp. Detokenizes an array of token IDs into a string.
     *
     * @param tokens Array of token IDs.
     * @return Detokenization as a string.
     */
    fun detokenize(tokens: IntArray): String {
        // Detokenize tokens into byte array storing UTF-8 encoded string first to bypass JNI errors
        val byteArray = detokenize(tokens, ctx)

        // Convert UTF-8 encoded string to Java/Kotlin string
        val string = String(bytes = byteArray, charset = Charsets.UTF_8)

        return string
    }

    /**
     * Function to suppress special tokens, i.e. eog (end-of-generation) and control tokens.
     *
     * Suppressing eog tokens is needed to avoid early termination when generating a cover text.
     *
     * Additionally suppressing control tokens is beneficial because the cover text then can't contain any invisible tokens.
     * This ensures integrity when using a non-digital communication medium.
     *
     * @param logits Logits for the last token of the prompt (= last row of logits matrix).
     */
    fun suppressSpecialTokens(logits: FloatArray) {
        // Suppress special tokens by setting their logits to negative values
        for (token in logits.indices) {
            if (isSpecial(token)) {
                logits[token] = -100f
            }
        }
    }

    /**
     * Function to check if a token is the end of a sentence. Needed to complete the last sentence of the cover text.
     *
     * Corresponds to Stegasuras method `is_sent_finish` in `utils.py`.
     *
     * @param token Token ID to check.
     * @return Boolean that is true if the token ends with `.`, `!` or `?` or an emoji, false otherwise.
     */
    fun isEndOfSentence(token: Int): Boolean {
        // Detokenize the token and check if it ends with a punctuation mark or an emoji (covers "?" vs " ?" etc)
        val detokenization = detokenize(intArrayOf(token))

        val isSentenceFinished = detokenization.endsWith(".")
                || detokenization.endsWith("!")
                || detokenization.endsWith("?")
                || detokenization.endsWithEmoji()

        return isSentenceFinished
    }

    /**
     * Function to check if `this` string ends with an emoji.
     *
     * Helper for the `isEndOfSentence` function. May miss some emojis as it relies on a regular expression.
     *
     * @return Boolean that is true if `this` string ends with an emoji, false otherwise.
     */
    private fun String.endsWithEmoji(): Boolean {
        // Most emojis are classified as "Symbols, other" (So) in Unicode, try to find them via regular expression
        val endsWithEmoji = this.isNotEmpty() && Regex("\\p{So}").containsMatchIn(this.takeLast(1))

        return endsWithEmoji
    }

    /**
     * Function to get the end-of-generation (eog) token of the LLM.
     * If the LLM has multiple eog tokens, the first one is returned.
     *
     * @return ID of the eog token.
     */
    fun getEndOfGeneration(): Int {
        var eogTokens = intArrayOf()

        for (token in 0 until getVocabSize()) {
            if (isEndOfGeneration(token)) {
                eogTokens += token
            }
        }

        return eogTokens.first()
    }

    /**
     * Function to format a list of messages as a llama.cpp chat (i.e. apply the chat template of the LLM).
     * Creates the context needed to do steganography encoding/decoding in a conversation.
     *
     * Closely related to the demo implementation of a conversation between Alice and Bob, as roles are assigned to messages based on the `isAlice` parameter:
     * - When encoding, this means the LLM always takes on the role of the user to generate a cover text for.
     * - When decoding, this means the state right before/during encoding is reproduced.
     *
     * Effectively, the roles are constantly switched to make the LLM talk to itself without knowing it.
     * Roles don't need to be strictly alternating, multiple consecutive messages from the same role are fine.
     *
     * @param priorMessages The list of messages prior to the one being encoded/decoded.
     * @param isAlice Boolean that is true if the LLM takes on the role of Alice, false otherwise.
     * @param numberOfMessages Number of messages from `priorMessages` to use as context. Determined by Settings object.
     * @return Context string for steganography encoding/decoding containing the messages formatted as chat.
     */
    fun formatChat(priorMessages: List<Message>, isAlice: Boolean, numberOfMessages: Int = if (Settings.numberOfMessages > 0) Settings.numberOfMessages else priorMessages.size): String {
        // Always add system prompt to chat first
        // Append special token for the assistant role if there are no other messages yet
        var context = addMessage(role = Role.System.name, content = Settings.systemPrompt, appendAssistant = priorMessages.isEmpty())

        // Only use the last numberOfMessages messages as context
        for (priorMessage in priorMessages.takeLast(numberOfMessages)) {
            // Assign assistant/user roles to messages based on isAlice
            val priorMessageRole = if (isAlice) {
                // Alice is assistant, Bob is user
                if (priorMessage.senderID == User.Alice.id) { Role.Assistant.name }
                else { Role.User.name }
            }
            else {
                // Alice is user, Bob is assistant
                if (priorMessage.senderID == User.Alice.id) { Role.User.name }
                else { Role.Assistant.name }
            }

            // Add message to chat
            // Append special token for the assistant role if current message is end of the context
            context += addMessage(role = priorMessageRole, content = priorMessage.content, appendAssistant = priorMessage == priorMessages.last())
        }

        return context
    }

    // Declare the native methods called via JNI as Kotlin external functions

    /**
     * Wrapper for the `llama_model_load_from_file` function of llama.cpp. Loads the LLM into memory.
     *
     * @param path Path to the LLM (.gguf file).
     * @return Memory address of the LLM.
     */
    private external fun loadModel(path: String = this.path): Long

    /**
     * Wrapper for the `llama_model_free` function of llama.cpp. Unloads the LLM from memory.
     *
     * @param model Memory address of the LLM.
     */
    private external fun unloadModel(model: Long = this.model)

    /**
     * Wrapper for the `llama_new_context_with_model` function of llama.cpp. Loads the context into memory.
     *
     * @param model Memory address of the LLM.
     * @return Memory address of the context.
     */
    private external fun loadCtx(model: Long = this.model): Long

    /**
     * Wrapper for the `llama_free` function of llama.cpp. Unloads the context from memory.
     *
     * @param ctx Memory address of the context.
     */
    private external fun unloadCtx(ctx: Long = this.ctx)

    /**
     * Wrapper for the `llama_sampler_init_*` functions of llama.cpp. Loads the sampler into memory.
     *
     * Currently only supports greedy sampler.
     *
     * @return Memory address of the sampler.
     */
    private external fun loadSmpl(): Long

    /**
     * Wrapper for the `llama_sampler_free` function of llama.cpp. Unloads the sampler from memory.
     *
     * @param smpl Memory address of the sampler.
     */
    private external fun unloadSmpl(smpl: Long = this.smpl)

    // Parameter ctx is optional since it has a default value, put it at the end to avoid conflicts

    /**
     * Wrapper for the `llama_vocab_n_tokens` function of llama.cpp. Gets the vocabulary size `n_vocab` of the LLM (i.e. the number of available tokens).
     *
     * @param model Memory address of the LLM.
     * @return Vocabulary size of the LLM.
     */
    external fun getVocabSize(model: Long = this.model): Int

    /**
     * Wrapper for the `common_tokenize` function of llama.cpp. Tokenizes a string into an array of token IDs.
     *
     * @param string String to be tokenized.
     * @param ctx Memory address of the context.
     * @return Tokenization as an array of token IDs.
     */
    external fun tokenize(string: String, ctx: Long = this.ctx): IntArray

    /**
     * Wrapper for the `common_detokenize` function of llama.cpp. Detokenizes an array of token IDs into a byte array storing a UTF-8 encoded string.
     *
     * Helper for the public `detokenize` function returning a string. Bypasses JNI errors caused by different character encodings.
     *
     * @param tokens Array of token IDs.
     * @param ctx Memory address of the context.
     * @return Detokenization as a byte array storing a UTF-8 encoded string.
     */
    private external fun detokenize(tokens: IntArray, ctx: Long = this.ctx): ByteArray

    /**
     * Wrapper for the `llama_vocab_is_eog` and `llama_vocab_is_control` functions of llama.cpp. Checks if a token is a special token.
     *
     * @param token Token ID to check.
     * @param model Memory address of the LLM.
     * @return Boolean that is true if the token is special, false otherwise.
     */
    private external fun isSpecial(token: Int, model: Long = this.model): Boolean

    /**
     * Wrapper for the `llama_vocab_is_eog` function of llama.cpp. Checks if a token is an end-of-generation (eog) token.
     *
     * @param token Token ID to check.
     * @param model Memory address of the LLM.
     * @return Boolean that is true if the token is an eog token, false otherwise.
     */
    private external fun isEndOfGeneration(token: Int, model: Long = this.model): Boolean

    /**
     * Wrapper for the `llama_get_logits` function of llama.cpp. Calculates the logit matrix (i.e. predictions for every token in the prompt).
     *
     * Only the last row of the `n_tokens` x `n_vocab` matrix is actually needed as it contains the logits corresponding to the last token of the prompt.
     *
     * @param tokens Token IDs from tokenization of the prompt.
     * @param ctx Memory address of the context.
     * @return The logit matrix.
     */
    external fun getLogits(tokens: IntArray, ctx: Long = this.ctx): Array<FloatArray>

    /**
     * Wrapper for the `llama_sampler_sample` function of llama.cpp. Samples the next token based on the last one.
     *
     * @param lastToken ID of the last token.
     * @param ctx Memory address of the context.
     * @return ID of the next token.
     */
    external fun sample(lastToken: Int, ctx: Long = this.ctx, smpl: Long = this.smpl): Int

    /**
     * Wrapper for the `llama_chat_apply_template` function of llama.cpp. Formats a message as a llama.cpp chat message so that it can be added to a chat.
     * This involves the following steps:
     * 1. Prepend a special token for the desired role (`system`, `user` or `assistant`).
     * 2. Append a special token to signal the end of the message.
     * 3. If the message is the last in the chat, append the special token for the `assistant` role to signal the LLM that it should generate the next message.
     *
     * @param role Role the new chat message should be sent as (`system`, `user` or `assistant`).
     * @param content Content of the new chat message.
     * @param appendAssistant Boolean that is true if the special token for the `assistant` role is to be appended at the end, false otherwise.
     * @param model Memory address of the LLM.
     * @return The message formatted as llama.cpp chat message.
     */
    private external fun addMessage(role: String, content: String, appendAssistant: Boolean, model: Long = this.model): String
}