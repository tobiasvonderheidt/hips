package org.vonderheidt.hips.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File

/**
 * Object (i.e. singleton class) to handle LLM download.
 */
object LLM {
    private const val DOWNLOAD_LINK = "https://huggingface.co/hugging-quants/Llama-3.2-1B-Instruct-Q4_K_M-GGUF/resolve/main/llama-3.2-1b-instruct-q4_k_m.gguf"
    private const val FILE_NAME = "llama-3.2-1b-instruct-q4_k_m.gguf"

    /**
     * Function to check if the LLM has already been downloaded.
     */
    fun isDownloaded(): Boolean {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, FILE_NAME)

        return file.exists()
    }

    /**
     * Function to download the LLM.
     */
    fun download(currentLocalContext: Context) {
        // Get access to Android's download manager
        val downloadManager = currentLocalContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Define request to the download manager that downloads the file from the given URL
        val request = DownloadManager.Request(Uri.parse(DOWNLOAD_LINK))
            .setTitle(FILE_NAME)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, FILE_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        // Queue request and save download ID
        // Will even wait for an internet connection to become available, i.e. being offline doesn't cause an error as caught below
        val downloadId: Long = downloadManager.enqueue(request)

        // Check for errors
        if (downloadId != -1L) {
            // Show confirmation via toast message
            Toast.makeText(currentLocalContext, "Download started", Toast.LENGTH_LONG).show()
        }
        else {
            // Show error via toast message
            Toast.makeText(currentLocalContext, "Download failed", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Function to get the path to the LLM (.gguf file).
     */
    fun getPath(): String {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val path = "$downloadDir/$FILE_NAME"

        return path
    }
}