#include "LlamaCpp.h"
#include <vector>
#include <string>
#include <algorithm>
#include <android/log.h>

#define TAG "LlamaCpp.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

std::string LlamaCpp::detokenize(const llama_tokens& tokens, const llama_context* ctx, bool special) {
    if (tokens.empty()) return "";
    return common_detokenize(const_cast<llama_context*>(ctx), tokens, special);
}

bool LlamaCpp::isSpecial(llama_token token, const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    return llama_vocab_is_eog(vocab, token) || llama_vocab_is_control(vocab, token);
}

bool LlamaCpp::isEndOfGeneration(llama_token token, const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    return llama_vocab_is_eog(vocab, token);
}

jstring LlamaCpp::detokenize(JNIEnv* env, const llama_tokens& tokens, const llama_context* ctx, bool special) {
    std::string cppString = LlamaCpp::detokenize(tokens, ctx, special);
    return env->NewStringUTF(cppString.c_str());
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
    if (llama_vocab_is_eog(llama_model_get_vocab(model), token)) return true;
    
    std::string detok = LlamaCpp::detokenize({token}, ctx, true);
    if (detok.empty()) return false;
    if (detok.find('\n') != std::string::npos) return true;

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
        std::string detok = LlamaCpp::detokenize({(llama_token)token}, ctx, true);
        for (char c : detok) {
            if (c == '\0') return (llama_token)token;
        }
    }
    return 0;
}

int32_t LlamaCpp::getVocabSize(const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    return llama_vocab_n_tokens(vocab);
}

llama_tokens LlamaCpp::tokenize(JNIEnv* env, std::string str, const llama_context* ctx) {
    return common_tokenize(const_cast<llama_context*>(ctx), str, false, true);
}

llama_tokens LlamaCpp::tokenize(JNIEnv* env, jstring jString, const llama_context* ctx) {
    if (jString == nullptr) return {};
    const char* cppString = env->GetStringUTFChars(jString, nullptr);
    llama_tokens tokens = common_tokenize(const_cast<llama_context*>(ctx), cppString, false, true);
    env->ReleaseStringUTFChars(jString, cppString);
    return tokens;
}

float* LlamaCpp::getLogits(const llama_tokens& tokens, llama_context* ctx, int n_past) {
    if (ctx == nullptr || tokens.empty()) return nullptr;
    
    int32_t n_tokens = (int32_t)tokens.size();
    
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens; 
    
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = n_past + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_tokens - 1);
    }
    
    const llama_model* model = llama_get_model(ctx);
    if (llama_model_has_encoder(model)) llama_encode(const_cast<llama_context*>(ctx), batch);
    
    int res = llama_decode(const_cast<llama_context*>(ctx), batch);
    llama_batch_free(batch);
    
    if (res != 0) {
        LOGe("getLogits: llama_decode failed with code %d", res);
        return nullptr;
    }
    
    return llama_get_logits(const_cast<llama_context*>(ctx));
}
