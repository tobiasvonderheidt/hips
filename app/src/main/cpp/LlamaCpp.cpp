#include "LlamaCpp.h"

std::string LlamaCpp::detokenize(const llama_tokens& tokens, const llama_context* ctx) {
    // Detokenize vector of tokens to C++ string
    // See common.cpp: common_detokenize calls llama_detokenize, with parameters "remove_special = false" hard-coded and "unparse_special = special" passed through
    std::string string = common_detokenize(ctx, tokens, true);

    return string;
}

std::string LlamaCpp::detokenize(const llama_token& token, const llama_context* ctx) {
    llama_tokens tokens = std::vector<llama_token>{token};

    std::string string = LlamaCpp::detokenize(tokens, ctx);

    return string;
}

bool LlamaCpp::isSpecial(llama_token token, const llama_model* model) {
    // Get vocabulary of the LLM
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // Check if token is special
    bool isSpecial = llama_vocab_is_eog(vocab, token) || llama_vocab_is_control(vocab, token);

    return isSpecial;
}

bool LlamaCpp::isEndOfGeneration(llama_token token, const llama_model* model) {
    // Check if token is eog token
    const llama_vocab* vocab = llama_model_get_vocab(model);
    bool isEog = llama_vocab_is_eog(vocab, token);

    return isEog;
}

jbyteArray LlamaCpp::detokenize(JNIEnv* env, const llama_tokens& tokens, const llama_context* ctx) {
    // Detokenize tokens to C++ string
    std::string cppString = LlamaCpp::detokenize(tokens, ctx);

    // Initialize Java byte array to store UTF-8 encoding of the C++ string
    jbyteArray jByteArray = env->NewByteArray((int32_t) cppString.size());

    // Fill the Java byte array and return it
    env->SetByteArrayRegion(jByteArray, 0, (int32_t) cppString.size(), reinterpret_cast<const jbyte*>(cppString.data()));

    return jByteArray;
}

void LlamaCpp::suppressSpecialTokens(double* probabilities, const llama_model* model) {
    // Suppress special tokens by setting their probabilities to 0
    for (llama_token token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        if (LlamaCpp::isSpecial(token, model)) {
            probabilities[token] = 0;
        }
    }
}

bool LlamaCpp::isEndOfSentence(llama_token token, const llama_context* ctx) {
    // Detokenize the token and check if it ends with a punctuation mark (covers "?" vs " ?" etc)
    std::string detokenization = LlamaCpp::detokenize(token, ctx);

    bool isSentenceFinished = detokenization.back() == '.'
                              || detokenization.back() == '?'
                              || detokenization.back() == '!';

    return isSentenceFinished;
}

llama_token LlamaCpp::getEndOfGeneration(const llama_model* model) {
    llama_tokens eogTokens;

    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        if (LlamaCpp::isEndOfGeneration(token, model)) {
            eogTokens.push_back(token);
        }
    }

    return eogTokens.front();
}

llama_token LlamaCpp::getAsciiNul(const llama_model* model, const llama_context* ctx) {
    // Only checks if detokenization contains the ASCII NUL character
    // Checking if it is equal to it would require constructing a string that only contains the NUL char, which conflicts with C/C++ strings being NUL-terminated
    // TODO llama.cpp's common_detokenize seems to return a string that only contains the NUL char, figure out how they do it
    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        if (LlamaCpp::detokenize(token, ctx).find('\0') != std::string::npos) {
            return token;
        }
    }

    throw std::runtime_error("LLM vocabulary doesn't contain ASCII NUL character");
}

// TODO Downward concat of split cover text
//  LlamaCpp::getAscii{Stx,Etx} are to get start and stop signal
llama_token LlamaCpp::getAsciiStx(const llama_model* model, const llama_context* ctx) {
    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        if (LlamaCpp::detokenize(token, ctx) == "\x02") {
            return token;
        }
    }

    throw std::runtime_error("LLM vocabulary doesn't contain ASCII STX character");
}

llama_token LlamaCpp::getAsciiEtx(const llama_model* model, const llama_context* ctx) {
    for (int32_t token = 0; token < LlamaCpp::getVocabSize(model); token++) {
        if (LlamaCpp::detokenize(token, ctx) == "\x03") {
            return token;
        }
    }

    throw std::runtime_error("LLM vocabulary doesn't contain ASCII ETX character");
}

int32_t LlamaCpp::getVocabSize(const llama_model* model) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    int32_t n_vocab = llama_vocab_n_tokens(vocab);

    return n_vocab;
}

llama_tokens LlamaCpp::tokenize(JNIEnv* env, jbyteArray jByteArray, const llama_context* ctx) {
    // Convert Java byte array storing UTF-8 encoding of string to be tokenized to C++ string
    jboolean isCopy = true;
    jbyte* jBytes = env->GetByteArrayElements(jByteArray, &isCopy);

    std::string cppString(reinterpret_cast<char*>(jBytes), env->GetArrayLength(jByteArray));

    // Tokenize string, save tokens as llama_tokens (equivalent to std::vector<llama_token>, with llama_token equivalent to int32_t)
    // See common.cpp: common_tokenize(ctx, ...) calls common_tokenize(vocab, ...), which calls llama_tokenize, always passing parameters {add,parse}_special through
    llama_tokens tokens = common_tokenize(ctx, cppString, false, true);

    // Release memory allocated above again
    env->ReleaseByteArrayElements(jByteArray, jBytes, 0);

    return tokens;
}

float* LlamaCpp::getLogits(llama_tokens tokens, llama_context* ctx) {
    // Get model the context was created with
    const llama_model* model = llama_get_model(ctx);

    // C++ allows accessing illegal array indices and returns garbage values, doesn't throw IndexOutOfBoundsException like Java/Kotlin
    // Manually ensure that indices stay within dimensions n_tokens x n_vocab of the logit matrix
    int32_t n_tokens = tokens.size();

    // Store tokens to be processed in batch data structure
    // llama.cpp example cited below stores multiple tokens from tokenization of the prompt in the first run, single last sampled token in subsequent runs
    // TODO
    //  llama.cpp docs: "NOTE: this is a helper function to facilitate transition to the new batch API - avoid using it"
    //  But is used like this in https://github.com/ggml-org/llama.cpp/blob/master/examples/simple/simple.cpp
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);

    // Check if model architecture is encoder-decoder or decoder-only
    if (llama_model_has_encoder(model)) {
        // Run encoder to calculate logits for the next token
        // Return value of llama_encode only indicates success/error, actual result is stored internally in cppCtx
        int32_t encode = llama_encode(ctx, batch);
    }

    // Run decoder to calculate logits for the next token
    // Return value of llama_decode only indicates success/error, actual result is stored internally in cppCtx
    int32_t decode = llama_decode(ctx, batch);

    // Get pointer to the logit matrix
    float* logits = llama_get_logits(ctx);

    return logits;
}

float* LlamaCpp::getLogits(llama_token token, llama_context* ctx) {
    llama_tokens tokens = std::vector<llama_token>{token};

    float* logits = LlamaCpp::getLogits(tokens, ctx);

    return logits;
}
