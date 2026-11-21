package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@Immutable
@IgnoreExtraProperties
data class UserPrivate(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val darkModeEnabled: Boolean = false,
  val themeMode: String = "system",
  val notificationsMuted: Boolean = false,
  val notifyLikes: Boolean = true,
  val notifyComments: Boolean = true,
  val notifyFollows: Boolean = true,
  val notifyMessages: Boolean = true,
  val dailyTimeLimitMinutes: Long = 0L,
  val sleepModeEnabled: Boolean = false,
  val hideLikeCounts: Boolean = false,
  val sensitiveContentLevel: String = "Standard",
  val autoplayReels: Boolean = true,
  val allowMessagesFrom: String = "Everyone",
  val showActivityStatus: Boolean = true,
  val showReadReceipts: Boolean = true,
  val allowTagsFrom: String = "Everyone",
  val allowCommentsFrom: String = "Everyone",
  val allowStorySharing: Boolean = true,
  val allowPostSharing: Boolean = true,
  val hideOffensiveComments: Boolean = true,
  val saveStoryToArchive: Boolean = true,
  val saveToCameraRoll: Boolean = false,
  val dataSaverEnabled: Boolean = false,
  val uploadHighQuality: Boolean = true,
  val alwaysShowCaptions: Boolean = false,
  val reducedMotion: Boolean = false,
  val appLanguage: String = "English",
  val closeFriendIds: List<String> = emptyList(),
  val blockedUserIds: List<String> = emptyList(),
  val mutedUserIds: List<String> = emptyList(),
  val restrictedUserIds: List<String> = emptyList(),
  val favoriteUserIds: List<String> = emptyList(),
  val hideStoryFromIds: List<String> = emptyList(),
  val hiddenWords: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val username: String = "",
  val displayName: String = "",
  val bio: String = "",
  val avatarUrl: String = "",
  val website: String = "",
  val followersCount: Long = 0L,
  val followingCount: Long = 0L,
  val postsCount: Long = 0L,
  val isVerified: Boolean = false,
  val isPublic: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class Post(
  @DocumentId val id: String = "",
  val userId: String = "",
  val authorUsername: String = "",
  val authorAvatarUrl: String = "",
  val authorVerified: Boolean = false,
  val caption: String = "",
  val mediaType: String = "photo",
  val imageData: String = "",
  val filterName: String = "Original",
  val location: String = "",
  val audioTitle: String = "",
  val tags: List<String> = emptyList(),
  val likesCount: Long = 0L,
  val commentsCount: Long = 0L,
  val visibility: String = "public",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class Comment(
  @DocumentId val id: String = "",
  val userId: String = "",
  val postId: String = "",
  val authorUsername: String = "",
  val authorAvatarUrl: String = "",
  val text: String = "",
  val visibility: String = "public",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class Story(
  @DocumentId val id: String = "",
  val userId: String = "",
  val authorUsername: String = "",
  val authorAvatarUrl: String = "",
  val imageData: String = "",
  val captionText: String = "",
  val backgroundHex: String = "#110D1C",
  val visibility: String = "public",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class LikedPost(
  @DocumentId val id: String = "",
  val userId: String = "",
  val postId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class SavedPost(
  @DocumentId val id: String = "",
  val userId: String = "",
  val postId: String = "",
  val imageData: String = "",
  val mediaType: String = "photo",
  val caption: String = "",
  val authorUsername: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class FollowingEdge(
  @DocumentId val id: String = "",
  val userId: String = "",
  val targetUserId: String = "",
  val targetUsername: String = "",
  val targetDisplayName: String = "",
  val targetAvatarUrl: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class ActivityNotification(
  @DocumentId val id: String = "",
  val recipientId: String = "",
  val actorId: String = "",
  val actorUsername: String = "",
  val actorAvatarUrl: String = "",
  val type: String = "like",
  val postId: String = "",
  val previewText: String = "",
  val isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class Conversation(
  @DocumentId val id: String = "",
  val participantIds: List<String> = emptyList(),
  val userAId: String = "",
  val userAName: String = "",
  val userAAvatar: String = "",
  val userBId: String = "",
  val userBName: String = "",
  val userBAvatar: String = "",
  val lastMessage: String = "",
  val lastSenderId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Immutable
@IgnoreExtraProperties
data class DirectMessage(
  @DocumentId val id: String = "",
  val senderId: String = "",
  val conversationId: String = "",
  val participantIds: List<String> = emptyList(),
  val text: String = "",
  val sharedPostId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)
