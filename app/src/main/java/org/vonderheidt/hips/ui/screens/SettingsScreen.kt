package org.vonderheidt.hips.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.vonderheidt.hips.data.HiPSDataStore
import org.vonderheidt.hips.data.Settings
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme
import org.vonderheidt.hips.utils.ConversionMode
import org.vonderheidt.hips.utils.LLM
import org.vonderheidt.hips.utils.LlamaCpp
import org.vonderheidt.hips.utils.SteganographyMode
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Function that defines the settings screen.
 *
 * @param navController The navController from NavGraph setup.
 * @param modifier The modifier from MainActivity.
 */
@Composable
fun SettingsScreen(navController: NavController, modifier: Modifier) {
    // State variables
    var isDownloaded by rememberSaveable { mutableStateOf(LLM.isDownloaded()) }
    var isInMemory by rememberSaveable { mutableStateOf(LlamaCpp.isInMemory()) }
    var selectedConversionMode by rememberSaveable { mutableStateOf(Settings.conversionMode) }
    var systemPrompt by rememberSaveable { mutableStateOf(Settings.systemPrompt) }
    var selectedNumberOfMessages by rememberSaveable { mutableIntStateOf(Settings.numberOfMessages) }
    var selectedSteganographyMode by rememberSaveable { mutableStateOf(Settings.steganographyMode) }
    var selectedTemperature by rememberSaveable { mutableFloatStateOf(Settings.temperature) }
    var selectedTopK by rememberSaveable { mutableIntStateOf(Settings.topK) }
    var selectedPrecision by remember { mutableIntStateOf(Settings.precision) }
    var selectedBlockSize by rememberSaveable { mutableIntStateOf(Settings.blockSize) }
    var selectedBitsPerToken by rememberSaveable { mutableIntStateOf(Settings.bitsPerToken) }
    val selectedResetModes = remember { mutableStateListOf(0, 1) }

    // Scrolling
    val scrollState = rememberScrollState()

    // Download and links
    val currentLocalContext = LocalContext.current

    // Coroutines
    val coroutineScope = rememberCoroutineScope()

    // Reset button
    val resetModes = listOf("General", "LLM")

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
                    if (!isDownloaded) {
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
                    // Check if the LLM is in memory already, load/unload it and update the state variable
                    // Use coroutine with Dispatchers.IO since loading the LLM is I/O-bound, doesn't block UI in main thread anymore this way
                    if (!isInMemory) {
                        CoroutineScope(Dispatchers.IO).launch {
                            LlamaCpp.startInstance()
                            isInMemory = LlamaCpp.isInMemory()
                        }
                    }
                    else {
                        CoroutineScope(Dispatchers.IO).launch {
                            LlamaCpp.stopInstance()
                            isInMemory = LlamaCpp.isInMemory()
                        }
                    }
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = if (!isInMemory) "Start LLM" else "Stop LLM")
            }

            Spacer(modifier = modifier.height(16.dp))
        }

        // Conversion settings
        Row(
            modifier = modifier.fillMaxWidth(0.9f)
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = "Conversion settings"
            )

            Spacer(modifier = modifier.width(16.dp))

            Column {
                Text(
                    text = "Conversion",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(text = "Select how to convert the secret message from string to binary.")

                Spacer(modifier = modifier.height(16.dp))

                // Select conversion mode
                ConversionMode.entries.toTypedArray().forEach { conversionMode ->
                    Row(
                        modifier = modifier
                            .fillMaxWidth()
                            .selectable(
                                // Make the whole row selectable instead of just the button for better accessibility
                                selected = conversionMode == selectedConversionMode,
                                onClick = {
                                    // Update state variable
                                    selectedConversionMode = conversionMode

                                    // Update DataStore
                                    Settings.conversionMode = conversionMode
                                    coroutineScope.launch { HiPSDataStore.writeSettings() }
                                }
                            ),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = conversionMode == selectedConversionMode,
                            onClick = {
                                // Same as row onClick
                                selectedConversionMode = conversionMode

                                Settings.conversionMode = conversionMode
                                coroutineScope.launch { HiPSDataStore.writeSettings() }
                            }
                        )

                        // Use .toString() instead of .name to get display name
                        Text(text = conversionMode.toString())
                    }
                }
            }
        }

        Spacer(modifier = modifier.height(16.dp))

        // Steganography settings
        Row(
            modifier = modifier.fillMaxWidth(0.9f)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Steganography settings"
            )

            Spacer(modifier = modifier.width(16.dp))

            Column {
                Text(
                    text = "Steganography",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Set system prompt
                Text(text = "Set the system prompt to define the role the LLM takes in a conversation.")

                Spacer(modifier = modifier.height(16.dp))

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = {
                        // Update state variable
                        systemPrompt = it
                    },
                    modifier = modifier.fillMaxWidth(),
                    label = { Text(text = "System prompt") },
                    trailingIcon = {
                        if (systemPrompt.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "Clear system prompt",
                                modifier = modifier.clickable {
                                    // Update state variable
                                    systemPrompt = ""
                                }
                            )
                        }
                    },
                    maxLines = 5
                )

                Spacer(modifier = modifier.height(8.dp))

                Button(
                    onClick = {
                        if (systemPrompt.isBlank()) {
                            Toast.makeText(currentLocalContext, "System prompt can't be blank", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        // Update DataStore
                        Settings.systemPrompt = systemPrompt
                        coroutineScope.launch { HiPSDataStore.writeSettings() }

                        Toast.makeText(currentLocalContext, "System prompt saved", Toast.LENGTH_LONG).show()
                    },
                    modifier = modifier.align(Alignment.End),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(text = "Save")
                }

                Spacer(modifier = modifier.height(16.dp))

                // Select number of messages
                Text(text = "Select the number of prior messages to use as context.")

                Spacer(modifier = modifier.height(16.dp))

                // Slider only allows floats, do int conversion here to abstract it away from state variable
                Slider(
                    value = selectedNumberOfMessages.toFloat(),
                    onValueChange = {
                        // Update state variable
                        selectedNumberOfMessages = it.toInt()

                        // Update DataStore
                        Settings.numberOfMessages = it.toInt()
                        coroutineScope.launch { HiPSDataStore.writeSettings() }
                    },
                    valueRange = 0f..10f,
                    steps = 9
                )

                Spacer(modifier = modifier.height(8.dp))

                Text(
                    text = when (selectedNumberOfMessages) {
                        0 -> "All messages"
                        1 -> "1 message"
                        else -> "$selectedNumberOfMessages messages"
                    },
                    modifier = modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = modifier.height(16.dp))

                Text(text = "Select how to encode the secret message into a cover text.")

                Spacer(modifier = modifier.height(16.dp))

                // Select steganography mode
                SteganographyMode.entries.toTypedArray().forEach { steganographyMode ->
                    Row(
                        modifier = modifier
                            .fillMaxWidth()
                            .selectable(
                                // Again, make the whole row selectable
                                selected = steganographyMode == selectedSteganographyMode,
                                onClick = {
                                    // Update state variable
                                    selectedSteganographyMode = steganographyMode

                                    // Update DataStore
                                    Settings.steganographyMode = steganographyMode
                                    coroutineScope.launch { HiPSDataStore.writeSettings() }
                                }
                            ),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = steganographyMode == selectedSteganographyMode,
                            onClick = {
                                // Same as row onClick
                                selectedSteganographyMode = steganographyMode

                                Settings.steganographyMode = steganographyMode
                                coroutineScope.launch { HiPSDataStore.writeSettings() }
                            }
                        )

                        // Use .toString() instead of .name to get display name
                        Text(text = steganographyMode.toString())
                    }
                }

                Spacer(modifier = modifier.height(16.dp))

                // Specific settings for each steganography mode
                when (selectedSteganographyMode) {
                    SteganographyMode.Arithmetic -> {
                        Text(text = "Set the temperature for token sampling. This is the \"creativity\" of the LLM. You can play around with it.")

                        Spacer(modifier = modifier.height(16.dp))

                        // Scale up, round and scale down again to fix float artefacts (e.g. 1.299998 instead of 1.3)
                        Slider(
                            value = selectedTemperature * 10,
                            onValueChange = {
                                // Temperature can't be 0 because logits are scaled with 1/temperature
                                if (it > 0f) {
                                    // Update state variable
                                    selectedTemperature = it.roundToInt() / 10f

                                    // Update DataStore
                                    Settings.temperature = it.roundToInt() / 10f
                                    coroutineScope.launch { HiPSDataStore.writeSettings() }
                                }
                            },
                            valueRange = 0f..20f,
                            steps = 19
                        )

                        Spacer(modifier = modifier.height(8.dp))

                        Text(
                            text = "$selectedTemperature",
                            modifier = modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = modifier.height(16.dp))

                        // Show settings specific to LLM only when it is in memory
                        if (isInMemory) {
                            // Top k
                            Text(text = "Set the top k for token sampling. This is the number of most likely tokens from the LLM's vocabulary to consider. 100% (= ${LlamaCpp.getVocabSize()} tokens) is recommended.")

                            Spacer(modifier = modifier.height(16.dp))

                            // Again, do int conversion here as slider only allows floats
                            // Display top k in %, but store absolute number internally
                            Slider(
                                value = (selectedTopK.toFloat() / LlamaCpp.getVocabSize() * 100),
                                onValueChange = {
                                    // Update state variable
                                    selectedTopK = (it / 100 * LlamaCpp.getVocabSize()).toInt()

                                    // Update DataStore
                                    Settings.topK = (it / 100 * LlamaCpp.getVocabSize()).toInt()
                                    coroutineScope.launch { HiPSDataStore.writeSettings() }
                                },
                                valueRange = 0f..100f,
                                steps = 99
                            )

                            Spacer(modifier = modifier.height(8.dp))

                            Text(
                                text = "${(selectedTopK.toFloat() / LlamaCpp.getVocabSize() * 100).toInt()}% of the vocabulary",
                                modifier = modifier.align(Alignment.CenterHorizontally)
                            )

                            Spacer(modifier = modifier.height(16.dp))

                            // Precision
                            Text(
                                text = "Set the precision for token sampling. Recommended is ⌈log₂($selectedTopK)⌉ = "
                                        + if (selectedTopK > 0) { "${ceil(log2(selectedTopK.toFloat())).toInt()}" } else { "n/a" }
                                        + " bits. Other values can be more efficient, but extremes produce long cover texts."
                            )

                            Spacer(modifier = modifier.height(16.dp))

                            // Again, do int conversion here as slider only allows floats
                            // Don't expose 64 bit precision from Arithmetic compression in UI for steganography, encoding would take ages and offer no benefit with vocabulary sizes of current LLMs
                            // Using 64 bits internally also avoids integer overflows at 31-32 bits
                            Slider(
                                value = selectedPrecision.toFloat(),
                                onValueChange = {
                                    // Update state variable
                                    selectedPrecision = it.toInt()

                                    // Update DataStore
                                    Settings.precision = it.toInt()
                                    coroutineScope.launch { HiPSDataStore.writeSettings() }
                                },
                                valueRange = 0f..32f,
                                steps = 31
                            )

                            Spacer(modifier = modifier.height(8.dp))

                            Text(
                                text = "$selectedPrecision " + if (selectedPrecision == 1) "bit" else "bits",
                                modifier = modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        else {
                            Text(
                                text = "Load the LLM into memory to see more settings here.",
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                    /*
                    SteganographyMode.Bins -> {
                        Text(text = "Set the number of bins (higher is more efficient, but less coherent).")

                        Spacer(modifier = modifier.height(16.dp))

                        // Again, do int conversion here as slider only allows floats
                        Slider(
                            value = selectedBlockSize.toFloat(),
                            onValueChange = {
                                // Update state variable
                                selectedBlockSize = it.toInt()

                                // Update DataStore
                                Settings.blockSize = it.toInt()
                                coroutineScope.launch { HiPSDataStore.writeSettings() }
                            },
                            valueRange = 1f..4f,
                            steps = 2
                        )

                        Spacer(modifier = modifier.height(8.dp))

                        Text(
                            text = "2" + when (selectedBlockSize) {
                                1 -> "¹"
                                2 -> "²"
                                3 -> "³"
                                4 -> "⁴"
                                else -> throw IllegalStateException("Selected block size has to be between 1 and 4")
                            } + " bins",
                            modifier = modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    */
                    SteganographyMode.Huffman -> {
                        Text(text = "Set the number of bits to encode per cover text token. Higher is more efficient, but less coherent.")

                        Spacer(modifier = modifier.height(16.dp))

                        // Again, do int conversion here as slider only allows floats
                        Slider(
                            value = selectedBitsPerToken.toFloat(),
                            onValueChange = {
                                // Update state variable
                                selectedBitsPerToken = it.toInt()

                                // Update DataStore
                                Settings.bitsPerToken = it.toInt()
                                coroutineScope.launch { HiPSDataStore.writeSettings() }
                            },
                            valueRange = 1f..4f,
                            steps = 2
                        )

                        Spacer(modifier = modifier.height(8.dp))

                        Text(
                            text = "$selectedBitsPerToken " + if (selectedBitsPerToken == 1) "bit/token" else "bits/token",
                            modifier = modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        Spacer(modifier = modifier.height(16.dp))

        // Reset settings
        Row(
            modifier = modifier.fillMaxWidth(0.9f)
        ) {
            Icon(
                imageVector = Icons.Outlined.Restore,
                contentDescription = "Reset settings to defaults"
            )

            Spacer(modifier = modifier.width(16.dp))

            Column {
                Text(
                    text = "Reset settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(text = "Reset settings to their default values.")

                Spacer(modifier = modifier.height(16.dp))

                Text(text = "If the LLM is in memory, this finds reasonable values for the settings that are specific to it.")

                Spacer(modifier = modifier.height(16.dp))

                Row(
                    modifier = modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Reset mode
                    MultiChoiceSegmentedButtonRow(
                        // Ensure minimal distance to reset button in portrait orientation
                        modifier = modifier.width(180.dp)
                    ) {
                        // Mostly follows example given in docs
                        resetModes.forEachIndexed { index, _ ->
                            SegmentedButton(
                                checked = index in selectedResetModes,
                                onCheckedChange = {
                                    if (index in selectedResetModes) {
                                        selectedResetModes.remove(index)
                                    }
                                    else {
                                        selectedResetModes.add(index)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = resetModes.size,
                                    baseShape = RoundedCornerShape(4.dp)
                                ),
                                label = { Text(text = resetModes[index]) }
                            )
                        }
                    }

                    // Reset button
                    Button(
                        onClick = {
                            // Check if anything is selected
                            if (!(0 in selectedResetModes || 1 in selectedResetModes)) {
                                Toast.makeText(currentLocalContext, "There was nothing to reset", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            // Update DataStore
                            Settings.reset(general = 0 in selectedResetModes, llm = 1 in selectedResetModes)
                            coroutineScope.launch { HiPSDataStore.writeSettings() }

                            // Update state variables
                            selectedConversionMode = Settings.conversionMode
                            systemPrompt = Settings.systemPrompt
                            selectedNumberOfMessages = Settings.numberOfMessages
                            selectedSteganographyMode = Settings.steganographyMode
                            selectedTemperature = Settings.temperature
                            selectedPrecision = Settings.precision
                            selectedTopK = Settings.topK
                            selectedBlockSize = Settings.blockSize
                            selectedBitsPerToken = Settings.bitsPerToken
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = "Reset")
                    }
                }
            }
        }

        Spacer(modifier = modifier.height(16.dp))

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
                        val intent = Intent(Intent.ACTION_VIEW, "mailto:tobias@vonderheidt.org?subject=HiPS".toUri())
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
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/tobiasvonderheidt/hips".toUri())
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