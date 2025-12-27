#ifndef FORMAT_H
#define FORMAT_H

#include <jni.h>
#include <vector>

/**
 * Class that represents various functions for bit vector formatting.
 */
class Format {
public:
    /**
     * Function to format a Java ByteArray as a bit vector.
     * Doesn't remove any padding, so length of bit vector will be multiple of 8.
     *
     * @param env The JNI environment.
     * @param byteArray A Java ByteArray.
     * @return The bit vector.
     */
    static std::vector<bool> asBitVector(JNIEnv* env, jbyteArray jByteArray);

    /**
     * Function to reverse formatting of a Java ByteArray as a bit vector (i.e. to reverse `Format::asBitVector(JNIEnv*, jbyteArray)`).
     * Doesn't add any padding, assumes that length of bit vector already is multiple of 8.
     *
     * @param env The JNI environment.
     * @param bitVector A bit vector.
     * @return The Java ByteArray.
     */
    static jbyteArray asByteArray(JNIEnv* env, std::vector<bool> bitVector);
};

#endif
