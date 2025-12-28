#ifndef LLAMACPP_H
#define LLAMACPP_H

#include <jni.h>
#include "llama.h"
#include "common.h"

/**
 * Class that represents llama.cpp.
 */
class LlamaCpp {
private:
    /**
     * Wrapper for the `common_detokenize` function of llama.cpp. Detokenizes a vector of token IDs into a C++ string.
     *
     * Helper for the public `detokenize` function returning a Java string.
     *
     * @param tokens Vector of token IDs.
     * @param ctx Memory address of the context.
     * @return Detokenization as a C++ string.
     */
    static std::string detokenize(llama_tokens tokens, const llama_context* ctx);

    /**
     * Wrapper for the `llama_vocab_is_eog` and `llama_vocab_is_control` functions of llama.cpp. Checks if a token is a special token.
     *
     * @param token Token ID to check.
     * @param model Memory address of the LLM.
     * @return Boolean that is true if the token is special, false otherwise.
     */
    static bool isSpecial(llama_token token, const llama_model* model);

    /**
     * Wrapper for the `llama_vocab_is_eog` function of llama.cpp. Checks if a token is an end-of-generation (eog) token.
     *
     * @param token Token ID to check.
     * @param model Memory address of the LLM.
     * @return Boolean that is true if the token is an eog token, false otherwise.
     */
    static bool isEndOfGeneration(llama_token token, const llama_model* model);

public:
    /**
     * Wrapper for the `common_detokenize` function of llama.cpp. Detokenizes a vector of token IDs into a Java string.
     *
     * @param env The JNI environment.
     * @param tokens Vector of token IDs.
     * @param ctx Memory address of the context.
     * @return Detokenization as a Java string.
     */
    static jstring detokenize(JNIEnv* env, llama_tokens tokens, const llama_context* ctx);

    /**
     * Function to suppress special tokens, i.e. end-of-generation (eog) and control tokens.
     *
     * Suppressing eog tokens is needed to avoid early termination when generating a cover text.
     * Additionally suppressing control tokens is needed to avoid artefacts when generating a conversation of cover texts.
     *
     * @param probabilities Probabilities for the last token of the prompt (= last row of logits matrix after normalization).
     * @param model Memory address of the LLM.
     */
    static void suppressSpecialTokens(float* probabilities, const llama_model* model);

    /**
     * Function to check if a token is the end of a sentence. Needed to complete the last sentence of the cover text.
     *
     * Corresponds to Stegasuras method `is_sent_finish` in `utils.py`.
     *
     * @param token Token ID to check.
     * @param ctx Memory address of the context.
     * @return Boolean that is true if the token ends with `.`, `!` or `?`, false otherwise.
     */
    static bool isEndOfSentence(llama_token token, const llama_context* ctx);

    /**
     * Function to get the end-of-generation (eog) token of the LLM.
     * If the LLM has multiple eog tokens, the first one is returned.
     *
     * @param model Memory address of the LLM.
     * @return ID of the eog token.
     */
    static llama_token getEndOfGeneration(const llama_model* model);

    /**
     * Function to get the token ID of the ASCII NUL character in the vocabulary of the LLM.
     *
     * @param model Memory address of the LLM.
     * @param ctx Memory address of the context.
     * @return Token ID of the ASCII NUL character.
     * @throws std::runtime_error When the LLM vocabulary doesn't contain the ASCII NUL character.
     */
    static llama_token getAsciiNul(const llama_model* model, const llama_context* ctx);

    /**
     * Wrapper for the `llama_vocab_n_tokens` function of llama.cpp. Gets the vocabulary size `n_vocab` of the LLM (i.e. the number of available tokens).
     *
     * @param model Memory address of the LLM.
     * @return Vocabulary size of the LLM.
     */
    static int32_t getVocabSize(const llama_model* model);

    /**
     * Wrapper for the `common_tokenize` function of llama.cpp. Tokenizes a Java string into a vector of token IDs.
     *
     * @param env The JNI environment.
     * @param jString Java string to be tokenized.
     * @param ctx Memory address of the context.
     * @return Tokenization as a vector of token IDs.
     */
    static llama_tokens tokenize(JNIEnv* env, jstring jString, const llama_context* ctx);

    /**
     * Function to calculate the logit matrix (i.e. predictions for every token in the prompt).
     *
     * Only the last row of the `n_tokens` x `n_vocab` matrix is actually needed as it contains the logits corresponding to the last token of the prompt.
     *
     * @param tokens Token IDs from tokenization of the prompt.
     * @param ctx Memory address of the context.
     * @return The last row of the logit matrix.
     */
    static float* getLogits(llama_tokens tokens, llama_context* ctx);
};

#endif
