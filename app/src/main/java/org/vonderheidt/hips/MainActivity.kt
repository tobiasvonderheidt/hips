package org.vonderheidt.hips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.vonderheidt.hips.data.HiPSDataStore
import org.vonderheidt.hips.data.HiPSDatabase
import org.vonderheidt.hips.navigation.NavGraph
import org.vonderheidt.hips.ui.theme.HiPSTheme
import org.vonderheidt.hips.utils.LLM
import org.vonderheidt.hips.utils.LlamaCpp

/**
 * Class that defines the entry point into the app and calls the main screen.
 */
class MainActivity : ComponentActivity() {
    // Boilerplate code
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set MainScreen function as content of the main screen
        setContent {
            // Use HiPS theme for dark and light mode
            HiPSTheme {
                // Scaffold arranges top bar/bottom bar/floating action buttons/etc. on screen
                // innerPadding is necessary so that content and top bar/etc. don't overlap
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val modifier: Modifier = Modifier.padding(innerPadding)

                    // Initialize navigation
                    NavGraph.Setup(modifier)
                }
            }
        }

        // Load LLM on app startup
        if (LLM.isDownloaded()) {
            CoroutineScope(Dispatchers.IO).launch { LlamaCpp.startInstance() }
        }

        // Instantiate Room database on app startup
        // Application context isn't directly available on conversation screen
        HiPSDatabase.startInstance(applicationContext)

        // Instantiate DataStore on app startup and read stored settings
        // Writes default settings to DataStore if app was just installed
        HiPSDataStore.startInstance(applicationContext)
        lifecycleScope.launch { HiPSDataStore.readSettings() }
    }

    // Load C++ libraries
    companion object {
        init {
            System.loadLibrary("hips")
        }
    }
}