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