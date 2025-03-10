package org.vonderheidt.hips.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme
import org.vonderheidt.hips.utils.LLM
import org.vonderheidt.hips.utils.LlamaCpp
import org.vonderheidt.hips.utils.Steganography

/**
 * Function that defines the home screen.
 */
@Composable
fun HomeScreen(navController: NavController, modifier: Modifier) {
    // State variables
    val isDownloaded by rememberSaveable { mutableStateOf(LLM.isDownloaded()) }
    var context by rememberSaveable { mutableStateOf("") }
    var secretMessage by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var isOutputVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var coverText by rememberSaveable { mutableStateOf("") }

    // Start button
    val modes = listOf("Encode", "Decode")

    // Scrolling
    val scrollState = rememberScrollState()

    // Clipboard
    val currentLocalContext = LocalContext.current
    val clipboardManager = currentLocalContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // UI components
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Conversation icon
            IconButton(
                onClick = { navController.navigate(Screen.Conversation.route) }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = "Conversations"
                )
            }

            // Settings icon
            // Use a Box to overlay Badge and IconButton
            Box {
                IconButton(
                    onClick = { navController.navigate(Screen.Settings.route) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings"
                    )
                }

                if (!isDownloaded) {
                    Badge(
                        modifier = modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(8.dp)
                    )
                }
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
                if (context.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear context",
                        modifier = modifier.clickable { context = "" }
                    )
                }
            },
            maxLines = 5
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
                    if (secretMessage.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear secret message",
                            modifier = modifier.clickable { secretMessage = "" }
                        )
                    }
                },
                maxLines = 5
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
                    if (coverText.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear cover text",
                            modifier = modifier.clickable { coverText = "" }
                        )
                    }
                },
                maxLines = 5
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
                            isOutputVisible = false

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
                    // Check if LLM is loaded
                    if (!LlamaCpp.isInMemory()) {
                        Toast.makeText(currentLocalContext, "Load LLM into memory first", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    // Check inputs
                    if (context.isBlank()) {
                        Toast.makeText(currentLocalContext, "Context can't be blank", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (selectedMode == 0 && secretMessage.isBlank()) {
                        Toast.makeText(currentLocalContext, "Secret message can't be blank", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (selectedMode == 1 && coverText.isBlank()) {
                        Toast.makeText(currentLocalContext, "Cover text can't be blank", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    // Hide old output when start button is pressed again, show loading animation
                    isOutputVisible = false
                    isLoading = true

                    // Call encode or decode function as coroutine, depending on mode selected
                    // Use Dispatchers.Default since LLM inference is CPU-bound
                    // Keep encapsulation of coroutine vs if-else like this so the loading animation works
                    CoroutineScope(Dispatchers.Default).launch {
                        // Reset LLM instance on every button press to ensure reproducibility, otherwise ctx before encode and decode would not be the same
                        // Works when called before this coroutine or inside it, but crashes when called inside its own coroutine before this one
                        // Might be desirable to use it with Dispatchers.IO as it is presumably I/O-bound, but seems negligible
                        LlamaCpp.resetInstance()

                        if (selectedMode == 0) {
                            coverText = Steganography.encode(context, secretMessage)
                        }
                        else {
                            secretMessage = Steganography.decode(context, coverText)
                        }

                        // Hide loading animation, show new output only when encode or decode is finished
                        isLoading = false
                        isOutputVisible = true
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
        if (isOutputVisible) {
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
 * Function to show preview of the home screen in Android Studio.
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