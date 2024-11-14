package org.vonderheidt.hips.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.vonderheidt.hips.ui.theme.HiPSTheme

/**
 * Function that defines the settings screen.
 */
@Composable
fun SettingsScreen(navController: NavController, modifier: Modifier) {
    // State variables
    // Scrolling
    val scrollState = rememberScrollState()

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
                    navController.navigate("home") {
                        // Empty back stack, including home screen
                        // Otherwise app won't close when user goes back once more via the phone's back button
                        popUpTo("home") {
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