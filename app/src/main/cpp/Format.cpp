#include "Format.h"

std::vector<bool> Format::asBitVector(JNIEnv* env, jbyteArray jByteArray) {
    // Number of bytes
    jint jByteArrayLength = env->GetArrayLength(jByteArray);

    // Bit vector
    std::vector<bool> bitVector(jByteArrayLength * 8);

    // Buffer to hold the bytes
    jbyte* jBytes = env->GetByteArrayElements(jByteArray, nullptr);

    // Fill the bit vector
    // Loop over bytes in byte array
    for (int i = 0; i < jByteArrayLength; i++) {
        // Loop over bits in byte
        for (int j = 0; j < 8; j++) {
            // Extract a bit from the byte:
            // Calculate position of the bit to retrieve with (7 - j), going from MSB to LSB
            // Shift right by this amount to make it the LSB
            // Use bitwise AND with bit mask 0000 0001 to isolate it (i.e. to set all more significant bits to 0)
            bitVector[i * 8 + j] = (jBytes[i] >> (7 - j)) & 0b00000001;
        }
    }

    // Release buffer from memory
    // Last argument tells JNI to release the byte array back to the JVM without copying any changes made to the buffer
    env->ReleaseByteArrayElements(jByteArray, jBytes, 0);

    return bitVector;
}

jbyteArray Format::asByteArray(JNIEnv* env, std::vector<bool> bitVector) {
    // Number of bytes
    int jByteArrayLength = bitVector.size() / 8;

    // Byte array
    jbyteArray jByteArray = env->NewByteArray(jByteArrayLength);

    // Fill the byte array
    // Loop over bytes in byte array
    for (int i = 0; i < jByteArrayLength; i++) {
        // Temporary variable to construct the byte
        jbyte jByte = 0;

        // Loop over bits in byte
        for (int j = 0; j < 8; j++) {
            // Construct a byte from the bits:
            // Calculate position of bit to retrieve with [i * 8 + j], going from MSB to LSB
            // Shift the bit left by (7 - j) to restore its original significance
            // Use bitwise OR to add it to the byte
            jByte |= (bitVector[i * 8 + j] << (7 - j));
        }

        // Add constructed byte to byte array
        env->SetByteArrayRegion(jByteArray, i, 1, &jByte);
    }

    return jByteArray;
}

std::vector<bool> Format::asBitVectorWithoutPadding(JNIEnv* env, jbyteArray jByteArray) {
    // Convert Java ByteArray to bit vector as is
    std::vector<bool> bitVector = Format::asBitVector(env, jByteArray);

    // Remove padding length and padding from bit vector
    int paddingLength = 0;

    for (int i = 0; i < 8; i++) {
        paddingLength |= (bitVector[i] << (7 - i));
    }

    bitVector.erase(bitVector.begin(), bitVector.begin() + 8 + paddingLength);

    return bitVector;
}

jbyteArray Format::asByteArrayWithPadding(JNIEnv* env, const std::vector<bool>& bitVector) {
    // Pad bit vector to length multiple of 8
    size_t paddingLength = (8 - (bitVector.size() % 8)) % 8;    // Outer % is for case that length of bit string is already multiple of 8

    std::vector<bool> paddedBitVector(paddingLength, false);
    paddedBitVector.insert(paddedBitVector.end(), bitVector.begin(), bitVector.end());

    // Create Java ByteArray with extra byte
    jbyteArray jByteArray = env->NewByteArray(1 + (paddedBitVector.size() / 8));

    // First byte stores padding length
    auto paddingByte = static_cast<jbyte>(paddingLength);
    env->SetByteArrayRegion(jByteArray, 0, 1, &paddingByte);

    // Subsequent bytes store bytes from padded bit vector
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
    // Only edge case covered in Stegasuras
    if (numberOfBits == 0) {
        return {};
    }

    // Convert long long number to bit vector of desired length with any necessary 0-padding at the start
    std::vector<bool> bitVector(numberOfBits, false);

    for (int i = 0; i < numberOfBits; i++) {
        // (1LL << (numberOfBits - 1 - i)) creates bit mask for a single bit, going from MSB to LSB of loong
        // Bitwise AND of loong with bit mask isolates this bit from long
        // Check if this bit is 1 and set bit in vector accordingly, so that bitVector[0] corresponds to MSB of loong etc
        bitVector[i] = (loong & (1LL << (numberOfBits - 1 - i))) != 0;
    }

    return bitVector;
}

long long Format::asLong(const std::vector<bool>& bitVector) {
    // Convert bit vector to long long number, dropping leading 0s
    long long loong = 0;

    // Loop through bit vector, going from MSB to LSB
    for (size_t i = 0; i < bitVector.size(); i++) {
        // Concat bits using left shift and bitwise OR to construct loong
        loong = (loong << 1) | bitVector[i];
    }

    return loong;
}
