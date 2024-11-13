package org.vonderheidt.hips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val modes = listOf("Encode", "Decode")
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
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
            label = { Text(text = "Context") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Context"
                )
            }
        )

        Spacer(modifier = modifier.height(32.dp))

        // 2nd input is either secret message or cover text
        if (selectedMode == 0) {
            OutlinedTextField(
                value = secretMessage,
                onValueChange = { secretMessage = it },
                modifier = modifier.fillMaxWidth(0.8f),
                label = { Text(text = "Secret message") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Secret message"
                    )
                }
            )
        }
        else {
            OutlinedTextField(
                value = coverText,
                onValueChange = { coverText = it },
                modifier = modifier.fillMaxWidth(0.8f),
                label = { Text(text = "Cover text") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = "Cover text"
                    )
                }
            )
        }

        Spacer(modifier = modifier.height(32.dp))

        Row(
            modifier = modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode selector
            SingleChoiceSegmentedButtonRow {
                // Mostly follows example given in docs
                modes.forEachIndexed { index, _ ->
                    SegmentedButton(
                        selected = index == selectedMode,
                        onClick = {
                            // Clear both secret message and cover text when mode is changed
                            selectedMode = index
                            secretMessage = ""
                            coverText = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                            baseShape = RoundedCornerShape(4.dp)
                        ),
                        label = { Text(text = modes[index]) }
                    )
                }
            }

            // Start button
            Button(
                onClick = {
                    if (selectedMode == 0) {
                        coverText = "Encode of $secretMessage using $context"
                    }
                    else {
                        secretMessage = "Decode of $coverText using $context"
                    }
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "Start")
            }
        }

        Spacer(modifier = modifier.height(32.dp))

        // Output is either cover text or secret message
        if (selectedMode == 0) {
            Text(
                text = "Cover text:",
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = modifier.height(16.dp))

            Text(
                text = coverText,
                modifier = modifier.fillMaxWidth(0.8f)
            )
        }
        else {
            Text(
                text = "Secret message:",
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = modifier.height(16.dp))

            Text(
                text = secretMessage,
                modifier = modifier.fillMaxWidth(0.8f)
            )
        }
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