package org.vonderheidt.hips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.vonderheidt.hips.ui.theme.HiPSTheme

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
                    MainScreen(modifier = modifier)
                }
            }
        }
    }
}

/**
 * Function that defines contents of the main screen.
 */
@Composable
fun MainScreen(modifier: Modifier) {
    Text(
        text = "Hello World!",
        modifier = modifier
    )
}

/**
 * Function to show preview of the main screen in Android Studio.
 */
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    // No Scaffold, no innerPadding
    HiPSTheme {
        val modifier: Modifier = Modifier
        MainScreen(modifier = modifier)
    }
}