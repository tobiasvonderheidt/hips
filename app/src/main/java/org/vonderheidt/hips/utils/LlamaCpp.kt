package org.vonderheidt.hips.utils

/**
 * Object (i.e. singleton class) to declare Kotlin external functions corresponding to llama.cpp functions.
 */
object LlamaCpp {
    private val path = LLM.getPath()

    // Annotate pointers to the LLM and its context as volatile so that r/w to them is atomic and immediately visible to all threads
    // Avoids race conditions, i.e. multiple threads trying to load/unload the LLM or its context simultaneously
    @Volatile
    private var model = 0L

    @Volatile
    private var ctx = 0L

    /**
     * Function to check if LLM has already been loaded into memory.
     *
     * @return Boolean that is true if the LLM is in memory, false otherwise.
     */
    fun isInMemory(): Boolean {
        return model != 0L && ctx != 0L
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
        // Synchronized allows only one thread to execute the code inside {...}, so other threads can't load LLM simultaneously
        synchronized(this) {
            if (!isInMemory()) {
                model = loadModel()
                ctx = loadCtx()
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
                // Unload context first as LLM is needed for context
                unloadCtx()
                ctx = 0L

                unloadModel()
                model = 0L
            }
        }
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

    // Parameter ctx is optional since it has a default value, put it at the end to avoid conflicts

    /**
     * Wrapper for the `common_tokenize` function of llama.cpp. Tokenizes a string into an array of token IDs.
     *
     * @param string String to be tokenized.
     * @param ctx Memory address of the context.
     * @return Tokenization as an array of token IDs.
     */
    external fun tokenize(string: String, ctx: Long = this.ctx): IntArray
}