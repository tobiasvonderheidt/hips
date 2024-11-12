package org.vonderheidt.hips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    // State variables
    var context by rememberSaveable { mutableStateOf("") }
    var secretMessage by rememberSaveable { mutableStateOf("") }
    var coverText by rememberSaveable { mutableStateOf("") }

    // UI components
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1st input is context
        OutlinedTextField(
            value = context,
            onValueChange = { context = it },
            modifier = modifier.fillMaxWidth(0.8f),
            label = { Text(text = "Context") }
        )

        Spacer(modifier = modifier.height(32.dp))

        // 2nd input is secret message
        OutlinedTextField(
            value = secretMessage,
            onValueChange = { secretMessage = it },
            modifier = modifier.fillMaxWidth(0.8f),
            label = { Text(text = "Secret message") }
        )

        Spacer(modifier = modifier.height(32.dp))

        // Start button
        Button(
            onClick = { coverText = "Encode of $secretMessage using $context" },
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(text = "Start")
        }

        Spacer(modifier = modifier.height(32.dp))

        // Output is cover text
        Text(
            text = coverText,
            modifier = modifier.fillMaxWidth(0.8f)
        )
    }
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