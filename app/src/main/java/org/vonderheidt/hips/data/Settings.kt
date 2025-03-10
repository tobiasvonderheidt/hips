package org.vonderheidt.hips.data

import org.vonderheidt.hips.utils.ConversionMode
import org.vonderheidt.hips.utils.LlamaCpp
import org.vonderheidt.hips.utils.SteganographyMode

/**
 * Object (i.e. singleton class) that represents the user settings. Holds default values to be set upon installation of this app.
 */
object Settings {
    // Define default values
    private val defaultConversionMode = ConversionMode.Arithmetic
    private val defaultSystemPrompt = """
        You and I are friends, talking about what we did on the weekend.
        Use phrases and abbreviations typical for chat messages.
        Be brief and casual, but friendly and engaging.
    """.trimIndent().replace("\n", " ")
    private val defaultNumberOfMessages = 0
    private val defaultSteganographyMode = SteganographyMode.Arithmetic
    private val defaultTemperature = 1.0f
    private val defaultTopK = 0             // Only used if LLM is not in memory
    private val defaultPrecision = 0        // Only used if LLM is not in memory
    private val defaultBlockSize = 3
    private val defaultBitsPerToken = 2

    // Initialize current values with defaults
    var conversionMode = defaultConversionMode
    var systemPrompt = defaultSystemPrompt
    var numberOfMessages = defaultNumberOfMessages
    var steganographyMode = defaultSteganographyMode
    var temperature = defaultTemperature
    var topK = defaultTopK
    var precision = defaultPrecision
    var blockSize = defaultBlockSize
    var bitsPerToken = defaultBitsPerToken

    /**
     * Function to reset the settings to their default values.
     *
     * @param general Boolean that is true if general settings are to be reset, false otherwise.
     * @param llm Boolean that is true if settings specific to the LLM are to be reset, false otherwise.
     */
    fun reset(general: Boolean, llm: Boolean) {
        if (general) {
            conversionMode = defaultConversionMode
            systemPrompt = defaultSystemPrompt
            numberOfMessages = defaultNumberOfMessages
            steganographyMode = defaultSteganographyMode
            temperature = defaultTemperature
            blockSize = defaultBlockSize
            bitsPerToken = defaultBitsPerToken
        }
        if (llm) {
            topK = if (LlamaCpp.isInMemory()) LlamaCpp.getVocabSize() else defaultTopK
            precision = defaultPrecision
        }
    }
}