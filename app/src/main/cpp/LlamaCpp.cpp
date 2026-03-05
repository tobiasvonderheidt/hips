#include "LlamaCpp.h"
#include <vector>
#include <string>
#include <algorithm>
#include <android/log.h>

#define TAG "LlamaCpp.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

std::string LlamaCpp::detokenize(const llama_tokens& tokens, const llama_context* ctx) {
    if (tokens.empty()) return "";
    return common_detokenize(const_cast<llama_context*>(ctx), tokens, true);
}

bool LlamaCpp::isSpecial(llama_token token, const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    return llama_vocab_is_eog(vocab, token) || llama_vocab_is_control(vocab, token);
}

bool LlamaCpp::isEndOfGeneration(llama_token token, const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    return llama_vocab_is_eog(vocab, token);
}

jstring LlamaCpp::detokenize(JNIEnv* env, const llama_tokens& tokens, const llama_context* ctx) {
    std::string cppString = LlamaCpp::detokenize(tokens, ctx);
    jbyteArray bytes = env->NewByteArray((jsize)cppString.size());
    env->SetByteArrayRegion(bytes, 0, (jsize)cppString.size(), reinterpret_cast<const jbyte*>(cppString.c_str()));
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID methodId = env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    jstring result = (jstring)env->NewObject(stringClass, methodId, bytes, encoding);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(encoding);
    return result;
}

void LlamaCpp::suppressSpecialTokens(float* probabilities, const llama_model* model) {
    int32_t n_vocab = LlamaCpp::getVocabSize(model);
    for (llama_token token = 0; token < n_vocab; token++) {
        if (LlamaCpp::isSpecial(token, model)) {
            probabilities[token] = 0.0f;
        }
    }
}

void LlamaCpp::suppressSpecialTokensLogits(float* logits, const llama_model* model) {
    int32_t n_vocab = LlamaCpp::getVocabSize(model);
    for (llama_token token = 0; token < n_vocab; token++) {
        if (LlamaCpp::isSpecial(token, model)) {
            logits[token] = -INFINITY;
        }
    }
}

bool LlamaCpp::isEndOfSentence(llama_token token, const llama_context* ctx) {
    const llama_model* model = llama_get_model(ctx);
    // 1. Stop on EOG or Control tokens
    if (llama_vocab_is_eog(llama_model_get_vocab(model), token)) return true;
    
    std::string detok = LlamaCpp::detokenize({token}, ctx);
    if (detok.empty()) return false;

    // 2. Stop on Newlines (typical for short chat messages)
    if (detok.find('\n') != std::string::npos) return true;

    // 3. Trim trailing whitespace and check for punctuation
    detok.erase(std::find_if(detok.rbegin(), detok.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), detok.end());

    if (detok.empty()) return false;
    char last = detok.back();
    return last == '.' || last == '?' || last == '!';
}

llama_token LlamaCpp::getEndOfGeneration(const llama_model* model) {
    int32_t n_vocab = LlamaCpp::getVocabSize(model);
    for (int32_t token = 0; token < n_vocab; token++) {
        if (LlamaCpp::isEndOfGeneration(token, model)) {
            return (llama_token)token;
        }
    }
    return 0;
}

llama_token LlamaCpp::getAsciiNul(const llama_model* model, const llama_context* ctx) {
    int32_t n_vocab = LlamaCpp::getVocabSize(model);
    for (int32_t token = 0; token < n_vocab; token++) {
        if (LlamaCpp::detokenize(std::vector<llama_token>{(llama_token)token}, ctx).find('\0') != std::string::npos) {
            return (llama_token)token;
        }
    }
    throw std::runtime_error("LLM vocabulary doesn't contain ASCII NUL character");
}

int32_t LlamaCpp::getVocabSize(const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    return llama_vocab_n_tokens(vocab);
}

llama_tokens LlamaCpp::tokenize(JNIEnv* env, jstring jString, const llama_context* ctx) {
    if (jString == nullptr) return {};
    const char* cppString = env->GetStringUTFChars(jString, nullptr);
    llama_tokens tokens = common_tokenize(const_cast<llama_context*>(ctx), cppString, false, true);
    env->ReleaseStringUTFChars(jString, cppString);
    return tokens;
}

float* LlamaCpp::getLogits(const llama_tokens& tokens, llama_context* ctx) {
    if (ctx == nullptr) return nullptr;
    int32_t n_tokens = (int32_t)tokens.size();
    if (n_tokens == 0) return llama_get_logits(const_cast<llama_context*>(ctx));
    llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(tokens.data()), n_tokens);
    const llama_model* model = llama_get_model(ctx);
    if (llama_model_has_encoder(model)) llama_encode(const_cast<llama_context*>(ctx), batch);
    int res = llama_decode(const_cast<llama_context*>(ctx), batch);
    if (res != 0) {
        LOGe("getLogits: llama_decode failed with code %d", res);
        return nullptr;
    }
    return llama_get_logits(const_cast<llama_context*>(ctx));
}
