// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("hips");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("hips")
//      }
//    }

// Notation: <system libs>, "user libs"
#include <jni.h>
#include "llama.h"
#include "common.h"

/**
 * Function to load the LLM into memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jPath Path to the LLM (.gguf file).
 * @return Memory address of the LLM.
 */
extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadModel(JNIEnv* env, jobject /* thiz */, jstring jPath) {
    // Convert path to LLM from Java string to C++ string using the JNI environment
    // Set isCopy == true to copy Java string so it doesn't get overwritten in memory
    jboolean isCopy = true;
    const char* cppPath = env -> GetStringUTFChars(jPath, &isCopy);

    // Use the LLM with default parameters
    llama_model_params params = llama_model_default_params();

    // Load the LLM into memory and save pointer to it
    llama_model* cppModel = llama_model_load_from_file(cppPath, params);

    // Release the memory allocated to the C++ path
    env -> ReleaseStringUTFChars(jPath, cppPath);

    // Cast C++ pointer (64 bit memory address) to Java long (also 64 bit) to return it via JNI
    auto jModel = reinterpret_cast<jlong>(cppModel);

    return jModel;
}

/**
 * Function to unload the LLM from memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jModel Memory address of the LLM.
 */
extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadModel(JNIEnv* /* env */, jobject /* thiz */, jlong jModel) {
    // Cast memory address of LLM from Java long to C++ pointer
    auto cppModel = reinterpret_cast<llama_model*>(jModel);

    // Unload LLM from memory
    llama_model_free(cppModel);
}

/**
 * Function to load the context into memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jModel Memory address of the LLM.
 * @return Memory address of the context.
 */
extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadCtx(JNIEnv* /* env */, jobject /* thiz */, jlong jModel) {
    // Cast memory address of the LLM from Java long to C++ pointer
    auto cppModel = reinterpret_cast<llama_model*>(jModel);

    // Use default parameters for the context
    llama_context_params params = llama_context_default_params();

    // Create context with the LLM (=> context knows its state) and save pointer to it
    llama_context* cppCtx = llama_init_from_model(cppModel, params);

    // Cast C++ pointer to Java long to return it
    auto jCtx = reinterpret_cast<jlong>(cppCtx);

    return jCtx;
}

/**
 * Function to unload the context from memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jCtx Memory address of the context.
 */
extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadCtx(JNIEnv* /* env */, jobject /* thiz */, jlong jCtx) {
    // Cast memory address of context from Java long to C++ pointer
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);

    // Unload context from memory
    llama_free(cppCtx);
}

/**
 * Function to load the sampler into memory.
 *
 * Currently only supports greedy sampler for Huffman encoding.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @return Memory address of the sampler.
 */
extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadSmpl(JNIEnv* /* env */, jobject /* thiz */) {
    // Initialize greedy sampler (no sampler chain needed when using only a single sampler)
    llama_sampler* cppSmpl = llama_sampler_init_greedy();

    // Convert C++ pointer to Java long to return it
    auto jSmpl = reinterpret_cast<jlong>(cppSmpl);

    return jSmpl;
}

/**
 * Function to unload the sampler from memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jSmpl Memory address of the sampler.
 */
extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadSmpl(JNIEnv* /* env */, jobject /* thiz */, jlong jSmpl) {
    // Cast memory address of sampler from Java long to C++ pointer
    auto cppSmpl = reinterpret_cast<llama_sampler*>(jSmpl);

    // Unload sampler from memory
    // llama.cpp docs: "important: do not free if the sampler has been added to a llama_sampler_chain (via llama_sampler_chain_add)" (not the case here)
    llama_sampler_free(cppSmpl);
}

/**
 * Function to get the vocabulary size `n_vocab` of the LLM (i.e. the number of available tokens).
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jModel Memory address of the LLM.
 * @return Vocabulary size of the LLM.
 */
extern "C" JNIEXPORT jint JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_getVocabSize(JNIEnv* /* env */, jobject /* thiz */, jlong jModel) {
    // Cast memory address of LLM from Java long to C++ pointer
    auto cppModel = reinterpret_cast<llama_model*>(jModel);

    // Get vocabulary size and return it, no cast needed as jint is int32_t
    const llama_vocab* vocab = llama_model_get_vocab(cppModel);
    int32_t n_vocab = llama_vocab_n_tokens(vocab);

    return n_vocab;
}

/**
 * Function to detokenize an array of token IDs into a byte array storing a UTF-8 encoded string.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jTokens Array of token IDs to be detokenized.
 * @param jCtx Memory address of the context.
 * @return Detokenization as a byte array storing a UTF-8 encoded string.
 */
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_detokenize(JNIEnv* env, jobject /* thiz */, jintArray jTokens, jlong jCtx) {
    // Cast memory address of the context from Java long to C++ pointer
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);

    // Initialize C++ array for token IDs and fill it
    jsize jTokensSize = env -> GetArrayLength(jTokens);

    llama_tokens cppTokens(jTokensSize);

    env -> GetIntArrayRegion(jTokens, 0, jTokensSize, reinterpret_cast<jint*>(cppTokens.data()));

    // Detokenize array of tokens to C++ string
    // See common.cpp: common_detokenize calls llama_detokenize, with parameters "remove_special = false" hard-coded and "unparse_special = special" passed through
    std::basic_string<char> cppString = common_detokenize(cppCtx, cppTokens, true);

    // Initial solution was to convert cppString to a jstring object using the NewStringUTF function before returning it
    // JNI docs for NewStringUTF say: "Constructs a new java.lang.String object from an array of characters in modified UTF-8 encoding."
    // This crashes when the system prompt tells the LLM to generate emojis
    // Alternatively there is the NewString function, about which JNI docs say: "Constructs a new java.lang.String object from an array of Unicode characters."
    // Looks like it removes need for wrapper function on Kotlin side, but requires conversion on C++ side
    // See https://stackoverflow.com/questions/32205446/getting-true-utf-8-characters-in-java-jni for details

    // Initialize Java byte array to store UTF-8 encoding of the C++ string
    jbyteArray jByteArray = env -> NewByteArray((int32_t) cppString.size());

    // Fill the Java array with the bytes of the C++ string and return it
    env -> SetByteArrayRegion(jByteArray, 0, (int32_t) cppString.size(), reinterpret_cast<const jbyte*>(cppString.data()));

    return jByteArray;
}

/**
 * Function to sample the next token based on the last one.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param lastToken ID of the last token.
 * @param jCtx Memory address of the context.
 * @param jSmpl Memory address of the sampler.
 * @return ID of the next token.
 */
extern "C" JNIEXPORT jint JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_sample(JNIEnv* /* env */, jobject /* thiz */, jint lastToken, jlong jCtx, jlong jSmpl) {
    // Cast memory addresses of context and sampler from Java long to C++ pointers
    // Casting the last token ID from jint to llama_token is not necessary since both is just int32_t
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    auto cppSmpl = reinterpret_cast<llama_sampler*>(jSmpl);

    // Create a batch containing only the last token
    // TODO
    //  llama.cpp docs: "NOTE: this is a helper function to facilitate transition to the new batch API - avoid using it"
    //  But is used like this in https://github.com/ggml-org/llama.cpp/blob/master/examples/simple/simple.cpp
    llama_batch batch = llama_batch_get_one(&lastToken, 1);

    // Check if model architecture is encoder-decoder or decoder-only
    const llama_model* model = llama_get_model(cppCtx);

    if (llama_model_has_encoder(model)) {
        // Run encoder to calculate logits for the next token
        // Return value of llama_encode only indicates success/error, actual result is stored internally in cppCtx
        int32_t encode = llama_encode(cppCtx, batch);
    }

    // Run decoder to calculate logits for the next token
    // Return value of llama_decode only indicates success/error, actual result is stored internally in cppCtx
    int32_t decode = llama_decode(cppCtx, batch);

    // Sample next token from logits with given sampler and return it
    // Again, casting the next token ID is not necessary
    llama_token nextToken = llama_sampler_sample(cppSmpl, cppCtx, -1);

    return nextToken;
}

/**
 * Function to format a message as a llama.cpp chat message so that it can be added to a chat. This involves the following steps:
 * 1. Prepend a special token for the desired role (`system`, `user` or `assistant`).
 * 2. Append a special token to signal the end of the message.
 * 3. If the message is the last in the chat, append the special token for the `assistant` role to signal the LLM that it should generate the next message.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jRole Role the new chat message should be sent as (`system`, `user` or `assistant`).
 * @param jContent Content of the new chat message.
 * @param jAppendAssistant Boolean that is true if the special token for the `assistant` role is to be appended at the end, false otherwise.
 * @param jModel Memory address of the LLM.
 * @return The message formatted as llama.cpp chat message.
 */
extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_addMessage(JNIEnv* env, jobject /* thiz */, jstring jRole, jstring jContent, jboolean jAppendAssistant, jlong jModel) {
    // Mostly follows https://github.com/ggml-org/llama.cpp/blob/master/examples/simple-chat/simple-chat.cpp

    // Convert role and content of the message from Java strings to C++ strings using the JNI environment
    jboolean isCopy = true;
    const char* cppRole = env -> GetStringUTFChars(jRole, &isCopy);
    const char* cppContent = env -> GetStringUTFChars(jContent, &isCopy);

    // Cast appendAssistant boolean
    // static_cast because casting booleans is type safe, unlike reinterpret_cast for casting C++ pointers to Java long
    auto cppAppendAssistant = static_cast<jboolean>(jAppendAssistant);

    // Cast memory addresses of the LLM from Java long to C++ pointers
    auto cppModel = reinterpret_cast<llama_model*>(jModel);

    // Create vector of chars to store the formatted chat
    std::vector<char> formatted;
    // "int prev_len = 0;" isn't overwritten in this implementation

    // Get default chat template of the LLM
    // Defines syntax the LLM uses to differentiate system prompt, user and assistant messages
    const char* tmpl = llama_model_chat_template(cppModel, nullptr);

    // Create chat message from role and content
    llama_chat_message message = {cppRole, cppContent};

    // Create vector of chat messages store the chat messages
    std::vector<llama_chat_message> chat;

    // Append the new message to the chat
    chat.push_back(message);

    // Apply chat template to messages to format them into a single prompt string
    // Last parameter is current size of buffer for formatted string, return value is required size
    int32_t new_len = llama_chat_apply_template(tmpl, chat.data(), chat.size(), cppAppendAssistant, formatted.data(), (int32_t) formatted.size());

    // Check if current size of buffer is enough
    if (new_len > (int) formatted.size()) {
        // Resize buffer if needed
        formatted.resize(new_len);

        // Apply chat template again with resized buffer
        new_len = llama_chat_apply_template(tmpl, chat.data(), chat.size(), cppAppendAssistant, formatted.data(), (int32_t) formatted.size());
    }

    // Extract prompt to generate the response by removing previous messages
    std::string cppPrompt(formatted.begin() /* + prev_len */, formatted.begin() + new_len);

    // Release C++ strings for role and content from memory
    env -> ReleaseStringUTFChars(jRole, cppRole);
    env -> ReleaseStringUTFChars(jContent, cppContent);

    // Convert prompt from C++ string to Java string and return it
    jstring jPrompt = env -> NewStringUTF(cppPrompt.c_str());

    return jPrompt;
}