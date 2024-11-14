package org.vonderheidt.hips.utils

import kotlinx.coroutines.delay

/**
 * Function to check if the LLM has already been downloaded.
 */
fun llmDownloaded(): Boolean {
    // Initially, LLM hasn't been downloaded yet
    return false
}

/**
 * Function to download the LLM.
 */
suspend fun downloadLLM() {
    // Wait 5 seconds
    delay(5000)
}