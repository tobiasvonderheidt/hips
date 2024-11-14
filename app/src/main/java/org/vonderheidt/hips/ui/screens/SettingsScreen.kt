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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme

/**
 * Function that defines the settings screen.
 */
@Composable
fun SettingsScreen(navController: NavController, modifier: Modifier) {
    // State variables
    // Scrolling
    val scrollState = rememberScrollState()
    // Links
    val currentLocalContext = LocalContext.current

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

                Text(
                    text = "Tobias Vonderheidt <tobias@vonderheidt.org>"
                )
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

                Text(
                    text = "github.com/tobiasvonderheidt/hips"
                )
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