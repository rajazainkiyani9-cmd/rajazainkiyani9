package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.AuraAvatar
import com.example.ui.components.AuraImage
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraLikeRed
import kotlinx.coroutines.delay

@Composable
fun StoryViewerOverlay(
  stories: List<Story>,
  onClose: () -> Unit,
  onReplyToStory: (Story, String) -> Unit,
  onOpenAuthorProfile: (String) -> Unit,
) {
  if (stories.isEmpty()) {
    onClose()
    return
  }

  BackHandler { onClose() }

  var currentIndex by remember { mutableIntStateOf(0) }
  var progress by remember { mutableFloatStateOf(0f) }
  var isPaused by remember { mutableStateOf(false) }
  var replyText by remember { mutableStateOf("") }
  var sentReaction by remember { mutableStateOf(false) }

  val currentStory = stories[currentIndex.coerceIn(0, stories.lastIndex)]

  LaunchedEffect(currentIndex, isPaused) {
    if (isPaused) return@LaunchedEffect
    progress = 0f
    val stepMs = 50L
    val totalSteps = 100
    repeat(totalSteps) {
      delay(stepMs)
      if (!isPaused) {
        progress = (it + 1) / totalSteps.toFloat()
      }
    }
    if (currentIndex < stories.lastIndex) {
      currentIndex++
    } else {
      onClose()
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
    AuraImage(
      imageData = currentStory.imageData,
      contentDescription = currentStory.captionText,
      modifier =
        Modifier.fillMaxSize().pointerInput(currentIndex) {
          detectTapGestures(
            onPress = {
              isPaused = true
              tryAwaitRelease()
              isPaused = false
            },
            onTap = { offset ->
              if (offset.x < size.width / 3f) {
                if (currentIndex > 0) currentIndex--
              } else {
                if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
              }
            },
          )
        },
      contentScale = ContentScale.Crop,
      targetSizePx = 1440,
    )

    // Top scrim
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(120.dp)
          .align(Alignment.TopCenter)
          .background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))
          )
    )

    Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp)) {
      // Segmented Progress Bars
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        stories.forEachIndexed { idx, _ ->
          val barProgress =
            when {
              idx < currentIndex -> 1f
              idx == currentIndex -> progress
              else -> 0f
            }
          LinearProgressIndicator(
            progress = { barProgress },
            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.35f),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable {
            onClose()
            onOpenAuthorProfile(currentStory.userId)
          },
        ) {
          AuraAvatar(
            avatarUrl = currentStory.authorAvatarUrl.ifBlank { currentStory.imageData },
            username = currentStory.authorUsername,
            size = 36.dp,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = currentStory.authorUsername,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = formatRelativeTime(currentStory.createdAt),
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
          )
        }

        IconButton(onClick = onClose) {
          Icon(Icons.Filled.Close, contentDescription = "Close story", tint = Color.White)
        }
      }
    }

    if (currentStory.captionText.isNotBlank()) {
      Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp, start = 20.dp, end = 20.dp),
      ) {
        Text(
          text = currentStory.captionText,
          color = Color.White,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
      }
    }

    // Bottom Reply Bar
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(Color.Black.copy(alpha = 0.45f))
          .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = replyText,
        onValueChange = {
          isPaused = it.isNotEmpty()
          replyText = it
        },
        placeholder = { Text("Send message...", color = Color.White.copy(alpha = 0.7f)) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(8.dp))
      IconButton(
        onClick = {
          sentReaction = true
          onReplyToStory(currentStory, "❤️")
        }
      ) {
        Icon(
          Icons.Filled.Favorite,
          contentDescription = "Quick heart",
          tint = if (sentReaction) AuraLikeRed else Color.White,
        )
      }
      if (replyText.isNotBlank()) {
        IconButton(
          onClick = {
            onReplyToStory(currentStory, replyText)
            replyText = ""
            isPaused = false
          }
        ) {
          Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send reply",
            tint = AuraBlue,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
  post: Post,
  commentsState: UiState<List<Comment>>,
  blockedUserIds: Set<String> = emptySet(),
  hiddenWords: List<String> = emptyList(),
  onDismiss: () -> Unit,
  onSubmitComment: (String) -> Unit,
  onOpenProfile: (String) -> Unit,
) {
  var commentText by remember { mutableStateOf("") }
  val quickEmojis = remember { listOf("❤️", "🙌", "🔥", "👏", "😍", "✨") }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 560.dp)) {
      Text(
        text = "Comments",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
      )
      HorizontalDivider()

      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (commentsState) {
          is UiState.Loading -> {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
          }
          is UiState.Error -> {
            Text(
              text = commentsState.message,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
          }
          is UiState.Success -> {
            val filteredComments =
              remember(commentsState.data, blockedUserIds, hiddenWords) {
                commentsState.data.filter { c ->
                  !blockedUserIds.contains(c.userId) &&
                    (hiddenWords.isEmpty() || hiddenWords.none { w -> c.text.contains(w, ignoreCase = true) })
                }
              }
            if (filteredComments.isEmpty()) {
              Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text("No comments yet", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "Start the conversation!",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                items(filteredComments, key = { it.id }) { comment ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                  ) {
                    AuraAvatar(
                      avatarUrl = comment.authorAvatarUrl,
                      username = comment.authorUsername,
                      size = 34.dp,
                      onClick = {
                        onDismiss()
                        onOpenProfile(comment.userId)
                      },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = comment.authorUsername,
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.clickable {
                            onDismiss()
                            onOpenProfile(comment.userId)
                          },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = formatRelativeTime(comment.createdAt),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
                    }
                  }
                }
              }
            }
          }
        }
      }

      HorizontalDivider()
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
      ) {
        quickEmojis.forEach { emoji ->
          Text(
            text = emoji,
            fontSize = 22.sp,
            modifier =
              Modifier.clip(CircleShape)
                .clickable { commentText += emoji }
                .padding(4.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = commentText,
          onValueChange = { commentText = it },
          placeholder = { Text("Add a comment for ${post.authorUsername}...") },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.weight(1f).testTag("comment_input"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = {
            if (commentText.isNotBlank()) {
              onSubmitComment(commentText)
              commentText = ""
            }
          },
          enabled = commentText.isNotBlank(),
          modifier = Modifier.testTag("comment_submit_button"),
        ) {
          Text("Post")
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
    }
  }
}

@Composable
fun PostDetailDialog(
  post: Post,
  isLiked: Boolean,
  isSaved: Boolean,
  isOwnPost: Boolean,
  hideLikeCounts: Boolean = false,
  onDismiss: () -> Unit,
  onAuthorClick: () -> Unit,
  onToggleLike: (Boolean) -> Unit,
  onToggleSave: () -> Unit,
  onOpenComments: () -> Unit,
  onShare: () -> Unit,
  onDelete: () -> Unit,
  onToggleArchive: () -> Unit = {},
  onOpenReel: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      shape = RoundedCornerShape(18.dp),
      color = MaterialTheme.colorScheme.background,
    ) {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Post", style = MaterialTheme.typography.titleMedium)
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }
        FeedPostCard(
          post = post,
          isLiked = isLiked,
          isSaved = isSaved,
          isOwnPost = isOwnPost,
          hideLikeCounts = hideLikeCounts,
          hasActiveStory = false,
          isStorySeen = false,
          onAuthorClick = {
            onDismiss()
            onAuthorClick()
          },
          onDoubleTapLike = { onToggleLike(true) },
          onToggleLike = { onToggleLike(false) },
          onToggleSave = onToggleSave,
          onCommentClick = {
            onDismiss()
            onOpenComments()
          },
          onShareClick = {
            onDismiss()
            onShare()
          },
          onDeleteClick = {
            onDismiss()
            onDelete()
          },
          onToggleArchiveClick = {
            onDismiss()
            onToggleArchive()
          },
          onOpenReel = {
            onDismiss()
            onOpenReel()
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePostSheet(
  post: Post,
  profiles: List<UserProfile>,
  currentUserId: String,
  onDismiss: () -> Unit,
  onSendToUser: (UserProfile, Post) -> Unit,
) {
  val context = LocalContext.current
  val recipients = remember(profiles, currentUserId) {
    profiles.filter { it.userId != currentUserId }
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
      Text(text = "Share to", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(12.dp))

      if (recipients.isEmpty()) {
        Text(
          text = "No other Aura users found yet — share externally using the button below.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 12.dp),
        )
      } else {
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
          items(recipients, key = { it.userId }) { recipient ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    onSendToUser(recipient, post)
                    onDismiss()
                  }
                  .padding(vertical = 8.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                AuraAvatar(
                  avatarUrl = recipient.avatarUrl,
                  username = recipient.username,
                  size = 44.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(recipient.username, style = MaterialTheme.typography.titleSmall)
                  Text(
                    recipient.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Button(
                onClick = {
                  onSendToUser(recipient, post)
                  onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp),
              ) {
                Text("Send")
              }
            }
          }
        }
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

      OutlinedButton(
        onClick = {
          onDismiss()
          val intent =
            Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(
                Intent.EXTRA_TEXT,
                "Check out @${post.authorUsername}'s post on Aura: ${post.caption}",
              )
            }
          context.startActivity(Intent.createChooser(intent, "Share via"))
        },
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Filled.Share, contentDescription = null, tint = AuraCoral)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Share via other apps")
      }
      Spacer(modifier = Modifier.height(22.dp))
    }
  }
}
