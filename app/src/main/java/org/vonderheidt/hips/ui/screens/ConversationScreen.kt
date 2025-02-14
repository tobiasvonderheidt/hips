package org.vonderheidt.hips.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vonderheidt.hips.data.HiPSDatabase
import org.vonderheidt.hips.data.Message
import org.vonderheidt.hips.data.User
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme
import org.vonderheidt.hips.utils.LlamaCpp

/**
 * Function that defines the conversation screen.
 */
@Composable
fun ConversationScreen(navController: NavController, modifier: Modifier) {
    // State variables
    var messages by rememberSaveable { mutableStateOf(listOf<Message>()) }
    var selectedMessages by rememberSaveable { mutableStateOf(listOf<Message>()) }
    var newMessageContent by rememberSaveable { mutableStateOf("") }
    var isAlice by rememberSaveable { mutableStateOf(true) }
    var isPlainText by rememberSaveable { mutableStateOf(false) }
    var isEncoding by rememberSaveable { mutableStateOf(false) }

    // Database
    val db = HiPSDatabase.getInstance()

    // Coroutines
    val coroutineScope = rememberCoroutineScope()

    // Toasts
    val currentLocalContext = LocalContext.current

    // Vibration
    val hapticFeedback = LocalHapticFeedback.current

    // Query messages from database upon composition of this screen
    // Unit parameter allows query to be only run once
    LaunchedEffect(Unit) {
        messages = db.messageDao.getConversation(User.Alice.id, User.Bob.id)
    }

    // UI components
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
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
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Go back to home screen"
                )
            }
        }

        // Chat partner
        Row(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Profile picture",
                modifier = modifier.size(24.dp)
            )

            Spacer(modifier = modifier.width(8.dp))

            // Name
            Text(
                text = "Demo",
                fontSize = 24.sp,
            )

            // Profile picture and name on the left, buttons on the right
            Spacer(modifier = modifier.weight(1f))

            if (selectedMessages.isNotEmpty()) {
                // Decode button
                IconButton(
                    onClick = {
                        // Only 1 message can be decoded at a time
                        if (selectedMessages.size == 1) {
                            Toast.makeText(currentLocalContext, "Secret message encoded in ${selectedMessages[0].content}", Toast.LENGTH_LONG).show()
                        }
                        else {
                            Toast.makeText(currentLocalContext, "Only 1 message can be decoded at a time", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isEncoding
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "Decode selected message"
                    )
                }

                // Delete button
                IconButton(
                    onClick = {
                        // Only last messages of the conversation can be deleted, otherwise context would be corrupted
                        if (messages.takeLast(selectedMessages.size) == selectedMessages) {
                            // Update database
                            // Inverse encapsulation of loop vs coroutine causes messages to not be deleted
                            for (selectedMessage in selectedMessages) {
                                coroutineScope.launch { db.messageDao.deleteMessage(selectedMessage) }
                            }

                            // Update state variables
                            messages = messages.dropLast(selectedMessages.size)
                            selectedMessages = listOf()
                        }
                        else {
                            Toast.makeText(currentLocalContext, "Only messages at the end can be deleted", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isEncoding
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete selected messages"
                    )
                }
            }
        }

        Spacer(modifier = modifier.height(8.dp))

        // Messages
        // Use LazyColumn as it only loads visible messages into memory, allowing for arbitrary number of messages
        LazyColumn(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .weight(1f)
        ) {
            // Current user (senderID == 0) is right aligned and green
            // Chat partners (senderID != 0) are left aligned and red
            items(messages) { message ->
                Row(
                    modifier = modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.senderID == User.Alice.id) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxWidth(0.9f)
                            .background(
                                color = if (message.senderID == User.Alice.id) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .graphicsLayer(
                                alpha = if (selectedMessages.isEmpty()) { 1f }
                                        else {
                                            if (message in selectedMessages) { 1f }
                                            else { 0.25f }
                                        }
                            )
                            .padding(8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        // Vibrate
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                        // List of selected messages has to be sorted because list of messages is sorted, otherwise couldn't be compared to its end
                                        selectedMessages += message
                                        selectedMessages = selectedMessages.sortedBy { it.timestamp }
                                    },
                                    onTap = {
                                        selectedMessages -= message
                                    }
                                )
                            }
                    ) {
                        Text(
                            text = message.content,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = modifier.height(8.dp))
            }
        }

        Row(
            modifier = modifier.fillMaxWidth(0.95f),
            verticalAlignment = Alignment.Bottom
        ) {
            // Input field for new message
            OutlinedTextField(
                value = newMessageContent,
                onValueChange = { newMessageContent = it },
                modifier = modifier.weight(1f),
                enabled = !isEncoding,
                label = { Text(text = "New message") },
                trailingIcon = {
                    if (newMessageContent.isNotEmpty() && !isEncoding) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear new message",
                            modifier = modifier.clickable { newMessageContent = "" }
                        )
                    }
                },
                maxLines = 5
            )

            Spacer(modifier = modifier.width(8.dp))

            // Send button
            // Colour corresponds to user a new message is being sent as
            Box {
                IconButton(
                    onClick = { /* See onTap of Icon */ },
                    modifier = modifier
                        .background(
                            color = if (isAlice) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                            shape = CircleShape
                        ),
                    enabled = !isEncoding
                ) {
                    // Show loading animation while encoding
                    if (isEncoding) {
                        CircularProgressIndicator(
                            modifier = modifier.size(32.dp),
                            color = Color.White
                        )
                    }
                    else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Send message",
                            modifier = modifier.pointerInput(Unit) {
                                detectTapGestures (
                                    onLongPress = {
                                        // Vibrate
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                        // Toggle steganography on/off
                                        isPlainText = !isPlainText
                                    },
                                    onTap = {
                                        // Check if LLM is loaded
                                        if (!LlamaCpp.isInMemory()) {
                                            Toast.makeText(currentLocalContext, "Load LLM into memory first", Toast.LENGTH_LONG).show()
                                            return@detectTapGestures
                                        }
                                        // Only send non-blank messages, allows to switch user on button press
                                        if (newMessageContent.isBlank()) {
                                            // Clear input field and change mode
                                            newMessageContent = ""
                                            isAlice = !isAlice
                                            return@detectTapGestures
                                        }

                                        isEncoding = true

                                        // Create data objects for sender, receiver and message
                                        val newSender = if (isAlice) User.Alice else User.Bob
                                        val newReceiver = if (isAlice) User.Bob else User.Alice

                                        // Update database
                                        // Launch queries in coroutine so they can't block the UI in the main thread
                                        coroutineScope.launch {
                                            // Simulate encoding by waiting 5 seconds
                                            if (!isPlainText) {
                                                delay(5000)
                                            }

                                            val newMessage = Message(
                                                senderID = newSender.id,
                                                receiverID = newReceiver.id,
                                                timestamp = System.currentTimeMillis(),
                                                content = if (isPlainText) newMessageContent else "Cover text encoding $newMessageContent"
                                            )

                                            // Update state variable
                                            messages += newMessage

                                            // Order is important to avoid violating foreign key relations
                                            db.userDao.upsertUser(newSender)
                                            db.userDao.upsertUser(newReceiver)
                                            db.messageDao.upsertMessage(newMessage)

                                            // Clear input field and change mode
                                            newMessageContent = ""
                                            isAlice = !isAlice

                                            isEncoding = false
                                        }
                                    }
                                )
                            },
                            tint = Color.White
                        )
                    }
                }

                if (!isPlainText && !isEncoding) {
                    Badge(
                        modifier = modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(24.dp),
                        containerColor = Color.Transparent
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Send message",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = modifier.height(8.dp))
    }
}

/**
 * Function to show preview of the conversation screen in Android Studio.
 */
@Preview(showBackground = true)
@Composable
fun ConversationScreenPreview() {
    // No Scaffold, no innerPadding
    HiPSTheme {
        val modifier: Modifier = Modifier
        val navController: NavHostController = rememberNavController()

        ConversationScreen(navController, modifier)
    }
}