package org.vonderheidt.hips.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.vonderheidt.hips.data.HiPSDatabase
import org.vonderheidt.hips.data.Message
import org.vonderheidt.hips.data.User
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme

/**
 * Function that defines the conversation screen.
 */
@Composable
fun ConversationScreen(navController: NavController, modifier: Modifier) {
    // Database
    val db = HiPSDatabase.getInstance()

    // Coroutines
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var messages by rememberSaveable { mutableStateOf(listOf<Message>()) }
    var newMessageContent by rememberSaveable { mutableStateOf("") }
    var isSender by rememberSaveable { mutableStateOf(true) }

    // Query messages from database upon composition of this screen
    // Unit parameter allows query to be only run once
    LaunchedEffect(Unit) {
        messages = db.messageDao.getConversation(0, 1)
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
            modifier = modifier.fillMaxWidth(0.95f),
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
                    horizontalArrangement = if (message.senderID == 0) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxWidth(0.9f)
                            .background(
                                color = if (message.senderID == 0) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
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
                label = { Text(text = "New message") }
            )

            Spacer(modifier = modifier.width(8.dp))

            // Send button
            // Colour corresponds to user a new message is being sent as
            IconButton(
                onClick = {
                    // Only send non-blank messages
                    // Allows to switch user on button press
                    if (newMessageContent.isNotBlank()) {
                        // Create data objects for sender, receiver and message
                        val newSender = if (isSender) User(0, "Alice") else User(1, "Bob")
                        val newReceiver = if (isSender) User(1, "Bob") else User(0, "Alice")

                        val newMessage = Message(
                            senderID = newSender.id,
                            receiverID = newReceiver.id,
                            timestamp = System.currentTimeMillis(),
                            content = newMessageContent
                        )

                        // Update state variable
                        messages += newMessage

                        // Update database
                        // Launch queries in coroutine so they can't block the UI in the main thread
                        coroutineScope.launch {
                            // Order is important to avoid violating foreign key relations
                            db.userDao.upsertUser(newSender)
                            db.userDao.upsertUser(newReceiver)
                            db.messageDao.upsertMessage(newMessage)
                        }
                    }

                    // Clear input field and change mode
                    newMessageContent = ""
                    isSender = !isSender
                },
                modifier = modifier
                    .background(
                        color = if (isSender) Color(0xFF2E7D32) else Color(0xFFB71C1C),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send message",
                    tint = Color.White
                )
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