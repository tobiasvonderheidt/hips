#include "Huffman.h"
#include "common.h"
#include "HuffmanCoding.h"
#include "HuffmanNode.h"
#include "Format.h"
#include "LlamaCpp.h"
#include "Statistics.h"

extern "C" JNIEXPORT jstring JNICALL Java_org_vonderheidt_hips_utils_Huffman_encode(JNIEnv* env, jobject /* thiz */, jstring jContext, jbyteArray jCipherBits, jint jBitsPerToken, jlong jCtx) {
    // TODO Abstract state management away in LlamaCpp.{h,cpp}
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    const llama_model* model = llama_get_model(cppCtx);

    // Tokenize context
    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);

    // Convert cipher bits to bit vector
    std::vector<bool> cppCipherBits = Format::asBitVector(env, jCipherBits);

    // Initialize vector to store cover text token
    llama_tokens coverTextTokens;

    // Initialize variables and flags for loop
    int i = 0;
    bool isLastSentenceFinished = false;

    bool isFirstRun = true;             // llama.cpp batch needs to store context tokens in first run, but only last sampled token in subsequent runs
    llama_token sampledToken = -1;      // Will always be overwritten with last cover text token

    // Sample tokens until all bits of secret message are encoded and last sentence is finished
    while (i < cppCipherBits.size() || !isLastSentenceFinished) {
        // Call llama.cpp to calculate the logit matrix similar to https://github.com/ggerganov/llama.cpp/blob/master/examples/simple/simple.cpp:
        // Needs only next tokens to be processed to store in a batch, i.e. contextTokens in first run and last sampled token in subsequent runs, rest is managed internally in ctx
        // Only last row of logit matrix is needed as it contains logits corresponding to last token of the prompt
        float* logits = LlamaCpp::getLogits(isFirstRun ? contextTokens : std::vector<llama_token>{sampledToken}, cppCtx);

        // Normalize logits to probabilities
        float* probabilities = Statistics::softmax(logits, model);

        // Suppress special tokens to avoid early termination before all bits of secret message are encoded
        LlamaCpp::suppressSpecialTokens(probabilities, model);

        // Huffman sampling to encode bits of secret message into tokens
        if (i < cppCipherBits.size()) {
            // Get top 2^bitsPerToken probabilities for last token of prompt (= height of Huffman tree)
            std::vector<std::pair<llama_token, float>> topProbabilities = Huffman::getTopProbabilities(probabilities, jBitsPerToken, model);

            // Construct Huffman tree from top probabilities
            HuffmanCoding huffmanCoding = HuffmanCoding();
            huffmanCoding.buildHuffmanTree(topProbabilities);
            huffmanCoding.mergeHuffmanNodes();
            HuffmanNode* root = huffmanCoding.generateHuffmanCodes();

            // Traverse Huffman tree based on bits of secret message to sample next token, therefore encoding information in it
            HuffmanNode* currentNode = root;

            // First nodes won't have a token as they were created during the merge step
            while (currentNode->token == -1) {
                // First condition is needed in case (length of cipher bits) % (bits per token) != 0
                // In last loop of outer while, inner while can cause i to exceed cppCipherBits.size()
                // Second condition is only checked if first condition is false, so std::out_of_range can't happen
                if (i >= cppCipherBits.size() || cppCipherBits[i] == 0) {
                    // Assuming left and right child nodes to be not null is safe as Huffman tree isn't traversed further down than bitsPerToken levels
                    currentNode = currentNode->left;
                }
                else {
                    currentNode = currentNode->right;
                }

                // Every time a turn is made when traversing the Huffman tree, another bit is encoded
                i++;
            }

            // Token containing the right bitsPerToken bits of information in its path is now found
            sampledToken = currentNode->token;

            // Update flag
            isFirstRun = false;

            // Destructor for huffmanCoding is called implicitly as it goes out of scope here
        }
        // Greedy sampling to pick most likely token until last sentence is finished
        else {
            // Get most likely token by sampling top 2^0 = 1 tokens
            sampledToken = Huffman::getTopProbabilities(probabilities, 0, model).begin()->first;

            // Update flag
            isLastSentenceFinished = LlamaCpp::isEndOfSentence(sampledToken, cppCtx);
        }

        // Append last sampled token to cover text tokens
        coverTextTokens.push_back(sampledToken);
    }

    // Detokenize cover text tokens into cover text to return it
    jstring coverText = LlamaCpp::detokenize(env, coverTextTokens, cppCtx);

    return coverText;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_org_vonderheidt_hips_utils_Huffman_decode(JNIEnv* env, jobject /* thiz */, jstring jContext, jstring jCoverText, jint jBitsPerToken, jlong jCtx) {
    // TODO Abstract state management away in LlamaCpp.{h,cpp}
    auto cppCtx = reinterpret_cast<llama_context*>(jCtx);
    const llama_model* model = llama_get_model(cppCtx);

    // Tokenize context and cover text
    llama_tokens contextTokens = LlamaCpp::tokenize(env, jContext, cppCtx);
    llama_tokens coverTextTokens = LlamaCpp::tokenize(env, jCoverText, cppCtx);

    // Initialize vector to store cipher bits
    std::vector<bool> cppCipherBits;

    // Initialize variables and flags for loop
    int i = 0;

    bool isFirstRun = true;
    llama_token coverTextToken = -1;

    // Decode every cover text token into bitsPerToken bits
    while (i < coverTextTokens.size()) {
        // Calculate the logit matrix again initially from context tokens, then from last cover text token, and get last row
        float* logits = LlamaCpp::getLogits(isFirstRun ? contextTokens : std::vector<llama_token>{coverTextToken}, cppCtx);

        // Normalize logits to probabilities
        float* probabilities = Statistics::softmax(logits, model);

        // Suppress special tokens
        LlamaCpp::suppressSpecialTokens(probabilities, model);

        // Get top 2^bitsPerToken probabilities
        std::vector<std::pair<llama_token, float>> topProbabilities = Huffman::getTopProbabilities(probabilities, jBitsPerToken, model);

        // Construct Huffman tree
        HuffmanCoding huffmanCoding = HuffmanCoding();
        huffmanCoding.buildHuffmanTree(topProbabilities);
        huffmanCoding.mergeHuffmanNodes();
        huffmanCoding.generateHuffmanCodes();        // Return value (root) is not needed here as Huffman tree is not traversed manually

        // Querying Huffman tree for the path to the current cover text token decodes the encoded information
        cppCipherBits.insert(
            cppCipherBits.end(),
            huffmanCoding.huffmanCodes[coverTextTokens[i]].begin(),
            huffmanCoding.huffmanCodes[coverTextTokens[i]].end()
        );

        // Update loop variables and flags
        coverTextToken = coverTextTokens[i];
        isFirstRun = false;

        i++;

        // Destructor for huffmanCoding is called implicitly as it goes out of scope here
    }

    // Create Java ByteArray from bit vector to return cipher bits
    jbyteArray jCipherBits = Format::asByteArray(env, cppCipherBits);

    return jCipherBits;
}

std::vector<std::pair<llama_token, float>> Huffman::getTopProbabilities(float* probabilities, jint jBitsPerToken, const llama_model* model) {
    // Vector of token-probability pairs
    // Use vector because unlike Kotlin, C++ sorts maps only by keys and not by values
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

    // Only keep tokens with top 2^bitsPerToken probabilities
    topProbabilities.resize(1 << jBitsPerToken);

    return topProbabilities;
}
