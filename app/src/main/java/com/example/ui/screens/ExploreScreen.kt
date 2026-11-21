package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.components.AuraImage
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraVerifiedBlue

@Composable
fun ExploreScreen(
  state: AuraHomeUiState,
  currentUserId: String,
  onOpenProfile: (String) -> Unit,
  onToggleFollow: (UserProfile) -> Unit,
  onOpenPostDetail: (Post) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("All") }
  val categories = remember {
    listOf("All", "Photography", "Travel", "Coffee", "Nightlife", "Art", "Reels")
  }

  val matchingProfiles =
    remember(state.allProfiles, query, currentUserId) {
      val trimmed = query.trim().lowercase()
      state.allProfiles
        .filter { it.userId != currentUserId }
        .filter {
          trimmed.isEmpty() ||
            it.username.lowercase().contains(trimmed) ||
            it.displayName.lowercase().contains(trimmed)
        }
    }

  val filteredPosts =
    remember(state.feedPosts, query, selectedCategory) {
      val trimmed = query.trim().lowercase()
      state.feedPosts.filter { post ->
        val matchesCategory =
          when (selectedCategory) {
            "All" -> true
            "Reels" -> post.mediaType == "reel"
            else ->
              post.tags.any { it.equals(selectedCategory, ignoreCase = true) } ||
                post.caption.contains(selectedCategory, ignoreCase = true)
          }
        val matchesQuery =
          trimmed.isEmpty() ||
            post.caption.lowercase().contains(trimmed) ||
            post.authorUsername.lowercase().contains(trimmed) ||
            post.location.lowercase().contains(trimmed) ||
            post.tags.any { it.lowercase().contains(trimmed) }
        matchesCategory && matchesQuery
      }
    }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Search Bar
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = { Text("Search people, #tags, places...") },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { query = "" }) {
            Icon(Icons.Filled.Close, contentDescription = "Clear search")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          focusedBorderColor = Color.Transparent,
          unfocusedBorderColor = Color.Transparent,
        ),
      modifier =
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).testTag("explore_search_input"),
    )

    // Category Chips
    LazyRow(
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(categories) { category ->
        FilterChip(
          selected = selectedCategory == category,
          onClick = { selectedCategory = category },
          label = { Text(category) },
        )
      }
    }

    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // Creators strip when searching or discovering
      if (matchingProfiles.isNotEmpty()) {
        item(span = { GridItemSpan(3) }, key = "creators_strip") {
          Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
              text = if (query.isBlank()) "Suggested Creators" else "People",
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            LazyRow(
              contentPadding = PaddingValues(horizontal = 14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(matchingProfiles, key = { it.userId }) { profile ->
                val isFollowing = state.followingIds.contains(profile.userId)
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                  modifier = Modifier.width(142.dp).clickable { onOpenProfile(profile.userId) },
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    AuraAvatar(
                      avatarUrl = profile.avatarUrl,
                      username = profile.username,
                      size = 56.dp,
                      hasStory = state.storiesByUser.containsKey(profile.userId),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = profile.username,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      if (profile.isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                          imageVector = Icons.Filled.Verified,
                          contentDescription = null,
                          tint = AuraVerifiedBlue,
                          modifier = Modifier.size(13.dp),
                        )
                      }
                    }
                    Text(
                      text = profile.displayName,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                      onClick = { onToggleFollow(profile) },
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                      modifier = Modifier.fillMaxWidth().height(32.dp),
                      shape = RoundedCornerShape(8.dp),
                      colors =
                        ButtonDefaults.buttonColors(
                          containerColor =
                            if (isFollowing) MaterialTheme.colorScheme.surface else AuraBlue,
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

      if (filteredPosts.isEmpty()) {
        item(span = { GridItemSpan(3) }, key = "explore_empty") {
          Box(
            modifier = Modifier.fillMaxWidth().padding(36.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "No matching posts found.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      items(filteredPosts, key = { it.id }, contentType = { "explore_tile" }) { post ->
        Box(
          modifier =
            Modifier.aspectRatio(1f)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onOpenPostDetail(post) }
        ) {
          AuraImage(
            imageData = post.imageData,
            contentDescription = post.caption,
            filterName = post.filterName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            targetSizePx = 540,
          )
          if (post.mediaType == "reel") {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Reel",
              tint = Color.White,
              modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
            )
          }
        }
      }
    }
  }
}
