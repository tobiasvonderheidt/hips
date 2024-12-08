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

    // Unload model from memory
    llama_free_model(cppModel);

    // Log success message
    // Java long is used instead of now invalid C++ pointer, needs to formated as C++ long long to get all 64 bits
    LOGi("Java_org_vonderheidt_hips_utils_LlamaCpp_unloadModel: LLM was unloaded from memory address 0x%llx", jModel);
}