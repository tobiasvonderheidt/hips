package org.vonderheidt.hips.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme
import org.vonderheidt.hips.utils.LLM
import org.vonderheidt.hips.utils.LlamaCpp

/**
 * Function that defines the settings screen.
 */
@Composable
fun SettingsScreen(navController: NavController, modifier: Modifier) {
    // State variables
    var isDownloaded by rememberSaveable { mutableStateOf(LLM.isDownloaded()) }
    var isInMemory by rememberSaveable { mutableStateOf(false) }
    var model by rememberSaveable { mutableLongStateOf(0L) }

    // Scrolling
    val scrollState = rememberScrollState()

    // Download and links
    val currentLocalContext = LocalContext.current

    // Coroutines
    val coroutineScope = rememberCoroutineScope()

    // UI components
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        // No explicit alignment (via modifier or horizontalArrangement argument) needed here since left align is default
        Row(
            modifier = modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    // Navigate back to home screen
                    navController.navigate(Screen.Home.route) {
                        // Empty back stack, including home screen
                        // Otherwise app won't close when user goes back once more via the phone's back button
                        popUpTo(Screen.Home.route) {
                            inclusive = true
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Go back to home screen"
                )
            }
        }

        // LLM download hint
        Row(
            modifier = modifier.fillMaxWidth(0.9f)
        ) {
            if (isDownloaded) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Check mark"
                )
            }
            else {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = "Warning"
                )
            }

            Spacer(modifier = modifier.width(16.dp))

            Column {
                Text(
                    text = "Large Language Model",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isDownloaded) {
                    Text(text = "The LLM has been downloaded. You can now start using this app.")
                }
                else {
                    Text(text = "Before using this app, you need to download the LLM.")
                }
            }
        }

        Spacer(modifier = modifier.height(16.dp))

        // Download button
        Row {
            Button(
                onClick = {
                    if(!isDownloaded) {
                        LLM.download(currentLocalContext)
                        isDownloaded = true
                    }
                },
                enabled = !isDownloaded,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "Download")
            }
        }

        Spacer(modifier = modifier.height(16.dp))

        // Button to load LLM into memory
        if (isDownloaded) {
            Row(
                modifier = modifier.fillMaxWidth(0.9f)
            ) {
                Icon(
                    imageVector = if (!isInMemory) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (!isInMemory) "Load LLM into memory" else "Unload LLM from memory"
                )

                Spacer(modifier = modifier.width(16.dp))

                Text(text = "The LLM needs to be loaded into memory.")
            }

            Spacer(modifier = modifier.height(16.dp))

            Button(
                onClick = {
                    // Load and unload model in coroutines so app doesn't crash
                    if (!isInMemory) {
                        coroutineScope.launch { model = LlamaCpp.loadModel() }
                        isInMemory = true
                    }
                    else {
                        coroutineScope.launch { LlamaCpp.unloadModel(model) }
                        isInMemory = false
                    }
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = if (!isInMemory) "Start LLM" else "Stop LLM")
            }

            Spacer(modifier = modifier.height(16.dp))
        }

        HorizontalDivider()

        Spacer(modifier = modifier.height(16.dp))

        // Author credits
        // Make the whole row clickable instead of just the text for better accessibility
        Row(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .clickable(
                    onClick = {
                        // Open email app and create draft with subject "HiPS"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:tobias@vonderheidt.org?subject=HiPS"))
                        currentLocalContext.startActivity(intent)
                    }
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "The author of this app is Tobias Vonderheidt"
            )

            Spacer(modifier = modifier.width(16.dp))

            Column {
                Text(
                    text = "Author",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(text = "Tobias Vonderheidt <tobias@vonderheidt.org>")
            }
        }

        Spacer(modifier = modifier.height(16.dp))

        // Link to source code
        Row(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .clickable(
                    onClick = {
                        // Open the repo website
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tobiasvonderheidt/hips"))
                        currentLocalContext.startActivity(intent)
                    }
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "Link to the source code of this app"
            )

            Spacer(modifier = modifier.width(16.dp))

            Column {
                Text(
                    text = "Source Code",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(text = "github.com/tobiasvonderheidt/hips")
            }
        }

        Spacer(modifier = modifier.height(32.dp))
    }
}

/**
 * Function to show preview of the settings screen in Android Studio.
 */
@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    // No Scaffold, no innerPadding
    HiPSTheme {
        val modifier: Modifier = Modifier
        val navController: NavHostController = rememberNavController()

        SettingsScreen(navController, modifier)
    }
}