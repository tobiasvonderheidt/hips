package org.vonderheidt.hips.utils

/**
 * Class that defines all roles for llama.cpp chat messages.
 */
sealed class Role(val name: String) {
    data object Assistant: Role("assistant")
    data object System: Role("system")
    data object User: Role("user")
}