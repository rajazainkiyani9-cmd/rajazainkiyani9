package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.components.AuraImage
import com.example.ui.theme.AuraLikeRed
import com.example.ui.theme.AuraVerifiedBlue

@Composable
fun ReelsScreen(
  state: AuraHomeUiState,
  currentUserId: String,
  initialReelId: String? = null,
  onOpenProfile: (String) -> Unit,
  onToggleFollow: (UserProfile) -> Unit,
  onToggleLike: (Post, Boolean) -> Unit,
  onToggleSave: (Post) -> Unit,
  onOpenComments: (Post) -> Unit,
  onSharePost: (Post) -> Unit,
) {
  val reels = state.reels
  if (reels.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxSize().background(Color.Black),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "No Reels yet. Tap + to create your first Reel!",
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
      )
    }
    return
  }

  val startIndex =
    remember(reels, initialReelId) {
      reels.indexOfFirst { it.id == initialReelId }.coerceAtLeast(0)
    }
  val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { reels.size })

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    VerticalPager(
      state = pagerState,
      beyondViewportPageCount = 1,
      key = { reels[it].id },
      modifier = Modifier.fillMaxSize(),
    ) { page ->
      val reel = reels[page]
      val authorProfile = state.profilesById[reel.userId]
      val isLiked = state.likedPostIds.contains(reel.id)
      val isSaved = state.savedPostIds.contains(reel.id)
      val isFollowing = state.followingIds.contains(reel.userId)
      val isOwn = reel.userId == currentUserId
      val prefs = state.userPrivate
      val hideLikes = prefs?.hideLikeCounts == true && !isOwn
      val allowMotion = (prefs?.autoplayReels != false) && (prefs?.reducedMotion != true)
      val alwaysShowCaptions = prefs?.alwaysShowCaptions == true

      ReelPageItem(
        reel = reel,
        isActivePage = pagerState.currentPage == page,
        allowMotion = allowMotion,
        hideLikeCounts = hideLikes,
        alwaysShowCaptions = alwaysShowCaptions,
        isLiked = isLiked,
        isSaved = isSaved,
        isFollowing = isFollowing,
        isOwn = isOwn,
        onAuthorClick = { onOpenProfile(reel.userId) },
        onFollowClick = { if (authorProfile != null) onToggleFollow(authorProfile) },
        onDoubleTapLike = { onToggleLike(reel, true) },
        onToggleLike = { onToggleLike(reel, false) },
        onToggleSave = { onToggleSave(reel) },
        onCommentClick = { onOpenComments(reel) },
        onShareClick = { onSharePost(reel) },
      )
    }

    Text(
      text = "Reels",
      style = MaterialTheme.typography.headlineSmall,
      color = Color.White,
      modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 18.dp, vertical = 14.dp),
    )
  }
}

@Composable
private fun ReelPageItem(
  reel: Post,
  isActivePage: Boolean,
  allowMotion: Boolean,
  hideLikeCounts: Boolean,
  alwaysShowCaptions: Boolean,
  isLiked: Boolean,
  isSaved: Boolean,
  isFollowing: Boolean,
  isOwn: Boolean,
  onAuthorClick: () -> Unit,
  onFollowClick: () -> Unit,
  onDoubleTapLike: () -> Unit,
  onToggleLike: () -> Unit,
  onToggleSave: () -> Unit,
  onCommentClick: () -> Unit,
  onShareClick: () -> Unit,
) {
  var isPlaying by remember { mutableStateOf(allowMotion) }
  var isMuted by remember { mutableStateOf(false) }

  val shouldAnimate = isActivePage && isPlaying && allowMotion
  val kenBurnsScale: Float
  val discRotation: Float
  if (shouldAnimate) {
    val infiniteTransition = rememberInfiniteTransition(label = "reel_motion")
    kenBurnsScale =
      infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "ken_burns",
      ).value
    discRotation =
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing)),
        label = "disc_spin",
      ).value
  } else {
    kenBurnsScale = 1.0f
    discRotation = 0f
  }

  Box(
    modifier =
      Modifier.fillMaxSize().pointerInput(reel.id) {
        detectTapGestures(
          onTap = { isPlaying = !isPlaying },
          onDoubleTap = { onDoubleTapLike() },
        )
      }
  ) {
    AuraImage(
      imageData = reel.imageData,
      contentDescription = reel.caption,
      filterName = reel.filterName,
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          scaleX = kenBurnsScale
          scaleY = kenBurnsScale
        },
      contentScale = ContentScale.Crop,
      targetSizePx = 1440,
    )

    // Bottom Gradient Vignette
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(260.dp)
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
          )
    )

    // Play/Pause indicator
    AnimatedVisibility(
      visible = !isPlaying,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center),
    ) {
      Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape) {
        Icon(
          imageVector = Icons.Filled.Pause,
          contentDescription = "Paused",
          tint = Color.White,
          modifier = Modifier.padding(18.dp).size(40.dp),
        )
      }
    }

    // Top-right mute toggle
    IconButton(
      onClick = { isMuted = !isMuted },
      modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
    ) {
      Icon(
        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription = if (isMuted) "Unmute" else "Mute",
        tint = Color.White,
      )
    }

    // Right Vertical Action Bar
    Column(
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onToggleLike) {
          Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Like reel",
            tint = if (isLiked) AuraLikeRed else Color.White,
            modifier = Modifier.size(30.dp),
          )
        }
        Text(
          text = if (hideLikeCounts) "Likes" else formatCompactCount(reel.likesCount),
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onCommentClick) {
          Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Comments",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
          )
        }
        Text(
          text = formatCompactCount(reel.commentsCount),
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }

      IconButton(onClick = onShareClick) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.Send,
          contentDescription = "Share reel",
          tint = Color.White,
          modifier = Modifier.size(26.dp),
        )
      }

      IconButton(onClick = onToggleSave) {
        Icon(
          imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
          contentDescription = "Save reel",
          tint = Color.White,
          modifier = Modifier.size(26.dp),
        )
      }

      Box(
        modifier =
          Modifier.size(36.dp)
            .graphicsLayer { rotationZ = discRotation }
            .clip(CircleShape)
            .border(2.dp, Color.White, CircleShape)
      ) {
        AuraImage(
          imageData = reel.authorAvatarUrl.ifBlank { reel.imageData },
          contentDescription = "Audio disc",
          modifier = Modifier.fillMaxSize(),
          targetSizePx = 200,
        )
      }
    }

    // Bottom Left Creator Info
    Column(
      modifier =
        Modifier.align(Alignment.BottomStart)
          .padding(start = 16.dp, end = 84.dp, bottom = 24.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable(onClick = onAuthorClick),
        ) {
          AuraAvatar(
            avatarUrl = reel.authorAvatarUrl,
            username = reel.authorUsername,
            size = 36.dp,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = reel.authorUsername,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          if (reel.authorVerified) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Filled.Verified,
              contentDescription = null,
              tint = AuraVerifiedBlue,
              modifier = Modifier.size(14.dp),
            )
          }
        }
        if (!isOwn) {
          Spacer(modifier = Modifier.width(10.dp))
          Button(
            onClick = onFollowClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp).border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                contentColor = Color.White,
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

      if (reel.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = reel.caption,
          color = Color.White,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = if (alwaysShowCaptions) 5 else 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = reel.audioTitle.ifBlank { "Original Audio — ${reel.authorUsername}" },
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
