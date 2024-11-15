package org.vonderheidt.hips.utils

import android.os.Environment
import kotlinx.coroutines.delay
import java.io.File

/**
 * Function to check if the LLM has already been downloaded.
 */
fun llmDownloaded(): Boolean {
    // Let the LLM be called "llm.gguf"
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val fileName = "llm.gguf"

    val file = File(downloadDir, fileName)

    return file.exists()
}

/**
 * Function to download the LLM.
 */
suspend fun downloadLLM() {
    // Wait 5 seconds
    delay(5000)
}