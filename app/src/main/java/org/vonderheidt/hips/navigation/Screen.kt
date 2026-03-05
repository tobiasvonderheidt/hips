package org.vonderheidt.hips.navigation

/**
 * Class that defines routes to all screens.
 */
sealed class Screen(val route: String) {
    data object Home: Screen("home")
    data object Settings: Screen("settings")
    data object Conversation: Screen("conversation")
}