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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HiPSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val modifier: Modifier = Modifier.padding(innerPadding)
                    NavGraph.Setup(modifier)
                }
            }
        }
        if (LLM.isDownloaded()) {
            CoroutineScope(Dispatchers.IO).launch {
                LlamaCpp.startInstance()
            }
        }
        HiPSDatabase.startInstance(applicationContext)
        HiPSDataStore.startInstance(applicationContext)
        lifecycleScope.launch { HiPSDataStore.readSettings() }
    }
    companion object {
        init {
            System.loadLibrary("hips")
        }
    }
}