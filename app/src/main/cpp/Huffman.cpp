#include "Huffman.h"
#include "common.h"
#include "HuffmanCoding.h"
#include "HuffmanNode.h"
#include "Format.h"
#include "LlamaCpp.h"
#include "Statistics.h"
#include <cmath>
#include <android/log.h>

#define TAG "Huffman.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_Huffman_encode(JNIEnv* env, jobject /* thiz */, jstring jContext, jbyteArray jCipherBits, jint jBitsPerToken, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return env->NewStringUTF("");
    const llama_model* model = llama_get_model(cppCtx);

    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);
    std::vector<bool> cppCipherBits = Format::asBitVector(env, jCipherBits);

    llama_tokens coverTextTokens;
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

            // TRACK BITS
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
            int bitsEncoded = i - bitsBefore;
            sampledToken = currentNode->token;
            
            // LOG BIT COUNT
            std::string wordStr = LlamaCpp::detokenize(std::vector<llama_token>{sampledToken}, cppCtx);
            LOGi("Word: [%s] | Bits Hidden: %d", wordStr.c_str(), bitsEncoded);

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

    return LlamaCpp::detokenize(env, coverTextTokens, cppCtx);
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Huffman_decode(JNIEnv* env, jobject /* thiz */, jstring jContext, jstring jCoverText, jint jBitsPerToken, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return nullptr;
    const llama_model* model = llama_get_model(cppCtx);

    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);
    llama_tokens coverTextTokens = LlamaCpp::tokenize(env, jCoverText, cppCtx);

    std::vector<bool> cppCipherBits;
    int i = 0;
    bool isFirstRun = true;
    llama_token coverTextToken = -1;

    while (i < (int)coverTextTokens.size()) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{coverTextToken};
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx);
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
