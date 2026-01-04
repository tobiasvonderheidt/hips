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

    /**
     * Function to format a Java ByteArray as a bit vector. Assumes that the ByteArray is 0-padded and that the first byte stores the length of the padding in bits.
     * Removes both the padding length and the padding.
     *
     * @param env The JNI environment.
     * @param jByteArray A Java ByteArray, 0-padded with length of padding in bits stored in first byte.
     * @return The bit vector, with padding removed.
     */
    static std::vector<bool> asBitVectorWithoutPadding(JNIEnv* env, jbyteArray jByteArray);

    /**
     * Function to reverse formatting of a padded Java ByteArray as a bit vector (i.e. to reverse `Format::asBitVectorWithoutPadding(JNIEnv*, jbyteArray)`).
     * Adds 0-padding at the start so that length of bit vector is multiple of 8. Prepends a byte that stores length of padding in bits.
     *
     * @param env The JNI environment.
     * @param bitVector A bit vector.
     * @return The Java ByteArray, 0-padded with length of padding in bits stored in first byte.
     */
    static jbyteArray asByteArrayWithPadding(JNIEnv* env, const std::vector<bool>& bitVector);

    /**
     * Function to format a long long number as a bit vector of desired length. Pads the bit vector with leading 0s if needed.
     *
     * Corresponds to Stegasuras method `int2bits` in `utils.py`. Parameter `inp` was renamed to `loong`, `num_bits` to `numberOfBits`.
     *
     * @param loong A long long number.
     * @param numberOfBits The desired length of the bit vector.
     * @return The bit vector.
     */
    static std::vector<bool> asBitVector(long long loong, int numberOfBits);

    /**
     * Function to reverse formatting of a long long number as a bit vector (i.e. to reverse `Format::asBitVector(long long, int)`).
     *
     * Corresponds to Stegasuras method `bits2int` in `utils.py`. Parameter `bits` was renamed to `bitVector`.
     *
     * @param bitVector A bit vector containing a long long number.
     * @return The long long number.
     */
    static long long asLong(const std::vector<bool>& bitVector);
};

#endif
