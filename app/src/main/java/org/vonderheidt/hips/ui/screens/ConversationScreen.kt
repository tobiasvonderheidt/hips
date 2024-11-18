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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.vonderheidt.hips.data.Message
import org.vonderheidt.hips.navigation.Screen
import org.vonderheidt.hips.ui.theme.HiPSTheme

/**
 * Function that defines the conversation screen.
 */
@Composable
fun ConversationScreen(navController: NavController, modifier: Modifier) {
    // State variables
    var messages by rememberSaveable { mutableStateOf(listOf<Message>()) }
    var newMessage by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf(true) }

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
                Row (
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
                value = newMessage,
                onValueChange = {newMessage = it},
                modifier = modifier.weight(1f),
                label = { Text(text = "New message") }
            )

            Spacer(modifier = modifier.width(8.dp))

            // Send button
            // Colour corresponds to user a new message is being sent as
            IconButton(
                onClick = {
                    // Only send non-empty messages
                    // Allows to switch user on button press
                    if (newMessage != "") {
                        messages += Message(
                            senderID = if (sender) 0 else 1,
                            receiverID = if (sender) 1 else 0,
                            timestamp = System.currentTimeMillis(),
                            content = newMessage
                        )
                    }

                    // Clear input field and change mode
                    newMessage = ""
                    sender = !sender
                },
                modifier = modifier
                    .background(
                        color = if (sender) Color(0xFF2E7D32) else Color(0xFFB71C1C),
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