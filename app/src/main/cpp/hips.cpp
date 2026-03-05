#include <android/log.h>
#include <jni.h>
#include <thread>
#include <algorithm>
#include <vector>
#include "llama.h"
#include "common.h"
#include "LlamaCpp.h"

#define TAG "hips.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// JNI Entry points for org.vonderheidt.hips.utils.LlamaCpp

extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadModel(JNIEnv* env, jobject /* thiz */, jstring jPath) {
    jboolean isCopy = true;
    const char* cppPath = env->GetStringUTFChars(jPath, &isCopy);
    llama_model_params params = llama_model_default_params();
    params.use_mmap = false; // Disable mmap for emulator stability
    llama_model* cppModel = llama_model_load_from_file(cppPath, params);
    if (cppModel != nullptr) {
        LOGi("Model loaded successfully");
    } else {
        LOGe("Failed to load model from %s", cppPath);
    }
    env->ReleaseStringUTFChars(jPath, cppPath);
    return reinterpret_cast<jlong>(cppModel);
}

extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadModel(JNIEnv* /* env */, jobject /* thiz */, jlong jModel) {
    if (jModel) {
        llama_model_free(reinterpret_cast<llama_model*>(jModel));
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadCtx(JNIEnv* /* env */, jobject /* thiz */, jlong jModel) {
    auto cppModel = reinterpret_cast<llama_model*>(jModel);
    if (!cppModel) return 0;
    llama_context_params params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 512;
    // LIMIT THREADS: Using too many threads freezes the emulator.
    uint32_t n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency() / 2));
    params.n_threads = n_threads;
    params.n_threads_batch = n_threads;
    llama_context* cppCtx = llama_init_from_model(cppModel, params);
    return reinterpret_cast<jlong>(cppCtx);
}

extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadCtx(JNIEnv* /* env */, jobject /* thiz */, jlong jCtx) {
    if (jCtx) {
        llama_free(reinterpret_cast<llama_context*>(jCtx));
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_loadSmpl(JNIEnv* /* env */, jobject /* thiz */) {
    return reinterpret_cast<jlong>(llama_sampler_init_greedy());
}

extern "C" JNIEXPORT void JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_unloadSmpl(JNIEnv* /* env */, jobject /* thiz */, jlong jSmpl) {
    if (jSmpl) {
        llama_sampler_free(reinterpret_cast<llama_sampler*>(jSmpl));
    }
}

extern "C" JNIEXPORT jint JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_getVocabSize(JNIEnv* /* env */, jobject /* thiz */, jlong jModel) {
    return LlamaCpp::getVocabSize(reinterpret_cast<const llama_model*>(jModel));
}

extern "C" JNIEXPORT jintArray JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_tokenize(JNIEnv* env, jobject /* thiz */, jstring jString, jlong jCtx) {
    llama_tokens tokens = LlamaCpp::tokenize(env, jString, reinterpret_cast<const llama_context*>(jCtx));
    jintArray jTokens = env->NewIntArray((jsize)tokens.size());
    env->SetIntArrayRegion(jTokens, 0, (jsize)tokens.size(), reinterpret_cast<const jint*>(tokens.data()));
    return jTokens;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_detokenize(JNIEnv* env, jobject /* thiz */, jintArray jTokens, jlong jCtx) {
    jsize len = env->GetArrayLength(jTokens);
    llama_tokens tokens(len);
    env->GetIntArrayRegion(jTokens, 0, len, reinterpret_cast<jint*>(tokens.data()));
    std::string cppString = common_detokenize(reinterpret_cast<const llama_context*>(jCtx), tokens, true);
    jbyteArray jBytes = env->NewByteArray((jsize)cppString.size());
    env->SetByteArrayRegion(jBytes, 0, (jsize)cppString.size(), reinterpret_cast<const jbyte*>(cppString.c_str()));
    return jBytes;
}

extern "C" JNIEXPORT jboolean JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_isSpecial(JNIEnv* /* env */, jobject /* thiz */, jint token, jlong jModel) {
    return LlamaCpp::isSpecial(token, reinterpret_cast<const llama_model*>(jModel));
}

extern "C" JNIEXPORT jboolean JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_isEndOfGeneration(JNIEnv* /* env */, jobject /* thiz */, jint token, jlong jModel) {
    return LlamaCpp::isEndOfGeneration(token, reinterpret_cast<const llama_model*>(jModel));
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_getLogits(JNIEnv* env, jobject /* thiz */, jintArray jTokens, jlong jCtx) {
    jsize len = env->GetArrayLength(jTokens);
    llama_tokens tokens(len);
    env->GetIntArrayRegion(jTokens, 0, len, reinterpret_cast<jint*>(tokens.data()));
    
    auto ctx = reinterpret_cast<llama_context*>(jCtx);
    float* logits = LlamaCpp::getLogits(tokens, ctx);
    if (!logits) return nullptr;

    int32_t n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(llama_get_model(ctx)));
    jobjectArray jLogits = env->NewObjectArray(1, env->FindClass("[F"), nullptr);
    jfloatArray row = env->NewFloatArray(n_vocab);
    env->SetFloatArrayRegion(row, 0, n_vocab, logits);
    env->SetObjectArrayElement(jLogits, 0, row);
    env->DeleteLocalRef(row);
    return jLogits;
}

extern "C" JNIEXPORT jint JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_sample(JNIEnv* /* env */, jobject /* thiz */, jint lastToken, jlong jCtx, jlong jSmpl) {
    auto ctx = reinterpret_cast<llama_context*>(jCtx);
    auto smpl = reinterpret_cast<llama_sampler*>(jSmpl);
    llama_batch batch = llama_batch_get_one(&lastToken, 1);
    llama_decode(ctx, batch);
    return llama_sampler_sample(smpl, ctx, -1);
}

extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_LlamaCpp_addMessage(JNIEnv* env, jobject /* thiz */, jstring jRole, jstring jContent, jboolean jAppendAssistant, jlong jModel) {
    const char* cppRole = env->GetStringUTFChars(jRole, nullptr);
    const char* cppContent = env->GetStringUTFChars(jContent, nullptr);
    auto model = reinterpret_cast<llama_model*>(jModel);

    std::vector<char> formatted;
    const char* tmpl = llama_model_chat_template(model, nullptr);
    llama_chat_message message = {cppRole, cppContent};
    std::vector<llama_chat_message> chat = {message};

    int32_t new_len = llama_chat_apply_template(tmpl, chat.data(), chat.size(), (bool)jAppendAssistant, nullptr, 0);
    formatted.resize(new_len + 1);
    llama_chat_apply_template(tmpl, chat.data(), chat.size(), (bool)jAppendAssistant, formatted.data(), formatted.size());

    env->ReleaseStringUTFChars(jRole, cppRole);
    env->ReleaseStringUTFChars(jContent, cppContent);

    return env->NewStringUTF(formatted.data());
}
