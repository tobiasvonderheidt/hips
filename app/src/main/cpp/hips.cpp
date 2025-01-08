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
#include <android/log.h>
#include <jni.h>
#include "llama.h"
#include "common.h"

#define TAG "hips.cpp"                                                              // Logcat tag to identify entries from hips.cpp
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)           // Log info message
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)           // Log warning message
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)          // Log error message

/**
 * Function to load the LLM into memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jPath Path to the LLM (.gguf file).
 * @return Memory address of the LLM.
 */
extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadModel(JNIEnv* env, jobject thiz, jstring jPath) {
    // Convert path to LLM from Java string to C++ string using the JNI environment
    // Set isCopy == true to copy Java string so it doesn't get overwritten in memory
    jboolean isCopy = true;
    const char* cppPath = env -> GetStringUTFChars(jPath, &isCopy);

    // Use the LLM with default parameters
    llama_model_params params = llama_model_default_params();

    // Load the LLM into memory and save pointer to it
    llama_model* cppModel = llama_load_model_from_file(cppPath, params);

    // Log success or error message
    // Cast pointer to unsigned long and format it as hex
    if (cppModel != nullptr) {
        LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_loadModel: LLM from %s was loaded into memory at address 0x%lx", cppPath, reinterpret_cast<u_long>(cppModel));
    }
    else {
        LOGe("Java_org_vonderheidt_hips_utils_LlamaCpp_loadModel: LLM from %s could not be loaded into memory (address 0x%lx)", cppPath, reinterpret_cast<u_long>(cppModel));
    }

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
extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadModel(JNIEnv* env, jobject thiz, jlong jModel) {
    // Cast memory address of LLM from Java long to C++ pointer
    auto cppModel = reinterpret_cast<llama_model*>(jModel);

    // Unload LLM from memory
    llama_free_model(cppModel);

    // Log success message
    // Java long is used instead of now invalid C++ pointer, needs to formated as C++ long long to get all 64 bits
    LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_unloadModel: LLM was unloaded from memory address 0x%llx", jModel);
}

/**
 * Function to load the context into memory.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jModel Memory address of the LLM.
 * @return Memory address of the context.
 */
extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadCtx(JNIEnv* env, jobject thiz, jlong jModel) {
    // Similar to loadModel

    // Cast memory address of the LLM from Java long to C++ pointer
    auto cppModel = reinterpret_cast<llama_model*>(jModel);

    // Use default parameters for the context
    llama_context_params params = llama_context_default_params();

    // Create context with the LLM (=> context knows its state) and save pointer to it
    llama_context* cppCtx = llama_new_context_with_model(cppModel, params);

    // Log success or error message
    if (cppCtx != nullptr) {
        LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_loadCtx: Context was loaded into memory at address 0x%lx", reinterpret_cast<u_long>(cppCtx));
    }
    else {
        LOGe("Java_org_vonderheidt_hips_utils_LlamaCpp_loadCtx: Context could not be loaded into memory (address 0x%lx)", reinterpret_cast<u_long>(cppCtx));
    }

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
extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadCtx(JNIEnv* env, jobject thiz, jlong jCtx) {
    // Similar to unloadModel

    // Cast memory address of context from Java long to C++ pointer
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);

    // Unload context from memory
    llama_free(cppCtx);

    // Log success message
    LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_unloadCtx: Context was unloaded from memory address 0x%llx", jCtx);
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
extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadSmpl(JNIEnv* env, jobject thiz) {
    // Similar to loadModel

    // Initialize greedy sampler (no sampler chain needed when using only a single sampler)
    llama_sampler* cppSmpl = llama_sampler_init_greedy();

    // Log success or error message
    if (cppSmpl != nullptr) {
        LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_loadSmpl: Greedy sampler was loaded into memory at address 0x%lx", reinterpret_cast<u_long>(cppSmpl));
    }
    else {
        LOGe("Java_org_vonderheidt_hips_utils_LlamaCpp_loadSmpl: Greedy sampler could not be loaded into memory (address 0x%lx)", reinterpret_cast<u_long>(cppSmpl));
    }

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
extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadSmpl(JNIEnv* env, jobject thiz, jlong jSmpl) {
    // Similar to unloadModel

    // Cast memory address of sampler from Java long to C++ pointer
    auto cppSmpl = reinterpret_cast<llama_sampler*>(jSmpl);

    // Unload sampler from memory
    // llama.cpp docs: "important: do not free if the sampler has been added to a llama_sampler_chain (via llama_sampler_chain_add)" (not the case here)
    llama_sampler_free(cppSmpl);

    // Log success message
    LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_unloadSmpl: Sampler was unloaded from memory address 0x%llx", jSmpl);
}

/**
 * Function to tokenize a string into an array of token IDs.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jString String to be tokenized.
 * @param jCtx Memory address of the context.
 * @return Tokenization as an array of token IDs.
 */
extern "C" JNIEXPORT jintArray JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_tokenize(JNIEnv* env, jobject thiz, jstring jString, jlong jCtx) {
    // Cast memory address of the context from Java long to C++ pointer
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);

    // Convert Java string to be tokenized to C++ string
    jboolean isCopy = true;
    const char* cppString = env -> GetStringUTFChars(jString, &isCopy);

    // Tokenize string, save tokens as llama_tokens (equivalent to std::vector<llama_token>, with llama_token equivalent to int32_t)
    // Hide special tokens to get clean input
    // See common.cpp: common_tokenize(ctx, ...) calls common_tokenize(model, ...), which calls llama_tokenize
    llama_tokens cppTokens = common_tokenize(cppCtx, cppString, false, false);

    // Release C++ string from memory
    env -> ReleaseStringUTFChars(jString, cppString);

    // Initialize Java int array to store token IDs
    jintArray jTokens = env -> NewIntArray(cppTokens.size());

    // Fill the Java array with token IDs and return it
    env -> SetIntArrayRegion(jTokens, 0, cppTokens.size(), reinterpret_cast<const jint*>(cppTokens.data()));

    return jTokens;
}

/**
 * Function to detokenize an array of token IDs into a string.
 *
 * @param env The JNI environment.
 * @param thiz Java object this function was called with.
 * @param jTokens Array of token IDs to be detokenized.
 * @param jCtx Memory address of the context.
 * @return Detokenization as a string.
 */
extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_detokenize(JNIEnv* env, jobject thiz, jintArray jTokens, jlong jCtx) {
    // Cast memory address of the context from Java long to C++ pointer
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);

    // Initialize C++ array for token IDs and fill it
    jsize jTokensSize = env -> GetArrayLength(jTokens);

    llama_tokens cppTokens(jTokensSize);

    env -> GetIntArrayRegion(jTokens, 0, jTokensSize, reinterpret_cast<jint*>(cppTokens.data()));

    // Detokenize array of tokens to C++ string, hide special tokens to get clean output
    // See common.cpp: common_detokenize calls llama_detokenize
    std::basic_string<char> cppString = common_detokenize(cppCtx, cppTokens, false);

    // Convert C++ string to Java string and return it
    jstring jString = env -> NewStringUTF(cppString.c_str());

    return jString;
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
extern "C" JNIEXPORT jint JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_sample(JNIEnv* env, jobject thiz, jint lastToken, jlong jCtx, jlong jSmpl) {
    // Cast memory addresses of context and sampler from Java long to C++ pointers
    // Casting the last token ID from jint to llama_token is not necessary since both is just int32_t
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    auto cppSmpl = reinterpret_cast<llama_sampler*>(jSmpl);

    // Create a batch containing only the last token
    // TODO
    //  llama.cpp docs: "NOTE: this is a helper function to facilitate transition to the new batch API - avoid using it"
    //  But is used like this in https://github.com/ggerganov/llama.cpp/blob/master/examples/simple/simple.cpp
    llama_batch batch = llama_batch_get_one(&lastToken, 1);

    // Run decoder to calculate logits for the next token
    int32_t decode = llama_decode(cppCtx, batch);

    // Log success or error message
    if (decode == 0) {
        LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_sample: decode = %d, success", decode);
    }
    else if (decode == 1) {
        LOGw("Java_org_vonderheidt_hips_utils_LlamaCpp_sample: decode = %d, could not find a KV slot for the batch", decode);
    }
    else {
        LOGe("Java_org_vonderheidt_hips_utils_LlamaCpp_sample: decode = %d, error. the KV cache state is restored to the state before this call", decode);
    }

    // Sample next token from logits with given sampler and return it
    // Again, casting the next token ID is not necessary
    llama_token nextToken = llama_sampler_sample(cppSmpl, cppCtx, -1);

    return nextToken;
}