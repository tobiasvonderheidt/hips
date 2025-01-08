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
        synchronized(this) {
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

        synchronized(this) {
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

        synchronized(this) {
            if (ctx != 0L) {
                unloadCtx()
                ctx = 0L

                ctx = loadCtx()
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
     * Wrapper for the `llama_load_model_from_file` function of llama.cpp. Loads the LLM into memory.
     *
     * @param path Path to the LLM (.gguf file).
     * @return Memory address of the LLM.
     */
    private external fun loadModel(path: String = this.path): Long

    /**
     * Wrapper for the `llama_free_model` function of llama.cpp. Unloads the LLM from memory.
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
     * Currently only supports greedy sampler for Huffman encoding.
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
     * Wrapper for the `llama_sampler_sample` function of llama.cpp. Samples the next token based on the last one.
     *
     * @param lastToken ID of the last token.
     * @param ctx Memory address of the context.
     * @return ID of the next token.
     */
    external fun sample(lastToken: Int, ctx: Long = this.ctx, smpl: Long = this.smpl): Int
}