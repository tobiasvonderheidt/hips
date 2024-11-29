package org.vonderheidt.hips.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.vonderheidt.hips.ui.screens.ConversationScreen
import org.vonderheidt.hips.ui.screens.HomeScreen
import org.vonderheidt.hips.ui.screens.SettingsScreen

/**
 * Function that initializes the navigation.
 */
@Composable
fun SetupNavGraph(modifier: Modifier) {
    val navController: NavHostController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) { HomeScreen(navController, modifier) }
        composable(Screen.Settings.route) { SettingsScreen(navController, modifier) }
        composable(Screen.Conversation.route) { ConversationScreen(navController, modifier) }
    }
}