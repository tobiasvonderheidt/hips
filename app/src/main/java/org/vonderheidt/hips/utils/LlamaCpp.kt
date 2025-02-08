package org.vonderheidt.hips.utils

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
     * @return Boolean that is true if the token ends with `.`, `?` or `!`, false otherwise.
     */
    fun isEndOfSentence(token: Int): Boolean {
        // Detokenize the token and check if it ends with a punctuation mark (covers "?" vs " ?" etc)
        val detokenization = detokenize(intArrayOf(token))

        val isSentenceFinished = detokenization.endsWith(".")
                || detokenization.endsWith("!")
                || detokenization.endsWith("?")

        return isSentenceFinished
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
     * Wrapper for the `common_tokenize` function of llama.cpp. Tokenizes a string into an array of token IDs.
     *
     * @param string String to be tokenized.
     * @param ctx Memory address of the context.
     * @return Tokenization as an array of token IDs.
     */
    external fun tokenize(string: String, ctx: Long = this.ctx): IntArray

    /**
     * Wrapper for the `common_detokenize` function of llama.cpp. Detokenizes an array of token IDs into a string.
     *
     * @param tokens Array of token IDs.
     * @param ctx Memory address of the context.
     * @return Detokenization as a string.
     */
    external fun detokenize(tokens: IntArray, ctx: Long = this.ctx): String

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
     * Wrapper for the `llama_vocab_is_eog` and `llama_vocab_is_control` functions of llama.cpp. Checks if a token is a special token.
     *
     * @param token Token ID to check.
     * @param model Memory address of the LLM.
     * @return Boolean that is true if the token is special, false otherwise.
     */
    private external fun isSpecial(token: Int, model: Long = this.model): Boolean

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