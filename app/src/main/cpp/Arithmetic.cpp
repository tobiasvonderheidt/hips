#include <algorithm>
#include <jni.h>
#include <cmath>
#include <vector>
#include <random>
#include <string>
#include "Arithmetic.h"
#include "common.h"
#include "Format.h"
#include "LlamaCpp.h"
#include "Statistics.h"

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Arithmetic_encodeNative(JNIEnv* env, jobject /* thiz */, jbyteArray jContext, jbyteArray jCipherBits, jfloat jTemperature, jint jTopK, jint jPrecision, jint jSeed, jlong jCtx) {
    if (jCipherBits == nullptr) return Format::asByteArray(env, std::string(""));
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return Format::asByteArray(env, std::string(""));

    const llama_model *model = llama_get_model(cppCtx);
    int32_t n_vocab = LlamaCpp::getVocabSize(model);

    std::string cppContext = Format::asString(env, jContext);
    bool isDecompression = (cppContext.empty());
    llama_tokens contextTokens = LlamaCpp::tokenize(env, cppContext, cppCtx);
    std::vector<bool> cppCipherBits = isDecompression ? Format::asBitVectorWithoutPadding(env, jCipherBits) : Format::asBitVector(env, jCipherBits);

    llama_tokens coverTextTokens;
    if (isDecompression) {
        contextTokens.clear();
        contextTokens.push_back(LlamaCpp::getEndOfGeneration(model));
    }

    std::pair<long long, long long> currentInterval = {0, 1LL << jPrecision};
    int i = 0;
    bool isLastSentenceFinished = false;
    bool isFirstRun = true;
    llama_token sampledToken = -1;
    const int MAX_TOKENS = 512;

    while ((i < (int)cppCipherBits.size() || (!isDecompression && !isLastSentenceFinished)) && coverTextTokens.size() < MAX_TOKENS) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{sampledToken};
        int n_past = isFirstRun ? 0 : (int)(contextTokens.size() + coverTextTokens.size() - 1);
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx, n_past);
        
        if (rawLogits == nullptr) break;
        
        std::vector<float> logits(n_vocab);
        std::copy(rawLogits, rawLogits + n_vocab, logits.begin());

        if (!isDecompression) {
            LlamaCpp::suppressSpecialTokensLogits(logits.data(), model); 
        }

        float lse = Statistics::logSumExp(logits.data(), n_vocab);
        std::vector<std::pair<llama_token, float>> allSorted;
        allSorted.reserve(n_vocab);
        for (int32_t token = 0; token < n_vocab; token++) {
            allSorted.emplace_back((llama_token)token, logits[token] / jTemperature);
        }

        std::sort(allSorted.begin(), allSorted.end(), [](const auto& a, const auto& b) {
            if (std::abs(a.second - b.second) > 1e-9f) return a.second > b.second;
            return a.first < b.first; 
        });

        std::vector<std::pair<llama_token, float>> pool;
        if (isDecompression) {
            pool = allSorted;
        } else {
            float cumP = 0.0f;
            for (const auto& pair : allSorted) {
                float p = std::exp((pair.second * jTemperature) - lse); 
                if (p < 0.001f && pool.size() >= 2) break; 
                pool.push_back(pair);
                cumP += p;
                if (cumP >= 0.95f && pool.size() >= 2) break;
            }
        }

        long long currentIntervalRange = currentInterval.second - currentInterval.first;
        float logThreshold = -static_cast<float>(std::log(static_cast<double>(currentIntervalRange)));
        
        std::vector<float> poolLogits;
        for(auto& p : pool) poolLogits.push_back(p.second);
        float poolLse = Statistics::logSumExp(poolLogits.data(), (int32_t)poolLogits.size());

        int k_candidate = 0;
        for (const auto& pair : pool) {
            if (pair.second - poolLse >= logThreshold) k_candidate++;
            else break;
        }

        int k = std::min(std::max(2, k_candidate), (int)pool.size());
        if (isDecompression) k = (int)pool.size();

        std::vector<std::pair<llama_token, float>> topKTokens(pool.begin(), pool.begin() + k);
        if (!isDecompression && jSeed > 0) {
            std::shuffle(topKTokens.begin(), topKTokens.end(), std::mt19937(jSeed + (int)coverTextTokens.size()));
        }

        if (i < (int)cppCipherBits.size()) {
            double probSum = 0.0;
            for (int j = 0; j < k; j++) {
                probSum += static_cast<double>(std::exp(topKTokens[j].second - poolLse));
            }

            std::vector<std::pair<llama_token, long long>> roundedScaledProbabilities;
            for (const auto& pair : topKTokens) {
                double rescaled = (std::exp(pair.second - poolLse) / probSum) * static_cast<double>(currentIntervalRange);
                roundedScaledProbabilities.emplace_back(pair.first, std::max(1LL, (long long)std::llround(rescaled)));
            }

            std::vector<std::pair<llama_token, long long>> cumulatedProbabilities;
            long long cumulatedProbability = 0;
            for (const auto& [token, probability] : roundedScaledProbabilities) {
                cumulatedProbability += probability;
                cumulatedProbabilities.emplace_back(token, cumulatedProbability);
            }

            while(!cumulatedProbabilities.empty() && cumulatedProbabilities.back().second > currentIntervalRange) {
                cumulatedProbabilities.pop_back();
            }
            
            if (cumulatedProbabilities.empty()) {
                cumulatedProbabilities.emplace_back(topKTokens[0].first, currentIntervalRange);
            } else {
                long long gap = currentIntervalRange - cumulatedProbabilities.back().second;
                if (gap != 0) {
                   for (auto& item : cumulatedProbabilities) item.second += gap;
                }
            }

            for (auto& item : cumulatedProbabilities) item.second += currentInterval.first;

            std::vector<bool> cipherBitSubvector;
            if (i + jPrecision < (int)cppCipherBits.size()) {
                cipherBitSubvector = std::vector<bool>(cppCipherBits.begin() + i, cppCipherBits.begin() + i + jPrecision);
            } else {
                cipherBitSubvector = std::vector<bool>(cppCipherBits.begin() + i, cppCipherBits.end());
                cipherBitSubvector.resize(jPrecision, false);
            }

            long long messageValue = Format::asLong(cipherBitSubvector);
            auto iterator = std::find_if(cumulatedProbabilities.begin(), cumulatedProbabilities.end(), [messageValue](const auto& pair) {
                return pair.second > messageValue;
            });

            int selectedSubinterval = std::distance(cumulatedProbabilities.begin(), iterator);
            if (selectedSubinterval >= (int)cumulatedProbabilities.size()) selectedSubinterval = (int)cumulatedProbabilities.size() - 1;

            long long newIntervalBottom = selectedSubinterval > 0 ? cumulatedProbabilities[selectedSubinterval-1].second : currentInterval.first;
            long long newIntervalTop = cumulatedProbabilities[selectedSubinterval].second;

            std::vector<bool> newIntervalBottomBitsInclusive = Format::asBitVector(newIntervalBottom, jPrecision);
            std::vector<bool> newIntervalTopBitsInclusive = Format::asBitVector(newIntervalTop - 1, jPrecision);

            int numBits = Arithmetic::numberOfSameBitsFromBeginning(newIntervalBottomBitsInclusive, newIntervalTopBitsInclusive);
            i += numBits;

            std::vector<bool> newBottomBits(newIntervalBottomBitsInclusive.begin() + numBits, newIntervalBottomBitsInclusive.end());
            newBottomBits.resize(jPrecision, false);
            std::vector<bool> newTopBits(newIntervalTopBitsInclusive.begin() + numBits, newIntervalTopBitsInclusive.end());
            newTopBits.resize(jPrecision, true);

            currentInterval.first = Format::asLong(newBottomBits);
            currentInterval.second = Format::asLong(newTopBits) + 1;

            sampledToken = cumulatedProbabilities[selectedSubinterval].first;
            isFirstRun = false;
            
            if (i >= (int)cppCipherBits.size() && LlamaCpp::isEndOfSentence(sampledToken, cppCtx)) isLastSentenceFinished = true;
        } else {
            sampledToken = pool[0].first; 
            isLastSentenceFinished = LlamaCpp::isEndOfSentence(sampledToken, cppCtx);
        }
        coverTextTokens.push_back(sampledToken);
        if (coverTextTokens.back() == LlamaCpp::getAsciiNul(model, cppCtx) || LlamaCpp::isEndOfGeneration(sampledToken, model)) break;
    }
    std::string resultStr = LlamaCpp::detokenize(coverTextTokens, cppCtx, true);
    return Format::asByteArray(env, resultStr);
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Arithmetic_decodeNative(JNIEnv* env, jobject /* thiz */, jbyteArray jContext, jbyteArray jCoverText, jfloat jTemperature, jint jTopK, jint jPrecision, jint jSeed, jlong jCtx) {
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    if (cppCtx == nullptr) return nullptr;

    const llama_model* model = llama_get_model(cppCtx);
    int32_t n_vocab = LlamaCpp::getVocabSize(model);

    std::string cppContext = Format::asString(env, jContext);
    std::string cppCoverText = Format::asString(env, jCoverText);
    bool isCompression = (cppContext.empty());
    llama_tokens contextTokens = LlamaCpp::tokenize(env, cppContext, cppCtx);
    llama_tokens coverTextTokens = LlamaCpp::tokenize(env, cppCoverText, cppCtx);

    if (isCompression) {
        contextTokens.clear();
        contextTokens.push_back(LlamaCpp::getEndOfGeneration(model));
    }

    std::pair<long long, long long> currentInterval = {0, 1LL << jPrecision};
    std::vector<bool> cppCipherBits;
    int i = 0;
    bool isFirstRun = true;
    llama_token coverTextToken = -1;

    while (i < (int)coverTextTokens.size()) {
        llama_tokens nextTokens = isFirstRun ? contextTokens : std::vector<llama_token>{coverTextToken};
        int n_past = isFirstRun ? 0 : (int)(contextTokens.size() + i - 1);
        float* rawLogits = LlamaCpp::getLogits(nextTokens, cppCtx, n_past);
        
        if (rawLogits == nullptr) break;
        
        std::vector<float> logits(n_vocab);
        std::copy(rawLogits, rawLogits + n_vocab, logits.begin());
        if (!isCompression) LlamaCpp::suppressSpecialTokensLogits(logits.data(), model);

        float lse = Statistics::logSumExp(logits.data(), n_vocab);
        std::vector<std::pair<llama_token, float>> allSorted;
        for (int32_t token = 0; token < n_vocab; token++) {
            allSorted.emplace_back((llama_token)token, logits[token] / jTemperature);
        }
        
        std::sort(allSorted.begin(), allSorted.end(), [](const auto& a, const auto& b) {
            if (std::abs(a.second - b.second) > 1e-9f) return a.second > b.second;
            return a.first < b.first; 
        });

        std::vector<std::pair<llama_token, float>> pool;
        if (isCompression) {
            pool = allSorted;
        } else {
            float cumP = 0.0f;
            for (const auto& pair : allSorted) {
                float p = std::exp((pair.second * jTemperature) - lse);
                if (p < 0.001f && pool.size() >= 2) break; 
                pool.push_back(pair);
                cumP += p;
                if (cumP >= 0.95f && pool.size() >= 2) break;
            }
        }

        long long currentIntervalRange = currentInterval.second - currentInterval.first;
        float logThreshold = -static_cast<float>(std::log(static_cast<double>(currentIntervalRange)));
        std::vector<float> poolLogits;
        for(auto& p : pool) poolLogits.push_back(p.second);
        float poolLse = Statistics::logSumExp(poolLogits.data(), (int32_t)poolLogits.size());

        int k_candidate = 0;
        for (const auto& pair : pool) {
            if (pair.second - poolLse >= logThreshold) k_candidate++;
            else break;
        }
        int k = std::min(std::max(2, k_candidate), (int)pool.size());
        if (isCompression) k = (int)pool.size();

        std::vector<std::pair<llama_token, float>> topKTokens(pool.begin(), pool.begin() + k);
        if (!isCompression && jSeed > 0) {
            std::shuffle(topKTokens.begin(), topKTokens.end(), std::mt19937(jSeed + i));
        }

        llama_token targetToken = coverTextTokens[i];
        if (LlamaCpp::isEndOfGeneration(targetToken, model)) break;

        auto rank_it = std::find_if(topKTokens.begin(), topKTokens.end(), [targetToken](const auto& pair) {
            return pair.first == targetToken;
        });
        int rank = (int)std::distance(topKTokens.begin(), rank_it);

        if (rank >= k) {
            rank = 0; 
        }

        double probSum = 0.0;
        for (int j = 0; j < k; j++) probSum += static_cast<double>(std::exp(topKTokens[j].second - poolLse));

        std::vector<std::pair<llama_token, long long>> roundedProbs;
        for (const auto& pair : topKTokens) {
            double rescaled = (std::exp(pair.second - poolLse) / probSum) * static_cast<double>(currentIntervalRange);
            roundedProbs.emplace_back(pair.first, std::max(1LL, (long long)std::llround(rescaled)));
        }

        std::vector<std::pair<llama_token, long long>> cumProbs;
        long long cumPVal = 0;
        for (const auto& [token, prob] : roundedProbs) {
            cumPVal += prob;
            cumProbs.emplace_back(token, cumPVal);
        }
        while(!cumProbs.empty() && cumProbs.back().second > currentIntervalRange) cumProbs.pop_back();
        
        if (cumProbs.empty()) {
            cumProbs.emplace_back(topKTokens[0].first, currentIntervalRange);
        } else {
            long long gap = currentIntervalRange - cumProbs.back().second;
            if (gap != 0) {
                for (auto& item : cumProbs) item.second += gap;
            }
        }
        for (auto& item : cumProbs) item.second += currentInterval.first;

        long long newBottom = rank > 0 ? cumProbs[rank-1].second : currentInterval.first;
        long long newTop = cumProbs[rank].second;

        std::vector<bool> newBottomBitsInc = Format::asBitVector(newBottom, (int)jPrecision);
        std::vector<bool> newTopBitsInc = Format::asBitVector(newTop - 1, (int)jPrecision);

        int numBits = Arithmetic::numberOfSameBitsFromBeginning(newBottomBitsInc, newTopBitsInc);

        if (i == (int)coverTextTokens.size() - 1) {
            cppCipherBits.insert(cppCipherBits.end(), newBottomBitsInc.begin(), newBottomBitsInc.end());
        } else {
            cppCipherBits.insert(cppCipherBits.end(), newTopBitsInc.begin(), newTopBitsInc.begin() + numBits);
        }

        std::vector<bool> nextBottom(newBottomBitsInc.begin() + numBits, newBottomBitsInc.end());
        nextBottom.resize((int)jPrecision, false);
        std::vector<bool> nextTop(newTopBitsInc.begin() + numBits, newTopBitsInc.end());
        nextTop.resize((int)jPrecision, true);

        currentInterval.first = Format::asLong(nextBottom);
        currentInterval.second = Format::asLong(nextTop) + 1;

        coverTextToken = coverTextTokens[i++];
        isFirstRun = false;
        if (LlamaCpp::getAsciiNul(model, cppCtx) == targetToken) break;
    }
    return isCompression ? Format::asByteArrayWithPadding(env, cppCipherBits) : Format::asByteArray(env, cppCipherBits);
}

int Arithmetic::numberOfSameBitsFromBeginning(const std::vector<bool>& bitVector1, const std::vector<bool>& bitVector2) {
    if (bitVector1.size() != bitVector2.size()) return 0;
    int count = 0;
    for (size_t i = 0; i < bitVector1.size(); i++) {
        if (bitVector1[i] != bitVector2[i]) break;
        count++;
    }
    return count;
}

llama_token Arithmetic::getTopProbability(const float *probabilities, const llama_model* model) {
    int32_t n_vocab = LlamaCpp::getVocabSize(model);
    llama_token best_token = 0;
    float best_prob = -1.0f;
    for (int32_t i = 0; i < n_vocab; i++) {
        if (probabilities[i] > best_prob) {
            best_prob = probabilities[i];
            best_token = (llama_token)i;
        } else if (std::abs(probabilities[i] - best_prob) < 1e-9f) {
            if (i < (int)best_token) best_token = (llama_token)i;
        }
    }
    return best_token;
}
