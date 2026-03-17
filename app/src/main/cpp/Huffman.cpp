#include "Huffman.h"
#include "common.h"
#include "HuffmanCoding.h"
#include "HuffmanNode.h"
#include "Format.h"
#include "LlamaCpp.h"
#include "Statistics.h"
#include <cmath>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>

#define TAG "Huffman.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Huffman_encodeNative(JNIEnv* env, jobject /* thiz */, jbyteArray jContext, jbyteArray jCipherBits, jint jBitsPerToken, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return Format::asByteArray(env, std::string(""));
    
    const llama_model* model = llama_get_model(cppCtx);

    std::string cppContext = Format::asString(env, jContext);
    llama_tokens contextTokens = LlamaCpp::tokenize(env, cppContext, cppCtx);
    std::vector<bool> cppCipherBits = Format::asBitVector(env, jCipherBits);

    llama_tokens coverTextTokens;
    int i = 0;
    bool isLastSentenceFinished = false;
    bool isFirstRun = true;
    llama_token sampledToken = -1;

    const int MAX_TOKENS = 256;

    while ((i < (int)cppCipherBits.size() || !isLastSentenceFinished) && coverTextTokens.size() < MAX_TOKENS) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{sampledToken};
        
        int n_past = isFirstRun ? 0 : (int)(contextTokens.size() + coverTextTokens.size() - 1);
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx, n_past);
        
        if (rawLogits == nullptr) break;

        int32_t n_vocab = LlamaCpp::getVocabSize(model);
        std::vector<float> logits(n_vocab);
        std::copy(rawLogits, rawLogits + n_vocab, logits.begin());

        float* probabilities = Statistics::softmax(logits.data(), model);
        LlamaCpp::suppressSpecialTokens(probabilities, model);

        if (i < (int)cppCipherBits.size()) {
            std::vector<std::pair<llama_token, float>> allProbs;
            allProbs.reserve(n_vocab);
            for(int32_t v=0; v<n_vocab; v++) allProbs.emplace_back(v, probabilities[v]);

            std::sort(allProbs.begin(), allProbs.end(), [](const auto& a, const auto& b) {
                if (std::abs(a.second - b.second) > 1e-9f) return a.second > b.second;
                return a.first < b.first;
            });

            std::vector<std::pair<llama_token, float>> topProbs;
            float cumP = 0.0f;
            for (const auto& p : allProbs) {
                if (p.second < 0.01f && topProbs.size() >= 2) break; 
                topProbs.push_back(p);
                cumP += p.second;
                if (cumP >= 0.9f && topProbs.size() >= 2) break;
            }

            HuffmanCoding huffmanCoding = HuffmanCoding();
            huffmanCoding.buildHuffmanTree(topProbs);
            huffmanCoding.mergeHuffmanNodes();
            HuffmanNode* root = huffmanCoding.generateHuffmanCodes();

            HuffmanNode* currentNode = root;
            while (currentNode->token == -1) {
                if (i >= (int)cppCipherBits.size() || cppCipherBits[i] == 0) {
                    currentNode = currentNode->left;
                } else {
                    currentNode = currentNode->right;
                }
                if (i < (int)cppCipherBits.size()) i++;
            }
            sampledToken = currentNode->token;
            isFirstRun = false;
            if (i >= (int)cppCipherBits.size() && LlamaCpp::isEndOfSentence(sampledToken, cppCtx)) {
                isLastSentenceFinished = true;
            }
        }
        else {
            sampledToken = Huffman::getTopProbabilities(probabilities, 0, model).begin()->first;
            isLastSentenceFinished = LlamaCpp::isEndOfSentence(sampledToken, cppCtx);
        }

        coverTextTokens.push_back(sampledToken);
        if (LlamaCpp::isEndOfGeneration(sampledToken, model)) break;
    }

    std::string resultStr = LlamaCpp::detokenize(coverTextTokens, cppCtx, true);
    return Format::asByteArray(env, resultStr);
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Huffman_decodeNative(JNIEnv* env, jobject /* thiz */, jbyteArray jContext, jbyteArray jCoverText, jint jBitsPerToken, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return nullptr;
    
    const llama_model* model = llama_get_model(cppCtx);

    std::string cppContext = Format::asString(env, jContext);
    std::string cppCoverText = Format::asString(env, jCoverText);
    llama_tokens contextTokens = LlamaCpp::tokenize(env, cppContext, cppCtx);
    llama_tokens coverTextTokens = LlamaCpp::tokenize(env, cppCoverText, cppCtx);

    std::vector<bool> cppCipherBits;
    int i = 0;
    bool isFirstRun = true;
    llama_token coverTextToken = -1;

    while (i < (int)coverTextTokens.size()) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{coverTextToken};
        
        int n_past = isFirstRun ? 0 : (int)(contextTokens.size() + i - 1);
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx, n_past);
        
        if (rawLogits == nullptr) break;

        int32_t n_vocab = LlamaCpp::getVocabSize(model);
        std::vector<float> logits(n_vocab);
        std::copy(rawLogits, rawLogits + n_vocab, logits.begin());

        float* probabilities = Statistics::softmax(logits.data(), model);
        LlamaCpp::suppressSpecialTokens(probabilities, model);

        std::vector<std::pair<llama_token, float>> allProbs;
        allProbs.reserve(n_vocab);
        for(int32_t v=0; v<n_vocab; v++) allProbs.emplace_back(v, probabilities[v]);

        std::sort(allProbs.begin(), allProbs.end(), [](const auto& a, const auto& b) {
            if (std::abs(a.second - b.second) > 1e-9f) return a.second > b.second;
            return a.first < b.first;
        });

        std::vector<std::pair<llama_token, float>> topProbs;
        float cumP = 0.0f;
        for (const auto& p : allProbs) {
            if (p.second < 0.01f && topProbs.size() >= 2) break; 
            topProbs.push_back(p);
            cumP += p.second;
            if (cumP >= 0.9f && topProbs.size() >= 2) break;
        }

        HuffmanCoding huffmanCoding = HuffmanCoding();
        huffmanCoding.buildHuffmanTree(topProbs);
        huffmanCoding.mergeHuffmanNodes();
        huffmanCoding.generateHuffmanCodes();

        llama_token targetToken = coverTextTokens[i];
        if (LlamaCpp::isEndOfGeneration(targetToken, model)) break;

        if (huffmanCoding.huffmanCodes.count(targetToken)) {
            cppCipherBits.insert(
                cppCipherBits.end(),
                huffmanCoding.huffmanCodes[targetToken].begin(),
                huffmanCoding.huffmanCodes[targetToken].end()
            );
        }

        coverTextToken = coverTextTokens[i];
        isFirstRun = false;
        i++;
    }

    return Format::asByteArray(env, cppCipherBits);
}

std::vector<std::pair<llama_token, float>> Huffman::getTopProbabilities(float* probabilities, jint jBitsPerToken, const llama_model* model) {
    std::vector<std::pair<llama_token, float>> topProbabilities;
    topProbabilities.reserve(LlamaCpp::getVocabSize(model));

    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        topProbabilities.emplace_back(token, probabilities[token]);
    }

    std::sort(
        topProbabilities.begin(),
        topProbabilities.end(),
        [](const auto& pair1, const auto& pair2) { 
            if (std::abs(pair1.second - pair2.second) > 1e-9f) return pair1.second > pair2.second;
            return pair1.first < pair2.first;
        }
    );

    int k = 1 << jBitsPerToken;
    if (k < (int)topProbabilities.size()) {
        topProbabilities.resize(k);
    }
    return topProbabilities;
}
