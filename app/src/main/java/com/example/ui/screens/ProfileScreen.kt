package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.components.AuraImage
import com.example.ui.components.compressUriToBase64DataUri
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraVerifiedBlue
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  profileUserId: String,
  currentUserId: String,
  state: AuraHomeUiState,
  isDarkTheme: Boolean,
  onBack: (() -> Unit)? = null,
  onOpenSettings: () -> Unit = {},
  onToggleFollow: (UserProfile) -> Unit,
  onMessageUser: (UserProfile) -> Unit,
  onUpdateProfile: (String, String, String, String, String) -> Unit,
  onToggleDarkMode: (Boolean) -> Unit,
  onToggleUserSetting: (fieldKey: String, currentList: List<String>, targetId: String, addedMsg: String, removedMsg: String) -> Unit = { _, _, _, _, _ -> },
  onOpenPostDetail: (Post) -> Unit,
  onOpenStory: (String) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val isOwnProfile = profileUserId == currentUserId
  val profile =
    if (isOwnProfile) {
      state.myProfile
        ?: UserProfile(
          userId = currentUserId,
          username = "aura_creator",
          displayName = "Aura Creator",
        )
    } else {
      state.profilesById[profileUserId]
    }

  if (profile == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Profile not found")
    }
    return
  }

  val userPosts = remember(state.feedPosts, profileUserId) {
    state.feedPosts.filter { it.userId == profileUserId }
  }
  val userReels = remember(userPosts) { userPosts.filter { it.mediaType == "reel" } }
  val isFollowing = state.followingIds.contains(profileUserId)

  var selectedTab by remember { mutableIntStateOf(0) }
  var showEditDialog by remember { mutableStateOf(false) }
  var otherMenuExpanded by remember { mutableStateOf(false) }
  val prefs = state.userPrivate

  val tabs = if (isOwnProfile) listOf("Posts", "Reels", "Saved") else listOf("Posts", "Reels")

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Top Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
        if (!profile.isPublic) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Private account",
            modifier = Modifier.size(18.dp).padding(end = 4.dp),
          )
        }
        Text(
          text = profile.username.ifBlank { "aura_user" },
          style = MaterialTheme.typography.headlineSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (profile.isVerified) {
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = "Verified",
            tint = AuraVerifiedBlue,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (isOwnProfile) {
        IconButton(
          onClick = onOpenSettings,
          modifier = Modifier.testTag("profile_menu_button"),
        ) {
          Icon(Icons.Filled.Menu, contentDescription = "Settings and activity")
        }
      } else if (prefs != null) {
        Box {
          IconButton(onClick = { otherMenuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Account options")
          }
          DropdownMenu(
            expanded = otherMenuExpanded,
            onDismissRequest = { otherMenuExpanded = false },
          ) {
            val isFav = prefs.favoriteUserIds.contains(profileUserId)
            val isMuted = prefs.mutedUserIds.contains(profileUserId)
            val isRestricted = prefs.restrictedUserIds.contains(profileUserId)
            val isBlocked = prefs.blockedUserIds.contains(profileUserId)
            DropdownMenuItem(
              text = { Text(if (isFav) "Remove from Favorites" else "Add to Favorites") },
              onClick = {
                otherMenuExpanded = false
                onToggleUserSetting("favoriteUserIds", prefs.favoriteUserIds, profileUserId, "Added to Favorites", "Removed from Favorites")
              },
            )
            DropdownMenuItem(
              text = { Text(if (isMuted) "Unmute @${profile.username}" else "Mute @${profile.username}") },
              onClick = {
                otherMenuExpanded = false
                onToggleUserSetting("mutedUserIds", prefs.mutedUserIds, profileUserId, "Muted @${profile.username}", "Unmuted @${profile.username}")
              },
            )
            DropdownMenuItem(
              text = { Text(if (isRestricted) "Unrestrict" else "Restrict") },
              onClick = {
                otherMenuExpanded = false
                onToggleUserSetting("restrictedUserIds", prefs.restrictedUserIds, profileUserId, "Account restricted", "Restriction removed")
              },
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = if (isBlocked) "Unblock" else "Block @${profile.username}",
                  color = MaterialTheme.colorScheme.error,
                )
              },
              onClick = {
                otherMenuExpanded = false
                onToggleUserSetting("blockedUserIds", prefs.blockedUserIds, profileUserId, "Blocked @${profile.username}", "Unblocked @${profile.username}")
              },
            )
          }
        }
      }
    }

    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      item(span = { GridItemSpan(3) }, key = "profile_header") {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            val hasStory = state.storiesByUser.containsKey(profile.userId)
            AuraAvatar(
              avatarUrl = profile.avatarUrl,
              username = profile.username,
              size = 84.dp,
              hasStory = hasStory,
              isStorySeen = state.seenStoryUserIds.contains(profile.userId),
              onClick = if (hasStory) { { onOpenStory(profile.userId) } } else null,
            )

            Row(
              modifier = Modifier.weight(1f).padding(start = 20.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              ProfileStat(count = userPosts.size.toLong(), label = "Posts")
              ProfileStat(count = profile.followersCount, label = "Followers")
              ProfileStat(count = profile.followingCount, label = "Following")
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = profile.displayName.ifBlank { profile.username },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          if (profile.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = profile.bio, style = MaterialTheme.typography.bodyMedium)
          }
          if (profile.website.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = profile.website,
              style = MaterialTheme.typography.bodySmall,
              color = AuraBlue,
              fontWeight = FontWeight.Medium,
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Action Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (isOwnProfile) {
              OutlinedButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.weight(1f).height(38.dp).testTag("edit_profile_button"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
              ) {
                Text("Edit profile", style = MaterialTheme.typography.labelMedium)
              }
              OutlinedButton(
                onClick = {
                  val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(
                        Intent.EXTRA_TEXT,
                        "Follow @${profile.username} on Aura!",
                      )
                    }
                  context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
                },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
              ) {
                Text("Share profile", style = MaterialTheme.typography.labelMedium)
              }
            } else {
              Button(
                onClick = { onToggleFollow(profile) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
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
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              OutlinedButton(
                onClick = { onMessageUser(profile) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
              ) {
                Text("Message", style = MaterialTheme.typography.labelMedium)
              }
            }
          }

          // Real active stories highlight if the user has posted stories
          val userStories = state.storiesByUser[profile.userId].orEmpty()
          if (userStories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
              items(userStories, key = { it.id }) { story ->
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.clickable { onOpenStory(profile.userId) },
                ) {
                  Box(
                    modifier =
                      Modifier.size(62.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                  ) {
                    AuraImage(
                      imageData = story.imageData,
                      contentDescription = story.captionText.ifBlank { "Story" },
                      modifier = Modifier.fillMaxSize(),
                      targetSizePx = 256,
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = story.captionText.ifBlank { "Story" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }
      }

      item(span = { GridItemSpan(3) }, key = "profile_tabs") {
        TabRow(selectedTabIndex = selectedTab) {
          tabs.forEachIndexed { idx, title ->
            Tab(
              selected = selectedTab == idx,
              onClick = { selectedTab = idx },
              icon = {
                Icon(
                  imageVector =
                    when (title) {
                      "Posts" -> Icons.Filled.GridOn
                      "Reels" -> Icons.Filled.VideoLibrary
                      else -> Icons.Filled.BookmarkBorder
                    },
                  contentDescription = title,
                )
              },
            )
          }
        }
      }

      when (selectedTab) {
        0 -> {
          if (userPosts.isEmpty()) {
            item(span = { GridItemSpan(3) }, key = "empty_posts") {
              EmptyGridState("No posts yet", "Share your first photo from the + Create tab.")
            }
          } else {
            items(userPosts, key = { "p_${it.id}" }, contentType = { "grid_tile" }) { post ->
              ProfileGridTile(
                imageData = post.imageData,
                filterName = post.filterName,
                isReel = post.mediaType == "reel",
                onClick = { onOpenPostDetail(post) },
              )
            }
          }
        }
        1 -> {
          if (userReels.isEmpty()) {
            item(span = { GridItemSpan(3) }, key = "empty_reels") {
              EmptyGridState("No reels yet", "Publish a Reel to see it featured here.")
            }
          } else {
            items(userReels, key = { "r_${it.id}" }, contentType = { "grid_tile" }) { reel ->
              ProfileGridTile(
                imageData = reel.imageData,
                filterName = reel.filterName,
                isReel = true,
                onClick = { onOpenPostDetail(reel) },
              )
            }
          }
        }
        2 -> {
          if (state.savedPosts.isEmpty()) {
            item(span = { GridItemSpan(3) }, key = "empty_saved") {
              EmptyGridState("No saved posts", "Tap the bookmark icon on any post to save it here.")
            }
          } else {
            items(state.savedPosts, key = { "s_${it.postId}" }, contentType = { "grid_tile" }) { saved ->
              val matchingPost =
                state.feedPosts.firstOrNull { it.id == saved.postId }
                  ?: Post(
                    id = saved.postId,
                    userId = saved.userId,
                    authorUsername = saved.authorUsername,
                    caption = saved.caption,
                    mediaType = saved.mediaType,
                    imageData = saved.imageData,
                    createdAt = saved.createdAt,
                  )
              ProfileGridTile(
                imageData = saved.imageData,
                filterName = matchingPost.filterName,
                isReel = saved.mediaType == "reel",
                onClick = { onOpenPostDetail(matchingPost) },
              )
            }
          }
        }
      }
    }
  }

  if (showEditDialog) {
    EditProfileDialog(
      profile = profile,
      onDismiss = { showEditDialog = false },
      onSave = { username, displayName, bio, website, avatar ->
        onUpdateProfile(username, displayName, bio, website, avatar)
        showEditDialog = false
      },
    )
  }
}

@Composable
private fun ProfileStat(count: Long, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = formatCompactCount(count),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ProfileGridTile(
  imageData: String,
  filterName: String,
  isReel: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.aspectRatio(1f)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick)
  ) {
    AuraImage(
      imageData = imageData,
      contentDescription = "Post thumbnail",
      filterName = filterName,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
      targetSizePx = 540,
    )
    if (isReel) {
      Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = "Reel",
        tint = Color.White,
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
      )
    }
  }
}

@Composable
private fun EmptyGridState(title: String, subtitle: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(36.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EditProfileDialog(
  profile: UserProfile,
  onDismiss: () -> Unit,
  onSave: (String, String, String, String, String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var username by remember { mutableStateOf(profile.username) }
  var displayName by remember { mutableStateOf(profile.displayName) }
  var bio by remember { mutableStateOf(profile.bio) }
  var website by remember { mutableStateOf(profile.website) }
  var avatarUrl by remember { mutableStateOf(profile.avatarUrl) }

  val avatarPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val encoded = compressUriToBase64DataUri(context, uri)
          if (encoded != null) avatarUrl = encoded
        }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Profile") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          AuraAvatar(avatarUrl = avatarUrl, username = username, size = 56.dp)
          Spacer(modifier = Modifier.width(12.dp))
          TextButton(
            onClick = {
              avatarPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            }
          ) {
            Text("Change photo")
          }
        }
        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Name") },
          singleLine = true,
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it.lowercase().replace(" ", "_") },
          label = { Text("Username") },
          singleLine = true,
        )
        OutlinedTextField(
          value = bio,
          onValueChange = { bio = it },
          label = { Text("Bio") },
          maxLines = 3,
        )
        OutlinedTextField(
          value = website,
          onValueChange = { website = it },
          label = { Text("Website") },
          singleLine = true,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cleanUser = username.trim().let { if (it.length < 2) "aura_user" else it }
          val cleanDisplay = displayName.trim().ifBlank { cleanUser }
          onSave(cleanUser, cleanDisplay, bio, website, avatarUrl)
        }
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
