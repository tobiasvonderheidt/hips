#include "Format.h"
#include <cmath>

std::vector<bool> Format::asBitVector(JNIEnv* env, jbyteArray jByteArray) {
    if (jByteArray == nullptr) return {};
    jint jByteArrayLength = env->GetArrayLength(jByteArray);
    std::vector<bool> bitVector(jByteArrayLength * 8);
    jbyte* jBytes = env->GetByteArrayElements(jByteArray, nullptr);

    for (int i = 0; i < jByteArrayLength; i++) {
        for (int j = 0; j < 8; j++) {
            bitVector[i * 8 + j] = (jBytes[i] >> (7 - j)) & 0b00000001;
        }
    }
    env->ReleaseByteArrayElements(jByteArray, jBytes, JNI_ABORT);
    return bitVector;
}

jbyteArray Format::asByteArray(JNIEnv* env, std::vector<bool> bitVector) {
    // FIX: Use ceiling division to prevent losing trailing bits
    int jByteArrayLength = (bitVector.size() + 7) / 8;
    jbyteArray jByteArray = env->NewByteArray(jByteArrayLength);

    for (int i = 0; i < jByteArrayLength; i++) {
        jbyte jByte = 0;
        for (int j = 0; j < 8; j++) {
            size_t bitIdx = i * 8 + j;
            if (bitIdx < bitVector.size()) {
                jByte |= (bitVector[bitIdx] << (7 - j));
            }
        }
        env->SetByteArrayRegion(jByteArray, i, 1, &jByte);
    }
    return jByteArray;
}

std::vector<bool> Format::asBitVectorWithoutPadding(JNIEnv* env, jbyteArray jByteArray) {
    std::vector<bool> bitVector = Format::asBitVector(env, jByteArray);
    if (bitVector.size() < 8) return bitVector;

    int paddingLength = 0;
    for (int i = 0; i < 8; i++) {
        paddingLength |= (bitVector[i] << (7 - i));
    }

    if (bitVector.size() >= (size_t)(8 + paddingLength)) {
        bitVector.erase(bitVector.begin(), bitVector.begin() + 8 + paddingLength);
    }
    return bitVector;
}

jbyteArray Format::asByteArrayWithPadding(JNIEnv* env, const std::vector<bool>& bitVector) {
    size_t paddingLength = (8 - (bitVector.size() % 8)) % 8;
    std::vector<bool> paddedBitVector(paddingLength, false);
    paddedBitVector.insert(paddedBitVector.end(), bitVector.begin(), bitVector.end());

    jbyteArray jByteArray = env->NewByteArray(1 + (paddedBitVector.size() / 8));
    auto paddingByte = static_cast<jbyte>(paddingLength);
    env->SetByteArrayRegion(jByteArray, 0, 1, &paddingByte);

    for (size_t i = 0; i < paddedBitVector.size() / 8; i++) {
        jbyte jByte = 0;
        for (size_t j = 0; j < 8; j++) {
            jByte |= (paddedBitVector[i * 8 + j] << (7 - j));
        }
        env->SetByteArrayRegion(jByteArray, i + 1, 1, &jByte);
    }
    return jByteArray;
}

std::vector<bool> Format::asBitVector(long long loong, int numberOfBits) {
    if (numberOfBits <= 0) return {};
    std::vector<bool> bitVector(numberOfBits, false);
    for (int i = 0; i < numberOfBits; i++) {
        bitVector[i] = (loong & (1LL << (numberOfBits - 1 - i))) != 0;
    }
    return bitVector;
}

long long Format::asLong(const std::vector<bool>& bitVector) {
    long long loong = 0;
    for (size_t i = 0; i < bitVector.size(); i++) {
        loong = (loong << 1) | (bitVector[i] ? 1 : 0);
    }
    return loong;
}
