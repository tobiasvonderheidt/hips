package org.vonderheidt.hips.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.vonderheidt.hips.decode
import org.vonderheidt.hips.encode
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme

/**
 * Function that defines contents of the home screen.
 */
@Composable
fun HomeScreen(navController: NavController, modifier: Modifier) {
    // State variables
    var context by rememberSaveable { mutableStateOf("") }
    var secretMessage by rememberSaveable { mutableStateOf("") }
    val modes = listOf("Encode", "Decode")
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var outputVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var coverText by rememberSaveable { mutableStateOf("") }

    // Scrolling
    val scrollState = rememberScrollState()

    // Clipboard
    val currentLocalContext = LocalContext.current
    val clipboardManager = currentLocalContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // Coroutines
    val coroutineScope = rememberCoroutineScope()

    // UI components
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Settings icon
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = { navController.navigate(Screen.Settings.route) }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        // App name
        // Doesn't need modifier since horizontalAlignment of Column already centers it
        Row {
            Text(
                text = "HiPS",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = modifier.height(32.dp))

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
            },
            trailingIcon = {
                if (context != "") {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear context",
                        modifier = modifier.clickable { context = "" }
                    )
                }
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
                },
                trailingIcon = {
                    if (secretMessage != "") {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear secret message",
                            modifier = modifier.clickable { secretMessage = "" }
                        )
                    }
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
                },
                trailingIcon = {
                    if (coverText != "") {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear cover text",
                            modifier = modifier.clickable { coverText = "" }
                        )
                    }
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
                            // Update the selected mode when a button is tapped on
                            selectedMode = index

                            // Hide old output when mode is changed
                            outputVisible = false

                            // Clear both secret message and cover text when mode is changed
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
                    // Hide old output when start button is pressed again, show loading animation
                    outputVisible = false
                    isLoading = true

                    // Call encode or decode function as coroutine, depending on mode selected
                    coroutineScope.launch {
                        if (selectedMode == 0) {
                            coverText = encode(context, secretMessage)
                        }
                        else {
                            secretMessage = decode(context, coverText)
                        }

                        // Hide loading animation, show new output only when encode or decode is finished
                        isLoading = false
                        outputVisible = true
                    }
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "Start")
            }
        }

        // Loading animation to show when start button is pressed
        if (isLoading) {
            Spacer (modifier = modifier.height(64.dp))

            CircularProgressIndicator()
        }

        // Output is either cover text or secret message
        // Only show when encode or decode is finished (i.e. also not on app startup)
        if (outputVisible) {
            Spacer(modifier = modifier.height(32.dp))

            if (selectedMode == 0) {
                Text(
                    text = "Cover text (tap to copy):",
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = modifier.height(16.dp))

                Text(
                    text = coverText,
                    modifier = modifier
                        .fillMaxWidth(0.8f)
                        .clickable {
                            // Copy cover text to clipboard
                            val clip = ClipData.newPlainText("Cover text", coverText)
                            clipboardManager.setPrimaryClip(clip)

                            // Show confirmation via toast message
                            Toast.makeText(currentLocalContext, "Copied to clipboard", Toast.LENGTH_LONG).show()
                        }
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

        Spacer(modifier = modifier.height(32.dp))
    }
}

/**
 * Function to show preview of the main screen in Android Studio.
 */
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    // No Scaffold, no innerPadding
    HiPSTheme {
        val modifier: Modifier = Modifier
        val navController: NavHostController = rememberNavController()

        HomeScreen(navController, modifier)
    }
}