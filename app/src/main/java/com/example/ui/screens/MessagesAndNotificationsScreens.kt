package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.model.DirectMessage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraButtonGradient
import com.example.ui.theme.AuraLikeRed

@Composable
fun NotificationsScreen(
  state: AuraHomeUiState,
  onBack: () -> Unit,
  onOpenProfile: (String) -> Unit,
  onToggleFollow: (UserProfile) -> Unit,
  onMarkRead: (String) -> Unit,
) {
  val prefs = state.userPrivate
  val visibleNotifs =
    remember(state.notifications, prefs) {
      if (prefs == null) state.notifications
      else
        state.notifications.filter { notif ->
          when (notif.type) {
            "like" -> prefs.notifyLikes
            "comment" -> prefs.notifyComments
            "follow" -> prefs.notifyFollows
            else -> true
          }
        }
    }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Spacer(modifier = Modifier.width(6.dp))
      Text("Notifications", style = MaterialTheme.typography.headlineSmall)
    }
    HorizontalDivider()

    if (prefs?.notificationsMuted == true) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(10.dp),
      ) {
        Text(
          text = "All notifications are currently paused in Settings.",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    if (visibleNotifs.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "No activity yet. Likes, comments, and new followers appear here.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(24.dp),
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visibleNotifs, key = { it.id }, contentType = { "notif" }) { notif ->
          val actorProfile = state.profilesById[notif.actorId]
          val isFollowing = state.followingIds.contains(notif.actorId)

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(
                  if (!notif.isRead) AuraBlue.copy(alpha = 0.06f) else Color.Transparent
                )
                .clickable {
                  onMarkRead(notif.id)
                  onOpenProfile(notif.actorId)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              AuraAvatar(
                avatarUrl = notif.actorAvatarUrl,
                username = notif.actorUsername,
                size = 44.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text =
                    buildAnnotatedString {
                      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("${notif.actorUsername} ")
                      }
                      append(notif.previewText)
                    },
                  style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = formatRelativeTime(notif.createdAt),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (notif.type == "follow" && actorProfile != null) {
              Spacer(modifier = Modifier.width(10.dp))
              Button(
                onClick = { onToggleFollow(actorProfile) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor =
                      if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else AuraBlue,
                    contentColor =
                      if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.White,
                  ),
              ) {
                Text(
                  text = if (isFollowing) "Following" else "Follow",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun InboxScreen(
  state: AuraHomeUiState,
  currentUserId: String,
  onBack: () -> Unit,
  onOpenChat: (UserProfile) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var showNewChatPicker by remember { mutableStateOf(false) }

  val otherProfiles =
    remember(state.allProfiles, currentUserId) {
      state.allProfiles.filter { it.userId != currentUserId }
    }

  val filteredConversations =
    remember(state.conversations, searchQuery, currentUserId) {
      val q = searchQuery.trim().lowercase()
      state.conversations.filter { conv ->
        val otherName = if (conv.userAId == currentUserId) conv.userBName else conv.userAName
        q.isEmpty() || otherName.lowercase().contains(q) || conv.lastMessage.lowercase().contains(q)
      }
    }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = state.myProfile?.username ?: "Messages",
          style = MaterialTheme.typography.headlineSmall,
        )
      }
      IconButton(
        onClick = { showNewChatPicker = true },
        modifier = Modifier.testTag("new_chat_button"),
      ) {
        Icon(Icons.Filled.AddComment, contentDescription = "New message")
      }
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search messages...") },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          focusedBorderColor = Color.Transparent,
          unfocusedBorderColor = Color.Transparent,
        ),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
    )

    // Quick Active Creators Row
    if (otherProfiles.isNotEmpty()) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(otherProfiles, key = { it.userId }) { user ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(68.dp).clickable { onOpenChat(user) },
          ) {
            AuraAvatar(
              avatarUrl = user.avatarUrl,
              username = user.username,
              size = 58.dp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = user.username,
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    Text(
      text = "Messages",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )

    if (filteredConversations.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "No messages yet. Tap the compose icon to start a real chat!",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(24.dp),
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredConversations, key = { it.id }, contentType = { "conv" }) { conv ->
          val isMeA = conv.userAId == currentUserId
          val otherId = if (isMeA) conv.userBId else conv.userAId
          val otherName = if (isMeA) conv.userBName else conv.userAName
          val otherAvatar = if (isMeA) conv.userBAvatar else conv.userAAvatar
          val targetProfile =
            state.profilesById[otherId]
              ?: UserProfile(userId = otherId, username = otherName, avatarUrl = otherAvatar)

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable { onOpenChat(targetProfile) }
              .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AuraAvatar(avatarUrl = otherAvatar, username = otherName, size = 54.dp)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = otherName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = conv.lastMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = formatRelativeTime(conv.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      }
    }
  }

  if (showNewChatPicker) {
    AlertDialog(
      onDismissRequest = { showNewChatPicker = false },
      title = { Text("New Message") },
      text = {
        LazyColumn(modifier = Modifier.height(280.dp)) {
          items(otherProfiles, key = { it.userId }) { user ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable {
                    showNewChatPicker = false
                    onOpenChat(user)
                  }
                  .padding(vertical = 8.dp, horizontal = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AuraAvatar(avatarUrl = user.avatarUrl, username = user.username, size = 40.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(user.username, style = MaterialTheme.typography.titleSmall)
                Text(
                  user.displayName,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNewChatPicker = false }) { Text("Close") }
      },
    )
  }
}

@Composable
fun ChatScreen(
  recipient: UserProfile,
  currentUserId: String,
  messagesState: UiState<List<DirectMessage>>,
  showActivityStatus: Boolean = true,
  onBack: () -> Unit,
  onOpenProfile: (String) -> Unit,
  onSendMessage: (String) -> Unit,
) {
  var messageText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  val messages = (messagesState as? UiState.Success)?.data.orEmpty()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Chat Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onOpenProfile(recipient.userId) },
      ) {
        AuraAvatar(avatarUrl = recipient.avatarUrl, username = recipient.username, size = 38.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(recipient.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(
            text = if (showActivityStatus) "${recipient.displayName} · Active" else recipient.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    HorizontalDivider()

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (messagesState) {
        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        is UiState.Error ->
          Text(
            text = messagesState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.Center).padding(20.dp),
          )
        is UiState.Success -> {
          if (messages.isEmpty()) {
            Column(
              modifier = Modifier.align(Alignment.Center).padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              AuraAvatar(
                avatarUrl = recipient.avatarUrl,
                username = recipient.username,
                size = 72.dp,
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(recipient.displayName, style = MaterialTheme.typography.titleMedium)
              Text(
                "@${recipient.username} · Aura",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                "Say hello to start a conversation!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUserId
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                ) {
                  Box(
                    modifier =
                      Modifier.widthIn(max = 280.dp)
                        .clip(
                          RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 18.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 18.dp,
                          )
                        )
                        .then(
                          if (isMine) Modifier.background(AuraButtonGradient)
                          else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                  ) {
                    Text(
                      text = msg.text,
                      color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                      style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Input Composer
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = messageText,
        onValueChange = { messageText = it },
        placeholder = { Text("Message...") },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f).testTag("chat_message_input"),
      )
      Spacer(modifier = Modifier.width(6.dp))
      if (messageText.isBlank()) {
        IconButton(onClick = { onSendMessage("❤️") }) {
          Icon(Icons.Filled.Favorite, contentDescription = "Send heart", tint = AuraLikeRed)
        }
      } else {
        IconButton(
          onClick = {
            onSendMessage(messageText)
            messageText = ""
          },
          modifier = Modifier.testTag("chat_send_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = AuraBlue)
        }
      }
    }
  }
}
