#include "Huffman.h"
#include "common.h"
#include "HuffmanCoding.h"
#include "HuffmanNode.h"
#include "Format.h"
#include "LlamaCpp.h"
#include "Statistics.h"
#include <cmath>
#include <algorithm>
#include <random>
#include <android/log.h>

#define TAG "Huffman.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_Huffman_encode(JNIEnv* env, jobject /* thiz */, jstring jContext, jbyteArray jCipherBits, jint jBitsPerToken, jint jSeed, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return env->NewStringUTF("");
    const llama_model* model = llama_get_model(cppCtx);

    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);
    std::vector<bool> cppCipherBits = Format::asBitVector(env, jCipherBits);

    bool isDecompression = (jContext == NULL || env->GetStringLength(jContext) == 0);

    llama_tokens coverTextTokens;
    if (isDecompression) {
        contextTokens.clear();
        contextTokens.push_back(LlamaCpp::getEndOfGeneration(model));
    }

    int i = 0;
    bool isLastSentenceFinished = false;
    bool isFirstRun = true;
    llama_token sampledToken = -1;
    const int MAX_TOKENS = 128;

    while ((i < (int)cppCipherBits.size() || !isLastSentenceFinished) && coverTextTokens.size() < MAX_TOKENS) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{sampledToken};
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx);
        if (rawLogits == nullptr) break;

        int32_t n_vocab = LlamaCpp::getVocabSize(model);
        std::vector<float> logits(n_vocab);
        std::copy(rawLogits, rawLogits + n_vocab, logits.begin());

        if (!isDecompression) {
            LlamaCpp::suppressSpecialTokensLogits(logits.data(), model);
        }

        // 1. Calculate Entropy
        float lse = Statistics::logSumExp(logits.data(), n_vocab);
        std::vector<float> baseProbabilities(n_vocab);
        for(int32_t v=0; v<n_vocab; v++) baseProbabilities[v] = std::exp(logits[v] - lse);
        float entropy = Statistics::calculateEntropy(baseProbabilities.data(), n_vocab);

        // 2. Determine dynamicTopK
        int maxK = 1 << jBitsPerToken;
        int dynamicTopK = isDecompression ? maxK : std::min(static_cast<int>(std::pow(2.0, static_cast<double>(entropy + 1.5f))), maxK);
        dynamicTopK = std::max(4, dynamicTopK);

        // 3. Filter and Sort (Nucleus + Threshold)
        std::vector<std::pair<llama_token, float>> allProbs;
        for(int32_t v=0; v<n_vocab; v++) allProbs.emplace_back((llama_token)v, baseProbabilities[v]);

        std::sort(allProbs.begin(), allProbs.end(), [](const auto& a, const auto& b) {
            if (std::abs(a.second - b.second) > 1e-9f) return a.second > b.second;
            return a.first < b.first;
        });

        std::vector<std::pair<llama_token, float>> pool;
        if (isDecompression) {
            pool = allProbs;
        } else {
            float cumP = 0.0f;
            for (const auto& p : allProbs) {
                if (p.second < 0.01f && pool.size() >= 2) break; 
                pool.push_back(p);
                cumP += p.second;
                if (cumP >= 0.9f && pool.size() >= 2) break;
            }
        }

        // 4. Select and Shuffle Top-K based on Seed
        int k = std::min((int)pool.size(), dynamicTopK);
        std::vector<std::pair<llama_token, float>> topKProbs(pool.begin(), pool.begin() + k);
        if (!isDecompression && jSeed > 0) {
            std::shuffle(topKProbs.begin(), topKProbs.end(), std::mt19937(jSeed + (int)coverTextTokens.size()));
        }

        if (i < (int)cppCipherBits.size()) {
            HuffmanCoding huffmanCoding = HuffmanCoding();
            huffmanCoding.buildHuffmanTree(topKProbs);
            huffmanCoding.mergeHuffmanNodes();
            HuffmanNode* root = huffmanCoding.generateHuffmanCodes();

            int bitsBefore = i;
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
            int bitsEncoded = i - bitsBefore;
            
            std::string wordStr = LlamaCpp::detokenize(std::vector<llama_token>{sampledToken}, cppCtx);
            LOGi("Word: [%s] | Bits Hidden: %d", wordStr.c_str(), bitsEncoded);

            isFirstRun = false;
            if (i >= (int)cppCipherBits.size() && LlamaCpp::isEndOfSentence(sampledToken, cppCtx)) {
                isLastSentenceFinished = true;
            }
        }
        else {
            sampledToken = topKProbs[0].first;
            isLastSentenceFinished = LlamaCpp::isEndOfSentence(sampledToken, cppCtx);
        }

        coverTextTokens.push_back(sampledToken);
        if (coverTextTokens.back() == LlamaCpp::getAsciiNul(model, cppCtx) || LlamaCpp::isEndOfGeneration(sampledToken, model)) break;
    }

    return LlamaCpp::detokenize(env, coverTextTokens, cppCtx);
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Huffman_decode(JNIEnv* env, jobject /* thiz */, jstring jContext, jstring jCoverText, jint jBitsPerToken, jint jSeed, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return nullptr;
    const llama_model* model = llama_get_model(cppCtx);
    int32_t n_vocab = LlamaCpp::getVocabSize(model);

    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);
    llama_tokens coverTextTokens = LlamaCpp::tokenize(env, jCoverText, cppCtx);

    bool isCompression = (jContext == NULL || env->GetStringLength(jContext) == 0);
    if (isCompression) {
        contextTokens.clear();
        contextTokens.push_back(LlamaCpp::getEndOfGeneration(model));
    }

    std::vector<bool> cppCipherBits;
    int i = 0;
    bool isFirstRun = true;
    llama_token coverTextToken = -1;

    while (i < (int)coverTextTokens.size()) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{coverTextToken};
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx);
        if (rawLogits == nullptr) break;

        std::vector<float> logits(n_vocab);
        std::copy(rawLogits, rawLogits + n_vocab, logits.begin());

        if (!isCompression) {
            LlamaCpp::suppressSpecialTokensLogits(logits.data(), model);
        }

        float lse = Statistics::logSumExp(logits.data(), n_vocab);
        std::vector<float> baseProbabilities(n_vocab);
        for(int32_t v=0; v<n_vocab; v++) baseProbabilities[v] = std::exp(logits[v] - lse);
        float entropy = Statistics::calculateEntropy(baseProbabilities.data(), n_vocab);

        int maxK = 1 << jBitsPerToken;
        int dynamicTopK = isCompression ? maxK : std::min(static_cast<int>(std::pow(2.0, static_cast<double>(entropy + 1.5f))), maxK);
        dynamicTopK = std::max(4, dynamicTopK);

        std::vector<std::pair<llama_token, float>> allProbs;
        for(int32_t v=0; v<n_vocab; v++) allProbs.emplace_back((llama_token)v, baseProbabilities[v]);

        std::sort(allProbs.begin(), allProbs.end(), [](const auto& a, const auto& b) {
            if (std::abs(a.second - b.second) > 1e-9f) return a.second > b.second;
            return a.first < b.first;
        });

        std::vector<std::pair<llama_token, float>> pool;
        if (isCompression) {
            pool = allProbs;
        } else {
            float cumP = 0.0f;
            for (const auto& p : allProbs) {
                if (p.second < 0.01f && pool.size() >= 2) break; 
                pool.push_back(p);
                cumP += p.second;
                if (cumP >= 0.9f && pool.size() >= 2) break;
            }
        }

        int k = std::min((int)pool.size(), dynamicTopK);
        std::vector<std::pair<llama_token, float>> topKProbs(pool.begin(), pool.begin() + k);
        if (!isCompression && jSeed > 0) {
            std::shuffle(topKProbs.begin(), topKProbs.end(), std::mt19937(jSeed + i));
        }

        HuffmanCoding huffmanCoding = HuffmanCoding();
        huffmanCoding.buildHuffmanTree(topKProbs);
        huffmanCoding.mergeHuffmanNodes();
        huffmanCoding.generateHuffmanCodes();

        llama_token targetToken = coverTextTokens[i];
        if (LlamaCpp::isEndOfGeneration(targetToken, model)) break;

        auto rank_it = std::find_if(topKProbs.begin(), topKProbs.end(), [targetToken](const auto& pair) {
            return pair.first == targetToken;
        });

        if (rank_it != topKProbs.end()) {
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
        topProbabilities.emplace_back((llama_token)token, probabilities[token]);
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
