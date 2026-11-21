package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.components.AuraImage
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraLikeRed
import com.example.ui.theme.AuraVerifiedBlue
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay

import androidx.compose.material.icons.filled.Star
import com.example.ui.theme.AuraCoral

@Composable
fun HomeFeedScreen(
  state: AuraHomeUiState,
  currentUserId: String,
  onOpenNotifications: () -> Unit,
  onOpenMessages: () -> Unit,
  onOpenStory: (String) -> Unit,
  onAddStoryClick: () -> Unit,
  onCreatePostClick: () -> Unit,
  onOpenProfile: (String) -> Unit,
  onToggleLike: (Post, Boolean) -> Unit,
  onToggleSave: (Post) -> Unit,
  onOpenComments: (Post) -> Unit,
  onSharePost: (Post) -> Unit,
  onDeletePost: (Post) -> Unit,
  onToggleArchivePost: (Post) -> Unit = {},
  onMuteUser: (String) -> Unit = {},
  onBlockUser: (String) -> Unit = {},
  onOpenReel: (Post) -> Unit,
) {
  var feedFilter by remember { mutableStateOf("All") }
  var filterMenuExpanded by remember { mutableStateOf(false) }

  val prefs = state.userPrivate
  val mutedSet = remember(prefs?.mutedUserIds) { prefs?.mutedUserIds?.toSet().orEmpty() }
  val favoriteSet = remember(prefs?.favoriteUserIds) { prefs?.favoriteUserIds?.toSet().orEmpty() }
  val hiddenWords = remember(prefs?.hiddenWords, prefs?.hideOffensiveComments) {
    if (prefs?.hideOffensiveComments == false) emptyList() else prefs?.hiddenWords.orEmpty()
  }
  val hideLikeCounts = prefs?.hideLikeCounts == true

  val filteredPosts =
    remember(state.feedPosts, feedFilter, state.followingIds, mutedSet, favoriteSet, hiddenWords, currentUserId) {
      state.feedPosts
        .filter { post ->
          (post.userId == currentUserId || !mutedSet.contains(post.userId)) &&
            (hiddenWords.isEmpty() || hiddenWords.none { post.caption.contains(it, ignoreCase = true) })
        }
        .filter { post ->
          when (feedFilter) {
            "Following" -> state.followingIds.contains(post.userId) || post.userId == currentUserId
            "Favorites" -> favoriteSet.contains(post.userId)
            else -> true
          }
        }
    }

  val visibleStoriesByUser =
    remember(state.storiesByUser, mutedSet, currentUserId) {
      state.storiesByUser.filterKeys { uid -> uid == currentUserId || !mutedSet.contains(uid) }
    }

  val unreadNotifs = state.notifications.count { !it.isRead }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Top App Bar
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Box {
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .clickable { filterMenuExpanded = true }
              .padding(vertical = 4.dp, horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(id = R.drawable.aura_icon_foreground),
            contentDescription = "Aura logo",
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Crop,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Aura",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "Feed filter",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
          )
        }
        DropdownMenu(
          expanded = filterMenuExpanded,
          onDismissRequest = { filterMenuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text("For You (All)") },
            onClick = {
              feedFilter = "All"
              filterMenuExpanded = false
            },
          )
          DropdownMenuItem(
            text = { Text("Following") },
            onClick = {
              feedFilter = "Following"
              filterMenuExpanded = false
            },
          )
          DropdownMenuItem(
            text = { Text("Favorites ⭐") },
            onClick = {
              feedFilter = "Favorites"
              filterMenuExpanded = false
            },
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
          IconButton(
            onClick = onOpenNotifications,
            modifier = Modifier.testTag("notifications_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.FavoriteBorder,
              contentDescription = "Activity notifications",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
          if (unreadNotifs > 0) {
            Box(
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .padding(top = 10.dp, end = 10.dp)
                  .size(9.dp)
                  .clip(CircleShape)
                  .background(AuraLikeRed)
            )
          }
        }

        Box {
          IconButton(
            onClick = onOpenMessages,
            modifier = Modifier.testTag("messages_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Send,
              contentDescription = "Direct messages",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
          if (state.conversations.isNotEmpty()) {
            Surface(
              color = AuraLikeRed,
              shape = CircleShape,
              modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
            ) {
              Text(
                text = state.conversations.size.coerceAtMost(9).toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
              )
            }
          }
        }
      }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
      // Stories Tray
      item(key = "stories_tray") {
        StoriesTray(
          myAvatarUrl = state.myProfile?.avatarUrl ?: "",
          myUsername = state.myProfile?.username ?: "you",
          currentUserId = currentUserId,
          storiesByUser = visibleStoriesByUser,
          seenStoryUserIds = state.seenStoryUserIds,
          onOpenStory = onOpenStory,
          onAddStoryClick = onAddStoryClick,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
      }

      if (feedFilter != "All") {
        item(key = "filter_banner") {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Showing $feedFilter feed",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
              selected = true,
              onClick = { feedFilter = "All" },
              label = { Text("Show All") },
            )
          }
        }
      }

      if (state.isLoading && filteredPosts.isEmpty()) {
        item(key = "feed_loading", contentType = "loading") {
          Box(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      } else if (filteredPosts.isEmpty()) {
        item(key = "empty_feed", contentType = "empty") {
          Column(
            modifier = Modifier.fillMaxWidth().padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text =
                if (feedFilter == "Following") "No posts in Following yet"
                else "Your Aura feed is live!",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text =
                if (feedFilter == "Following")
                  "Follow creators in Explore or switch to For You to discover posts."
                else "Share your first photo or reel to start building the community feed.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            if (feedFilter != "Following") {
              Spacer(modifier = Modifier.height(16.dp))
              Button(onClick = onCreatePostClick) {
                Text("Create First Post")
              }
            }
          }
        }
      }

      items(filteredPosts, key = { it.id }, contentType = { "post_card" }) { post ->
        FeedPostCard(
          post = post,
          isLiked = state.likedPostIds.contains(post.id),
          isSaved = state.savedPostIds.contains(post.id),
          isOwnPost = post.userId == currentUserId,
          isFavoriteAuthor = favoriteSet.contains(post.userId),
          hideLikeCounts = hideLikeCounts,
          hasActiveStory = visibleStoriesByUser.containsKey(post.userId),
          isStorySeen = state.seenStoryUserIds.contains(post.userId),
          onAuthorClick = { onOpenProfile(post.userId) },
          onDoubleTapLike = { onToggleLike(post, true) },
          onToggleLike = { onToggleLike(post, false) },
          onToggleSave = { onToggleSave(post) },
          onCommentClick = { onOpenComments(post) },
          onShareClick = { onSharePost(post) },
          onDeleteClick = { onDeletePost(post) },
          onToggleArchiveClick = { onToggleArchivePost(post) },
          onMuteAuthorClick = { onMuteUser(post.userId) },
          onBlockAuthorClick = { onBlockUser(post.userId) },
          onOpenReel = { onOpenReel(post) },
        )
      }

      item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun StoriesTray(
  myAvatarUrl: String,
  myUsername: String,
  currentUserId: String,
  storiesByUser: Map<String, List<Story>>,
  seenStoryUserIds: Set<String>,
  onOpenStory: (String) -> Unit,
  onAddStoryClick: () -> Unit,
) {
  val myHasStory = storiesByUser.containsKey(currentUserId)
  val otherStoryOwners =
    remember(storiesByUser, currentUserId) {
      storiesByUser.keys.filter { it != currentUserId }.toList()
    }

  LazyRow(
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Your Story item
    item(key = "my_story") {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.width(72.dp).clickable {
            if (myHasStory) onOpenStory(currentUserId) else onAddStoryClick()
          },
      ) {
        Box(modifier = Modifier.size(68.dp)) {
          AuraAvatar(
            avatarUrl = myAvatarUrl,
            username = myUsername,
            size = 68.dp,
            hasStory = myHasStory,
            isStorySeen = seenStoryUserIds.contains(currentUserId),
          )
          Box(
            modifier =
              Modifier.align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(AuraBlue)
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                .clickable { onAddStoryClick() },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Add story",
              tint = Color.White,
              modifier = Modifier.size(14.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Your story",
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    items(otherStoryOwners, key = { it }) { ownerId ->
      val firstStory = storiesByUser[ownerId]?.firstOrNull() ?: return@items
      val isSeen = seenStoryUserIds.contains(ownerId)
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable { onOpenStory(ownerId) },
      ) {
        AuraAvatar(
          avatarUrl = firstStory.authorAvatarUrl.ifBlank { firstStory.imageData },
          username = firstStory.authorUsername,
          size = 68.dp,
          hasStory = true,
          isStorySeen = isSeen,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = firstStory.authorUsername,
          style = MaterialTheme.typography.bodySmall,
          color =
            if (isSeen) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun FeedPostCard(
  post: Post,
  isLiked: Boolean,
  isSaved: Boolean,
  isOwnPost: Boolean,
  isFavoriteAuthor: Boolean = false,
  hideLikeCounts: Boolean = false,
  hasActiveStory: Boolean,
  isStorySeen: Boolean,
  onAuthorClick: () -> Unit,
  onDoubleTapLike: () -> Unit,
  onToggleLike: () -> Unit,
  onToggleSave: () -> Unit,
  onCommentClick: () -> Unit,
  onShareClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onToggleArchiveClick: () -> Unit = {},
  onMuteAuthorClick: () -> Unit = {},
  onBlockAuthorClick: () -> Unit = {},
  onOpenReel: () -> Unit,
) {
  val context = LocalContext.current
  var showBigHeart by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  var captionExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(showBigHeart) {
    if (showBigHeart) {
      delay(650)
      showBigHeart = false
    }
  }

  val heartScale by
    animateFloatAsState(
      targetValue = if (isLiked) 1.12f else 1.0f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
      label = "heart_scale",
    )

  Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
    // Post Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f).clickable(onClick = onAuthorClick),
      ) {
        AuraAvatar(
          avatarUrl = post.authorAvatarUrl,
          username = post.authorUsername,
          size = 36.dp,
          hasStory = hasActiveStory,
          isStorySeen = isStorySeen,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = post.authorUsername,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            if (post.authorVerified) {
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Verified",
                tint = AuraVerifiedBlue,
                modifier = Modifier.size(14.dp),
              )
            }
            if (isFavoriteAuthor) {
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favorite",
                tint = AuraCoral,
                modifier = Modifier.size(14.dp),
              )
            }
          }
          if (post.location.isNotBlank()) {
            Text(
              text = post.location,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Box {
        IconButton(onClick = { menuExpanded = true }) {
          Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Post options",
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
            text = { Text(if (isSaved) "Remove from Saved" else "Save Post") },
            onClick = {
              onToggleSave()
              menuExpanded = false
            },
          )
          DropdownMenuItem(
            text = { Text("Share Externally") },
            onClick = {
              menuExpanded = false
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out @${post.authorUsername} on Aura: ${post.caption}",
                  )
                }
              context.startActivity(Intent.createChooser(shareIntent, "Share via"))
            },
          )
          if (isOwnPost) {
            DropdownMenuItem(
              text = { Text(if (post.visibility == "private") "Restore to Profile" else "Move to Archive") },
              onClick = {
                menuExpanded = false
                onToggleArchiveClick()
              },
            )
            DropdownMenuItem(
              text = { Text("Delete Post", color = MaterialTheme.colorScheme.error) },
              onClick = {
                menuExpanded = false
                onDeleteClick()
              },
            )
          } else {
            DropdownMenuItem(
              text = { Text("Mute @${post.authorUsername}") },
              onClick = {
                menuExpanded = false
                onMuteAuthorClick()
              },
            )
            DropdownMenuItem(
              text = { Text("Block @${post.authorUsername}", color = MaterialTheme.colorScheme.error) },
              onClick = {
                menuExpanded = false
                onBlockAuthorClick()
              },
            )
          }
        }
      }
    }

    // Post Image / Reel Preview with Double-Tap Heart
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(if (post.mediaType == "reel") 0.85f else 1f)
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .pointerInput(post.id) {
            detectTapGestures(
              onDoubleTap = {
                showBigHeart = true
                onDoubleTapLike()
              },
              onTap = {
                if (post.mediaType == "reel") onOpenReel()
              },
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      AuraImage(
        imageData = post.imageData,
        contentDescription = post.caption.ifBlank { "Post by ${post.authorUsername}" },
        filterName = post.filterName,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        targetSizePx = 1440,
      )

      if (post.mediaType == "reel") {
        Surface(
          color = Color.Black.copy(alpha = 0.55f),
          shape = CircleShape,
          modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Reel",
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "Reel",
              color = Color.White,
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      androidx.compose.animation.AnimatedVisibility(
        visible = showBigHeart,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
      ) {
        Icon(
          imageVector = Icons.Filled.Favorite,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.92f),
          modifier = Modifier.size(96.dp),
        )
      }
    }

    // Action Bar
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onToggleLike,
          modifier = Modifier.testTag("like_button_${post.id}"),
        ) {
          Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            tint = if (isLiked) AuraLikeRed else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
              scaleX = heartScale
              scaleY = heartScale
            },
          )
        }
        IconButton(
          onClick = onCommentClick,
          modifier = Modifier.testTag("comment_button_${post.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Comments",
            tint = MaterialTheme.colorScheme.onBackground,
          )
        }
        IconButton(
          onClick = onShareClick,
          modifier = Modifier.testTag("share_button_${post.id}"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Share post",
            tint = MaterialTheme.colorScheme.onBackground,
          )
        }
      }

      IconButton(
        onClick = onToggleSave,
        modifier = Modifier.testTag("save_button_${post.id}"),
      ) {
        Icon(
          imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
          contentDescription = if (isSaved) "Unsave" else "Save",
          tint = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    // Likes & Caption
    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
      val likesLabel =
        if (hideLikeCounts && !isOwnPost) {
          if (post.likesCount > 0L) "Liked by others" else "Be the first to like"
        } else {
          val formattedLikes = remember(post.likesCount) { formatCompactCount(post.likesCount) }
          "$formattedLikes likes"
        }
      Text(
        text = likesLabel,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      if (post.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        val annotated =
          remember(post.id, post.authorUsername, post.caption) {
            buildAnnotatedString {
              withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("${post.authorUsername} ")
              }
              val words = post.caption.split(" ")
              words.forEachIndexed { idx, word ->
                if (word.startsWith("#") || word.startsWith("@")) {
                  withStyle(SpanStyle(color = AuraBlue, fontWeight = FontWeight.Medium)) {
                    append(word)
                  }
                } else {
                  append(word)
                }
                if (idx < words.lastIndex) append(" ")
              }
            }
          }
        Text(
          text = annotated,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = if (captionExpanded) Int.MAX_VALUE else 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.clickable { captionExpanded = !captionExpanded },
        )
      }
      if (post.commentsCount > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "View all ${post.commentsCount} comments",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.clickable(onClick = onCommentClick),
        )
      }
      Spacer(modifier = Modifier.height(3.dp))
      val relativeTime = remember(post.createdAt) { formatRelativeTime(post.createdAt) }
      Text(
        text = relativeTime,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

fun formatCompactCount(count: Long): String {
  return when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
    count >= 10_000 -> String.format("%.1fK", count / 1_000f)
    else -> count.toString()
  }
}

fun formatRelativeTime(ts: Timestamp?): String {
  if (ts == null) return "Just now"
  val diffSec = ((System.currentTimeMillis() - ts.toDate().time) / 1000L).coerceAtLeast(0L)
  return when {
    diffSec < 60 -> "Just now"
    diffSec < 3600 -> "${diffSec / 60}m ago"
    diffSec < 86_400 -> "${diffSec / 3600}h ago"
    else -> "${diffSec / 86_400}d ago"
  }
}
