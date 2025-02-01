package org.vonderheidt.hips.data

import org.vonderheidt.hips.utils.ConversionMode
import org.vonderheidt.hips.utils.SteganographyMode

/**
 * Object (i.e. singleton class) that represents the user settings. Holds default values to be set upon installation of this app.
 */
object Settings {
    var conversionMode = ConversionMode.Arithmetic
    var steganographyMode = SteganographyMode.Arithmetic
    var temperature = 0.9f
    var blockSize = 3
    var bitsPerToken = 3
}