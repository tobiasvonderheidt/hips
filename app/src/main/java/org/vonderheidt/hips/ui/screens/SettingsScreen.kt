package org.vonderheidt.hips.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Text(text = "Hello World!")
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