package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay

/**
 * Function to encode secret message into cover text using given context.
 */
suspend fun encode(context: String, secretMessage: String): String {
    // Wait 5 seconds
    delay(5000)

    // Return placeholder string
    return "Encode of $secretMessage using $context"
}

/**
 * Function to decode secret message from cover text using given context.
 */
suspend fun decode(context: String, coverText: String): String {
    // Wait 5 seconds
    delay(5000)

    // Return placeholder string
    return "Decode of $coverText using $context"
}