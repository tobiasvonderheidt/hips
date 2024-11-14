package org.vonderheidt.hips.navigation

/**
 * Class that defines routes to all screens.
 */
sealed class Screen(val route: String) {
    object Home: Screen("home")
    object Settings: Screen("settings")
}