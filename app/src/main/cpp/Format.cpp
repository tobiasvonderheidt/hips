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
