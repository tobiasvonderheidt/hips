package org.vonderheidt.hips.utils

/**
 * Object (i.e. singleton class) to declare Kotlin external functions corresponding to llama.cpp functions.
 */
object LlamaCpp {
    private val path = LLM.getPath()

    /**
     * Wrapper for the `llama_load_model_from_file` function of llama.cpp. Loads the LLM into memory.
     *
     * @param path Path to the LLM (.gguf file).
     * @return Memory address of the LLM.
     */
    external fun loadModel(path: String = this.path): Long
}