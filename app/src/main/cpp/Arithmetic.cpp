#include <algorithm>
#include <jni.h>
#include "Arithmetic.h"
#include "common.h"
#include "Format.h"
#include "LlamaCpp.h"
#include "Statistics.h"

extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_Arithmetic_encode(JNIEnv* env, jobject /* thiz */, jstring jContext, jbyteArray jCipherBits, jfloat jTemperature, jint jTopK, jint jPrecision, jlong jCtx) {
    // TODO Abstract state management away in LlamaCpp.{h,cpp}
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    const llama_model *model = llama_get_model(cppCtx);

    // Tokenize context
    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);

    // Convert cipher bits to bit vector
    bool isDecompression = contextTokens.empty();

    std::vector<bool> cppCipherBits = isDecompression ? Format::asBitVectorWithoutPadding(env, jCipherBits) : Format::asBitVector(env, jCipherBits);

    // Initialize vector to store cover text tokens
    llama_tokens coverTextTokens;

    // <Logic specific to arithmetic coding>

    // Stegasuras paper says that binary conversion happens with empty context, but code actually uses a single end-of-generation (eog) token as context
    // llama.cpp crashes with empty context anyway
    // UI doesn't allow empty context for steganography, so no collision possible when calling Arithmetic.{decode,encode} for binary conversion
    if (isDecompression) {
        contextTokens.push_back(LlamaCpp::getEndOfGeneration(model));
    }

    // Define initial interval as [0, 2^precision)
    // Stegasuras variable "max_val" is redundant
    std::pair<long long, long long> currentInterval = {0, 1 << jPrecision};

    // </Logic specific to arithmetic coding>

    // Initialize variables and flags for loop
    int i = 0;
    bool isLastSentenceFinished = false;

    bool isFirstRun = true;             // llama.cpp batch needs to store context tokens in first run, but only last sampled token in subsequent runs
    llama_token sampledToken = -1;      // Will always be overwritten with last cover text token

    // Sample tokens until all bits of secret message are encoded
    // But only finish last sentence during encoding, not during decompression, to avoid infinite loop
    // Our use of isDecompression here matches control flow of Stegasuras with its finish_sent parameter
    while (i < cppCipherBits.size() || (!isDecompression && !isLastSentenceFinished)) {
        // Call llama.cpp to calculate the logit matrix similar to https://github.com/ggerganov/llama.cpp/blob/master/examples/simple/simple.cpp:
        // Needs only next tokens to be processed to store in a batch, i.e. contextTokens in first run and last sampled token in subsequent runs, rest is managed internally in ctx
        // Only last row of logit matrix is needed as it contains logits corresponding to last token of the prompt
        float* logits = LlamaCpp::getLogits(isFirstRun ? contextTokens : std::vector<llama_token>{sampledToken}, cppCtx);

        // Normalize logits to probabilities
        float* probabilities = Statistics::softmax(logits, model);

        // Suppress special tokens to avoid early termination before all bits of secret message are encoded
        LlamaCpp::suppressSpecialTokens(probabilities, model);

        // Arithmetic sampling to encode bits of secret message into tokens
        if (i < cppCipherBits.size()) {
            // <Logic specific to arithmetic coding>

            // Scale probabilities with 1/temperature and sort descending
            std::vector<std::pair<llama_token, float>> scaledProbabilities;
            scaledProbabilities.reserve(LlamaCpp::getVocabSize(model));

            for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
                scaledProbabilities.emplace_back(token, probabilities[token] / jTemperature);
            }

            std::sort(
                scaledProbabilities.begin(),
                scaledProbabilities.end(),
                [](const auto& pair1, const auto& pair2) { return pair1.second > pair2.second; }
            );

            // Stegasuras: "Cut off low probabilities that would be rounded to 0"
            // currentThreshold needs to be float as it will be compared to probabilities, float division happens implicitly in Python but explicitly in Kotlin
            long long currentIntervalRange = currentInterval.second - currentInterval.first;
            double currentThreshold = 1.0 / currentIntervalRange;

            // Invert logic of Stegasuras:
            // Stegasuras: Drop all tokens with probability < currentThreshold
            // <=> HiPS: Keep all tokens with probability >= currentThreshold

            // Minimum ensures that k doesn't exceed topK
            // Maximum ensures that at least the tokens with the top 2 probabilities are considered
            // => Maximum is relevant if next token is practically certain (e.g. "Albert Einstein was a renowned theoretical" will be continued with " physicist" with > 99.5% probability)
            //    Probability of second most likely token will already be rounded to 0
            // => Loop can go through runs that don't encode any information (i.e. secret message bits) because a token is certain, but next token won't be certain and will encode information again
            //    Not possible with Huffman, where every token encodes bitsPerToken bits of information
            // => Matches entropy: Events that are certain don't contain any information (<=> Events that are very uncertain contain a lot of information)
            int k = std::min(
                std::max(
                    2,
                    static_cast<int>(std::count_if(
                        scaledProbabilities.begin(),
                        scaledProbabilities.end(),
                        [currentThreshold](const std::pair<llama_token, float>& pair) { return pair.second >= currentThreshold; }
                    ))
                ),
                jTopK
            );

            // Keep tokens with top k (!= topK) probabilities
            // Stegasuras would use variable name roundedScaledProbabilities here already, but requires overwriting one data type with another (List<Pair<Int, Float>> vs List<Pair<Int, Int>>)
            // Possible in Python, but not in Kotlin
            // Use topScaledProbabilities for now to be similar to decode, roundedScaledProbabilities only after rounding probabilities from float to int below
            std::vector<std::pair<llama_token, float>> topScaledProbabilities = scaledProbabilities;
            topScaledProbabilities.resize(k);

            // Stegasuras: "Rescale to correct range"
            // Top k probabilities sum up to something in [0,1), rescale to [0, 2^precision)
            float sum = 0.0;

            for (const auto& [token, probability] : topScaledProbabilities) {
                sum += probability;
            }

            for (auto& pair : topScaledProbabilities) {
                pair.second *= currentIntervalRange / sum;
            }

            // Stegasuras: "Round probabilities to integers given precision"
            // Variable name roundedScaledProbabilities is appropriate now
            std::vector<std::pair<llama_token, long long>> roundedScaledProbabilities;
            roundedScaledProbabilities.reserve(topScaledProbabilities.size());

            for (const auto& pair : topScaledProbabilities) {
                roundedScaledProbabilities.emplace_back(pair.first, std::round(pair.second));
            }

            // Replace probability with cumulated probability
            // Probabilities that would round to 0 were cut off earlier, so all at least round to 1, no collisions possible
            std::vector<std::pair<llama_token, long long>> cumulatedProbabilities;
            cumulatedProbabilities.reserve(roundedScaledProbabilities.size());

            long long cumulatedProbability = 0;

            for (const auto& [token, probability] : roundedScaledProbabilities) {
                cumulatedProbability += probability;
                cumulatedProbabilities.emplace_back(token, cumulatedProbability);
            }

            // Stegasuras: "Remove any elements from the bottom if rounding caused the total prob to be too large"
            // Remove tokens with low probabilities if their cumulated probability is too large
            int overfill = std::count_if(
                cumulatedProbabilities.begin(),
                cumulatedProbabilities.end(),
                [currentIntervalRange](const std::pair<llama_token, float>& pair) { return pair.second > currentIntervalRange; }
            );

            if (overfill > 0) {
                cumulatedProbabilities.resize(cumulatedProbabilities.size() - overfill);
            }

            // Stegasuras: "Add any mass to the top if removing/rounding causes the total prob to be too small"
            // Removing tokens might have created a gap at the top, i.e. a sub-interval between cumulated probability of last token and top of current interval, that doesn't correspond to any token
            // Arithmetic coding only works when current interval is exactly filled, so close the gap by shifting all cumulated probabilities up by its size
            // Equivalent to first token having larger probability, shifting cumulated probabilities of all subsequent tokens
            for (auto& item : cumulatedProbabilities) {
                item.second += currentIntervalRange - cumulatedProbabilities.back().second;
            }

            // Stegasuras: "Convert to position in range"
            // Shifts all cumulated probabilities up again by bottom of current interval
            for (auto& item : cumulatedProbabilities) {
                item.second += currentInterval.first;
            }

            // Replace token of last sub-interval with ASCII NUL character so it can be sampled during decompression
            // Similar to explanation at https://www.youtube.com/watch?v=RFWJM8JMXBs
            if (isDecompression) {
                scaledProbabilities[cumulatedProbabilities.size() - 1].first = LlamaCpp::getAsciiNul(model, cppCtx);
                cumulatedProbabilities[cumulatedProbabilities.size() - 1].first = LlamaCpp::getAsciiNul(model, cppCtx);
            }

            // Stegasuras: "Get selected index based on binary fraction from message bits"
            // Process cipher bits in portions of size precision
            // Unlike Python, Kotlin doesn't handle "cipherBitString.substring(startIndex = i, endIndex = i + precision)" gracefully if i + precision is too large
            std::vector<bool> cipherBitSubvector;

            if (i + jPrecision < cppCipherBits.size()) {
                cipherBitSubvector = std::vector<bool>(cppCipherBits.begin() + i, cppCipherBits.begin() + i + jPrecision);
            }
            else {
                cipherBitSubvector = std::vector<bool>(cppCipherBits.begin() + i, cppCipherBits.end());
            }

            // Append 0s to last cipher bits to make last portion of size precision as well
            if (i + jPrecision > cppCipherBits.size()) {
                cipherBitSubvector.resize(cipherBitSubvector.size() + i + jPrecision - cppCipherBits.size(), false);
            }

            // Convert portion of cipher bits to integer for comparison with cumulated probabilities
            // Find position of first token with cumulated probability larger than this integer, i.e. find relevant sub-interval of current interval
            // => sampledToken is already determined here, next steps only calculate new interval
            // Stegasuras variable "message_token" is redundant
            auto iterator = std::find_if(
                cumulatedProbabilities.begin(),
                cumulatedProbabilities.end(),
                [cipherBitSubvector](const std::pair<llama_token, long long>& pair) { return pair.second > Format::asLong(cipherBitSubvector); }    // Stegasuras would reverse cipherBitSubstring, shouldn't be necessary here
            );

            int selectedSubinterval = std::distance(cumulatedProbabilities.begin(), iterator);

            // Stegasuras: "Calculate new range as ints"
            // Calculate bottom and top of relevant sub-interval for next iteration
            // New bottom (inclusive) is top of preceding sub-interval (exclusive there) if relevant one is not the first one, old bottom otherwise
            // New top (exclusive) is top of relevant sub-interval
            long long newIntervalBottom = selectedSubinterval > 0 ? cumulatedProbabilities[selectedSubinterval-1].second : currentInterval.first;
            long long newIntervalTop = cumulatedProbabilities[selectedSubinterval].second;

            // Stegasuras: "Convert range to bits"
            std::vector<bool> newIntervalBottomBitsInclusive = Format::asBitVector(newIntervalBottom, jPrecision);  // Again, reversing shouldn't be necessary here
            std::vector<bool> newIntervalTopBitsInclusive = Format::asBitVector(newIntervalTop - 1, jPrecision);    // Stegasuras: "-1 here because upper bound is exclusive" (i.e. newIntervalTopBitsInclusive is inclusive)

            // Stegasuras: "Consume most significant bits which are now fixed and update interval"
            // Arithmetic coding encodes data into a number by iteratively narrowing initial interval defined earlier
            // Therefore most significant bits are fixed first (~ numberOfSameBitsFromBeginning), determining the order of magnitude of the number, less significant bits are fixed later
            int numberOfEncodedBits = Arithmetic::numberOfSameBitsFromBeginning(newIntervalBottomBitsInclusive, newIntervalTopBitsInclusive);

            // Deviation from Stegasuras:
            // For cases where the LLM is very confident about the next token, interval barely narrows and numberOfEncodedBits can be 0, so it would loop
            // Need to force 1 bit of progress during decompression to avoid this
            if (isDecompression && numberOfEncodedBits == 0) {
                numberOfEncodedBits = 1;
            }

            i += numberOfEncodedBits;

            // New interval is determined by setting unfixed bits to 0 for bottom end, to 1 for top end
            // Interval boundaries can jump around because first numberOfEncodedBits bits are already processed and therefore cut off
            // Next portion of cipher bits in general doesn't narrow the interval
            std::vector<bool> newIntervalBottomBits = std::vector<bool>(newIntervalBottomBitsInclusive.begin() + numberOfEncodedBits, newIntervalBottomBitsInclusive.end());
            newIntervalBottomBits.resize(newIntervalBottomBits.size() + numberOfEncodedBits, false);

            std::vector<bool> newIntervalTopBits = std::vector<bool>(newIntervalTopBitsInclusive.begin() + numberOfEncodedBits, newIntervalTopBitsInclusive.end());
            newIntervalTopBits.resize(newIntervalTopBits.size() + numberOfEncodedBits, true);

            currentInterval.first = Format::asLong(newIntervalBottomBits);                      // Again, reversing shouldn't be necessary here
            currentInterval.second = Format::asLong(newIntervalTopBits) + 1;                    // Stegasuras: "+1 here because upper bound is exclusive"

            // Sample token as determined above
            sampledToken = cumulatedProbabilities[selectedSubinterval].first;

            // </Logic specific to arithmetic coding>

            // Update flag
            isFirstRun = false;
        }
        // Greedy sampling to pick most likely token until last sentence is finished
        else {
            // Get most likely token
            sampledToken = Arithmetic::getTopProbability(probabilities, model);

            // Update flag
            isLastSentenceFinished = LlamaCpp::isEndOfSentence(sampledToken, cppCtx);
        }

        // Append last sampled token to cover text tokens
        coverTextTokens.push_back(sampledToken);

        // Stegasuras: "For text->bits->text"
        // Variable "partial" not needed here as cover text isn't appended to context
        if (coverTextTokens.back() == LlamaCpp::getAsciiNul(model, cppCtx)) {
            break;
        }
    }

    // Detokenize cover text tokens into cover text to return it
    jstring coverText = LlamaCpp::detokenize(env, coverTextTokens, cppCtx);

    return coverText;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Arithmetic_decode(JNIEnv* env, jobject /* thiz */, jstring jContext, jstring jCoverText, jfloat jTemperature, jint jTopK, jint jPrecision, jlong jCtx) {
    // TODO Abstract state management away in LlamaCpp.{h,cpp}
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    const llama_model* model = llama_get_model(cppCtx);

    // Tokenize context and cover text
    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);
    llama_tokens coverTextTokens = LlamaCpp::tokenize(env, jCoverText, cppCtx);

    // <Logic specific to arithmetic coding>

    // Similar to encode
    bool isCompression = contextTokens.empty();

    if (isCompression) {
        contextTokens.push_back(LlamaCpp::getEndOfGeneration(model));

        // During compression, Stegasuras appends eog token ('<eos>') to secret message passed via cover text parameter
        // Not done here as ASCII NUL is used instead (see translation of "partial" variable in encode)
    }

    std::pair<long long, long long> currentInterval = {0, 1 << jPrecision};

    // </Logic specific to arithmetic coding>

    // Initialize vector to store cipher bits
    std::vector<bool> cppCipherBits;

    // Initialize variables and flags for loop
    int i = 0;

    bool isFirstRun = true;
    llama_token coverTextToken = -1;

    // Decode every cover text token
    while (i < coverTextTokens.size()) {
        // Calculate the logit matrix again initially from context tokens, then from last cover text token, and get last row
        float* logits = LlamaCpp::getLogits(isFirstRun ? contextTokens : std::vector<llama_token>{coverTextToken}, cppCtx);

        // Normalize logits to probabilities
        float* probabilities = Statistics::softmax(logits, model);

        // Suppress special tokens
        LlamaCpp::suppressSpecialTokens(probabilities, model);

        // <Logic specific to arithmetic coding>

        std::vector<std::pair<llama_token, float>> scaledProbabilities;
        scaledProbabilities.reserve(LlamaCpp::getVocabSize(model));

        for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
            scaledProbabilities.emplace_back(token, probabilities[token] / jTemperature);
        }

        std::sort(
                scaledProbabilities.begin(),
                scaledProbabilities.end(),
                [](const auto& pair1, const auto& pair2) { return pair1.second > pair2.second; }
        );

        // Stegasuras: "Cut off low probabilities that would be rounded to 0"
        long long currentIntervalRange = currentInterval.second - currentInterval.first;
        double currentThreshold = 1.0 / currentIntervalRange;

        int k = std::min(
            std::max(
                2,
                static_cast<int>(std::count_if(
                    scaledProbabilities.begin(),
                    scaledProbabilities.end(),
                    [currentThreshold](const std::pair<llama_token, float>& pair) { return pair.second >= currentThreshold; }
                ))
            ),
            jTopK
        );

        // Don't reassign "scaledProbabilities = scaledProbabilities.take(k)" but introduce new variable topScaledProbabilities as decode needs scaledProbabilities again later
        std::vector<std::pair<llama_token, float>> topScaledProbabilities = scaledProbabilities;
        topScaledProbabilities.resize(k);

        // Stegasuras: "Rescale to correct range"
        float sum = 0.0;

        for (const auto& [token, probability] : topScaledProbabilities) {
            sum += probability;
        }

        for (auto& pair : topScaledProbabilities) {
            pair.second *= currentIntervalRange / sum;
        }

        // Stegasuras: "Round probabilities to integers given precision"
        std::vector<std::pair<llama_token, long long>> roundedScaledProbabilities;
        roundedScaledProbabilities.reserve(topScaledProbabilities.size());

        for (const auto& pair : topScaledProbabilities) {
            roundedScaledProbabilities.emplace_back(pair.first, std::round(pair.second));
        }

        std::vector<std::pair<llama_token, long long>> cumulatedProbabilities;
        cumulatedProbabilities.reserve(roundedScaledProbabilities.size());

        long long cumulatedProbability = 0;

        for (const auto& [token, probability] : roundedScaledProbabilities) {
            cumulatedProbability += probability;
            cumulatedProbabilities.emplace_back(token, cumulatedProbability);
        }

        // Stegasuras: "Remove any elements from the bottom if rounding caused the total prob to be too large"
        int overfill = std::count_if(
            cumulatedProbabilities.begin(),
            cumulatedProbabilities.end(),
            [currentIntervalRange](const std::pair<llama_token, float>& pair) { return pair.second > currentIntervalRange; }
        );

        if (overfill > 0) {
            cumulatedProbabilities.resize(cumulatedProbabilities.size() - overfill);
        }

        // Stegasuras: "Add any mass to the top if removing/rounding causes the total prob to be too small"
        for (auto& item : cumulatedProbabilities) {
            item.second += currentIntervalRange - cumulatedProbabilities.back().second;
        }

        // Stegasuras: "Convert to position in range"
        for (auto& item : cumulatedProbabilities) {
            item.second += currentInterval.first;
        }

        // Replace token of last sub-interval with ASCII NUL character so it can be sampled during compression
        // Similar to explanation at https://www.youtube.com/watch?v=RFWJM8JMXBs
        if (isCompression) {
            scaledProbabilities[cumulatedProbabilities.size() - 1].first = LlamaCpp::getAsciiNul(model, cppCtx);
            cumulatedProbabilities[cumulatedProbabilities.size() - 1].first = LlamaCpp::getAsciiNul(model, cppCtx);
        }

        // Stegasuras: n/a
        // Determine rank of predicted token amongst all tokens based on its probability
        auto iterator = std::find_if(
            scaledProbabilities.begin(),
            scaledProbabilities.end(),
            [coverTextTokens, i](const std::pair<llama_token, float>& pair) { return pair.first == coverTextTokens[i]; }
        );

        int rank = std::distance(scaledProbabilities.begin(), iterator);

        // Deviation from Stegasuras:
        // Error handling for if the token isn't found in the valid range
        // Small chance but possible as token probability has to be > currentThreshold (~ 1/2^precision)
        if (rank == -1 || rank > cumulatedProbabilities.size()) {
            throw std::invalid_argument("Cover text cannot be decoded: token mismatch at position " + std::to_string(i));
        }

        // Sample token at (corrected) rank
        int selectedSubinterval = rank;

        // Stegasuras: "Calculate new range as ints"
        long long newIntervalBottom = selectedSubinterval > 0 ? cumulatedProbabilities[selectedSubinterval-1].second : currentInterval.first;
        long long newIntervalTop = cumulatedProbabilities[selectedSubinterval].second;

        // Stegasuras: "Convert range to bits"
        std::vector<bool> newIntervalBottomBitsInclusive = Format::asBitVector(newIntervalBottom, jPrecision);
        std::vector<bool> newIntervalTopBitsInclusive = Format::asBitVector(newIntervalTop - 1, jPrecision);

        // Stegasuras: "Emit most significant bits which are now fixed and update interval"
        // Inline += operation to eliminate newBits variable
        int numberOfEncodedBits = Arithmetic::numberOfSameBitsFromBeginning(newIntervalBottomBitsInclusive, newIntervalTopBitsInclusive);

        cppCipherBits.insert(
            cppCipherBits.end(),
            newIntervalTopBitsInclusive.begin(),
            i == coverTextTokens.size() - 1 ? newIntervalBottomBitsInclusive.end() : newIntervalTopBitsInclusive.begin() + numberOfEncodedBits
        );

        std::vector<bool> newIntervalBottomBits = std::vector<bool>(newIntervalBottomBitsInclusive.begin() + numberOfEncodedBits, newIntervalBottomBitsInclusive.end());
        newIntervalBottomBits.resize(newIntervalBottomBits.size() + numberOfEncodedBits, false);

        std::vector<bool> newIntervalTopBits = std::vector<bool>(newIntervalTopBitsInclusive.begin() + numberOfEncodedBits, newIntervalTopBitsInclusive.end());
        newIntervalTopBits.resize(newIntervalTopBits.size() + numberOfEncodedBits, true);

        currentInterval.first = Format::asLong(newIntervalBottomBits);
        currentInterval.second = Format::asLong(newIntervalTopBits) + 1;

        // </Logic specific to arithmetic coding>

        // Update loop variables and flags
        coverTextToken = coverTextTokens[i];
        isFirstRun = false;

        i++;
    }

    // Create ByteArray from bit vector to return cipher bits
    jbyteArray jCipherBits = isCompression ? Format::asByteArrayWithPadding(env, cppCipherBits) : Format::asByteArray(env, cppCipherBits);

    return jCipherBits;
}

int Arithmetic::numberOfSameBitsFromBeginning(const std::vector<bool>& bitVector1, const std::vector<bool>& bitVector2) {
    // Only edge case covered in Stegasuras
    if (bitVector1.size() != bitVector2.size()) {
        throw std::invalid_argument("The bit vectors are of different length");
    }

    int numberOfSameBitsFromBeginning = 0;

    for (int i = 0; i < bitVector1.size(); i++) {
        if (bitVector1[i] != bitVector2[i]) {
            break;
        }

        numberOfSameBitsFromBeginning++;
    }

    return numberOfSameBitsFromBeginning;
}

llama_token Arithmetic::getTopProbability(float *probabilities, const llama_model* model) {
    // Vector of token-probability pairs
    // Use vector because unlike Kotlin, C++ can sort maps only by keys and not by values
    std::vector<std::pair<llama_token, float>> topProbabilities;

    // Fill vector with pairs constructed from probabilities array, effectively maps token IDs to their probabilities to not lose them when sorting
    // Reserve memory ahead of time and construct pairs in place with emplace_back(), better performance than just using push_back(std::pair<>()) in loop
    topProbabilities.reserve(LlamaCpp::getVocabSize(model));

    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        topProbabilities.emplace_back(token, probabilities[token]);
    }

    // Sort tokens descending based on probabilities
    std::sort(
        topProbabilities.begin(),
        topProbabilities.end(),
        [](const auto& pair1, const auto& pair2) { return pair1.second > pair2.second; }
    );

    // Only keep token with top probability
    llama_token sampledToken = topProbabilities[0].first;

    return sampledToken;
}
