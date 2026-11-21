package com.example.ui

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityNotification
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.DirectMessage
import com.example.data.model.Post
import com.example.data.model.SavedPost
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.data.model.UserPrivate
import com.example.data.model.UserProfile
import com.example.data.repository.AuraRepository
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.delay

@Immutable
data class AuraHomeUiState(
  val myProfile: UserProfile? = null,
  val userPrivate: UserPrivate? = null,
  val allProfiles: List<UserProfile> = emptyList(),
  val profilesById: Map<String, UserProfile> = emptyMap(),
  val feedPosts: List<Post> = emptyList(),
  val archivedPosts: List<Post> = emptyList(),
  val reels: List<Post> = emptyList(),
  val storiesByUser: Map<String, List<Story>> = emptyMap(),
  val myStories: List<Story> = emptyList(),
  val likedPostIds: Set<String> = emptySet(),
  val savedPostIds: Set<String> = emptySet(),
  val savedPosts: List<SavedPost> = emptyList(),
  val followingIds: Set<String> = emptySet(),
  val seenStoryUserIds: Set<String> = emptySet(),
  val notifications: List<ActivityNotification> = emptyList(),
  val conversations: List<Conversation> = emptyList(),
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
)

class AuraViewModel(
  private val repository: AuraRepository,
  private val currentUserId: String,
  private val preferredDisplayName: String? = null,
  private val preferredUsername: String? = null,
) : ViewModel() {

  private val _pendingLikeOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  private val _pendingSaveOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  private val _pendingFollowOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  private val _seenStoryUserIds = MutableStateFlow<Set<String>>(emptySet())
  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _selectedCommentPostId = MutableStateFlow<String?>(null)
  private val _selectedConversationId = MutableStateFlow<String?>(null)
  val sessionStartMs: Long = System.currentTimeMillis()
  private var remindedDailyLimit = false

  init {
    viewModelScope.launch(Dispatchers.IO) {
      Firebase.auth.currentUser?.let { user ->
        val result =
          repository.ensureUserInitialized(
            user = user,
            preferredDisplayName = preferredDisplayName,
            preferredUsername = preferredUsername,
          )
        if (result.isFailure) {
          Log.w(TAG, "Profile initialization warning", result.exceptionOrNull())
        }
      }
    }
    viewModelScope.launch {
      while (!remindedDailyLimit) {
        delay(15_000L)
        val elapsedSec = (System.currentTimeMillis() - sessionStartMs) / 1000L
        val limitMins = uiState.value.userPrivate?.dailyTimeLimitMinutes ?: 0L
        if (!remindedDailyLimit && limitMins > 0L && elapsedSec >= limitMins * 60L) {
          remindedDailyLimit = true
          _statusBanner.value = "Daily time limit reached (${limitMins}m). Take a break whenever ready."
        }
      }
    }
  }

  private fun <T> Flow<T>.safeFirestoreFlow(label: String, fallback: T): Flow<T> =
    catch { error ->
      val firestoreError =
        generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing $label", rootError)
      emit(fallback)
    }.onStart { emit(fallback) }

  private val myProfileFlow = repository.observeMyProfile().safeFirestoreFlow("myProfile", null)

  private val userPrivateFlow =
    repository.observeUserPrivate().safeFirestoreFlow("userPrivate", null)

  private val publicProfilesFlow =
    repository.observePublicProfiles().safeFirestoreFlow("publicProfiles", emptyList())

  private val postsLoadedStateFlow =
    repository
      .observePublicPosts()
      .map { it to true }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing publicPosts", firestoreError ?: error)
        emit(emptyList<Post>() to true)
      }
      .onStart { emit(emptyList<Post>() to false) }

  private val myPostsFlow =
    repository.observeMyPosts().safeFirestoreFlow("myPosts", emptyList())

  private val publicStoriesFlow =
    repository.observePublicStories().safeFirestoreFlow("publicStories", emptyList())

  private val likedIdsFlow =
    repository.observeLikedPostIds().safeFirestoreFlow("likedPostIds", emptySet())

  private val savedPostsFlow =
    repository.observeSavedPosts().safeFirestoreFlow("savedPosts", emptyList())

  private val followingFlow =
    repository.observeFollowing().safeFirestoreFlow("following", emptyList())

  private val notificationsFlow =
    repository.observeNotifications().safeFirestoreFlow("notifications", emptyList())

  private val conversationsFlow =
    repository.observeConversations().safeFirestoreFlow("conversations", emptyList())

  private val baseAccountFlow =
    combine(
      myProfileFlow,
      userPrivateFlow,
      publicProfilesFlow,
      followingFlow,
      _pendingFollowOverrides,
    ) { myProfile, userPrivate, remoteProfiles, remoteFollowing, followOverrides ->
      val blockedSet = userPrivate?.blockedUserIds?.toSet().orEmpty()
      val remoteFollowSet = remoteFollowing.map { it.targetUserId }.toSet()
      val effectiveFollowing =
        buildSet(remoteFollowSet.size + followOverrides.size) {
          addAll(remoteFollowSet)
          followOverrides.forEach { (uid, shouldFollow) ->
            if (shouldFollow) add(uid) else remove(uid)
          }
        }
      val distinctProfiles =
        (listOfNotNull(myProfile) + remoteProfiles)
          .distinctBy { it.userId }
          .filter { it.userId == currentUserId || !blockedSet.contains(it.userId) }
      val profileMap = distinctProfiles.associateBy { it.userId }
      AccountBundle(myProfile, userPrivate, distinctProfiles, profileMap, effectiveFollowing)
    }

  private val contentBundleFlow =
    combine(
      postsLoadedStateFlow,
      myPostsFlow,
      publicStoriesFlow,
      likedIdsFlow,
      savedPostsFlow,
    ) { postsPair, myPosts, remoteStories, remoteLikes, remoteSaved ->
      ContentBundle(postsPair.first, myPosts, postsPair.second, remoteStories, remoteLikes, remoteSaved)
    }

  private val extraBundleFlow =
    combine(
      _pendingLikeOverrides,
      _pendingSaveOverrides,
      _seenStoryUserIds,
      notificationsFlow,
      conversationsFlow,
    ) { likeOverrides, saveOverrides, seenStories, remoteNotifs, remoteConvs ->
      ExtraBundle(likeOverrides, saveOverrides, seenStories, remoteNotifs, remoteConvs)
    }

  val uiState: StateFlow<AuraHomeUiState> =
    combine(baseAccountFlow, contentBundleFlow, extraBundleFlow) { account, content, extra ->
      val blockedSet = account.userPrivate?.blockedUserIds?.toSet().orEmpty()
      val effectiveLikes =
        buildSet(content.remoteLikes.size + extra.likeOverrides.size) {
          addAll(content.remoteLikes)
          extra.likeOverrides.forEach { (postId, liked) ->
            if (liked) add(postId) else remove(postId)
          }
        }

      val mergedPublicPosts =
        (content.remotePosts + content.myPosts.filter { it.visibility == "public" })
          .distinctBy { it.id }
          .filter { it.userId == currentUserId || !blockedSet.contains(it.userId) }
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      val archivedList =
        content.myPosts
          .filter { it.visibility == "private" }
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      val reelsList = mergedPublicPosts.filter { it.mediaType == "reel" }
      val visibleStories =
        content.remoteStories.filter { it.userId == currentUserId || !blockedSet.contains(it.userId) }
      val groupedStories = visibleStories.groupBy { it.userId }
      val myStoriesList = groupedStories[currentUserId].orEmpty()

      val effectiveSavedIds =
        buildSet(content.remoteSaved.size + extra.saveOverrides.size) {
          content.remoteSaved.forEach { add(it.postId) }
          extra.saveOverrides.forEach { (postId, saved) ->
            if (saved) add(postId) else remove(postId)
          }
        }
      val visibleConvs =
        extra.remoteConvs.filter { conv ->
          val otherId = if (conv.userAId == currentUserId) conv.userBId else conv.userAId
          !blockedSet.contains(otherId)
        }

      AuraHomeUiState(
        myProfile = account.myProfile,
        userPrivate = account.userPrivate,
        allProfiles = account.allProfiles,
        profilesById = account.profilesById,
        feedPosts = mergedPublicPosts,
        archivedPosts = archivedList,
        reels = reelsList,
        storiesByUser = groupedStories,
        myStories = myStoriesList,
        likedPostIds = effectiveLikes,
        savedPostIds = effectiveSavedIds,
        savedPosts = content.remoteSaved,
        followingIds = account.followingIds,
        seenStoryUserIds = extra.seenStories,
        notifications = extra.remoteNotifs,
        conversations = visibleConvs,
        isLoading = !content.postsLoaded,
      )
    }
      .conflate()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AuraHomeUiState(isLoading = true),
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeComments: StateFlow<UiState<List<Comment>>> =
    _selectedCommentPostId
      .flatMapLatest { postId ->
        if (postId == null) {
          flowOf(UiState.Success(emptyList()))
        } else {
          repository
            .observeComments(postId)
            .map<List<Comment>, UiState<List<Comment>>> { UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { error ->
              val fsErr =
                generateSequence(error) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()
              if (fsErr == null && error is CancellationException) throw error
              emit(UiState.Error((fsErr ?: error).message ?: "Failed to load comments"))
            }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeMessages: StateFlow<UiState<List<DirectMessage>>> =
    _selectedConversationId
      .flatMapLatest { convId ->
        if (convId == null) {
          flowOf(UiState.Success(emptyList()))
        } else {
          repository
            .observeMessages(convId)
            .map<List<DirectMessage>, UiState<List<DirectMessage>>> { UiState.Success(it) }
            .onStart { emit(UiState.Loading) }
            .catch { error ->
              val fsErr =
                generateSequence(error) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()
              if (fsErr == null && error is CancellationException) throw error
              emit(UiState.Error((fsErr ?: error).message ?: "Failed to load messages"))
            }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  fun openCommentsForPost(postId: String?) {
    _selectedCommentPostId.value = postId
  }

  fun openConversation(conversationId: String?) {
    _selectedConversationId.value = conversationId
  }

  fun markStorySeen(userId: String) {
    _seenStoryUserIds.update { it + userId }
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun toggleDarkMode(enabled: Boolean) {
    viewModelScope.launch {
      repository.setDarkMode(enabled).onFailure {
        _statusBanner.value = "Could not update theme setting"
      }
    }
  }

  fun setThemeMode(mode: String) {
    val isDark = mode == "dark"
    viewModelScope.launch {
      repository
        .updateUserSettings(mapOf("themeMode" to mode, "darkModeEnabled" to isDark))
        .onFailure { _statusBanner.value = "Could not update appearance" }
    }
  }

  fun updateSettings(updates: Map<String, Any>, toast: String? = null) {
    viewModelScope.launch {
      repository
        .updateUserSettings(updates)
        .onSuccess { if (!toast.isNullOrBlank()) _statusBanner.value = toast }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save setting" }
    }
  }

  fun setAccountPrivacy(isPrivate: Boolean) {
    viewModelScope.launch {
      repository
        .setAccountPrivacy(isPublic = !isPrivate)
        .onSuccess {
          _statusBanner.value =
            if (isPrivate) "Switched to Private account" else "Switched to Public account"
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update account privacy" }
    }
  }

  fun toggleUserInSettingList(
    fieldKey: String,
    currentList: List<String>,
    targetUserId: String,
    addedMessage: String,
    removedMessage: String,
  ) {
    val nextList =
      if (currentList.contains(targetUserId)) currentList.filterNot { it == targetUserId }
      else (currentList + targetUserId).distinct().take(200)
    val toast = if (currentList.contains(targetUserId)) removedMessage else addedMessage
    updateSettings(mapOf(fieldKey to nextList), toast)
  }

  fun addHiddenWord(rawWord: String) {
    val clean = rawWord.trim().lowercase()
    if (clean.isEmpty()) return
    val current = uiState.value.userPrivate?.hiddenWords.orEmpty()
    if (current.contains(clean)) return
    updateSettings(mapOf("hiddenWords" to (current + clean).take(200)), "Added \"$clean\" to hidden words")
  }

  fun removeHiddenWord(word: String) {
    val current = uiState.value.userPrivate?.hiddenWords.orEmpty()
    updateSettings(mapOf("hiddenWords" to current.filterNot { it == word }), "Removed \"$word\"")
  }

  fun toggleArchivePost(post: Post) {
    val nextVis = if (post.visibility == "private") "public" else "private"
    viewModelScope.launch {
      repository
        .setPostVisibility(post.id, nextVis)
        .onSuccess {
          _statusBanner.value =
            if (nextVis == "private") "Moved to Archive" else "Restored to Profile"
        }
        .onFailure { _statusBanner.value = "Failed to update archive status" }
    }
  }

  fun deleteStory(storyId: String) {
    viewModelScope.launch {
      repository
        .deleteStory(storyId)
        .onSuccess { _statusBanner.value = "Story deleted" }
        .onFailure { _statusBanner.value = "Failed to delete story" }
    }
  }

  fun updateMyProfile(
    username: String,
    displayName: String,
    bio: String,
    website: String,
    avatarUrl: String,
  ) {
    viewModelScope.launch {
      repository
        .updateProfile(username, displayName, bio, website, avatarUrl)
        .onSuccess { _statusBanner.value = "Profile updated" }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update profile" }
    }
  }

  fun toggleLike(post: Post, forceLike: Boolean = false) {
    val isCurrentlyLiked = uiState.value.likedPostIds.contains(post.id)
    if (forceLike && isCurrentlyLiked) return
    val nextLiked = !isCurrentlyLiked
    _pendingLikeOverrides.update { it + (post.id to nextLiked) }

    viewModelScope.launch {
      repository
        .toggleLike(post, isCurrentlyLiked, uiState.value.myProfile)
        .onFailure { _statusBanner.value = "Failed to update like" }
      _pendingLikeOverrides.update { it - post.id }
    }
  }

  fun toggleSave(post: Post) {
    val isSaved = uiState.value.savedPostIds.contains(post.id)
    val nextSaved = !isSaved
    _pendingSaveOverrides.update { it + (post.id to nextSaved) }

    viewModelScope.launch {
      repository
        .toggleSavePost(post, isSaved)
        .onSuccess {
          _statusBanner.value = if (isSaved) "Removed from Saved" else "Saved to Collection"
        }
        .onFailure { _statusBanner.value = "Failed to update bookmark" }
      _pendingSaveOverrides.update { it - post.id }
    }
  }

  fun addComment(post: Post, text: String) {
    val profile = uiState.value.myProfile ?: return
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return

    viewModelScope.launch {
      repository.addComment(post, profile, trimmed).onFailure {
        _statusBanner.value = "Failed to post comment"
      }
    }
  }

  fun toggleFollow(targetProfile: UserProfile) {
    if (targetProfile.userId == currentUserId) return
    val isFollowing = uiState.value.followingIds.contains(targetProfile.userId)
    val myProfile = uiState.value.myProfile ?: return
    _pendingFollowOverrides.update { it + (targetProfile.userId to !isFollowing) }

    viewModelScope.launch {
      repository.toggleFollow(targetProfile, isFollowing, myProfile).onFailure {
        _statusBanner.value = "Failed to update follow status"
      }
      _pendingFollowOverrides.update { it - targetProfile.userId }
    }
  }

  fun publishPost(
    caption: String,
    mediaType: String,
    imageData: String,
    filterName: String,
    location: String,
    audioTitle: String,
    onDone: () -> Unit,
  ) {
    val profile = uiState.value.myProfile ?: return
    val extractedTags =
      Regex("#([A-Za-z0-9_]+)")
        .findAll(caption)
        .map { it.groupValues[1].lowercase() }
        .distinct()
        .take(10)
        .toList()

    viewModelScope.launch {
      repository
        .createPost(
          authorProfile = profile,
          caption = caption,
          mediaType = mediaType,
          imageData = imageData,
          filterName = filterName,
          location = location,
          audioTitle = audioTitle,
          tags = extractedTags,
        )
        .onSuccess {
          _statusBanner.value =
            if (mediaType == "reel") "Reel published to Aura!" else "Post shared to feed!"
          onDone()
        }
        .onFailure {
          _statusBanner.value = it.localizedMessage ?: "Failed to publish post"
          onDone()
        }
    }
  }

  fun publishStory(
    imageData: String,
    captionText: String,
    backgroundHex: String,
    onDone: () -> Unit,
  ) {
    val profile = uiState.value.myProfile ?: return
    viewModelScope.launch {
      repository
        .createStory(profile, imageData, captionText, backgroundHex)
        .onSuccess {
          _statusBanner.value = "Added to your Story!"
          onDone()
        }
        .onFailure {
          _statusBanner.value = it.localizedMessage ?: "Failed to add story"
          onDone()
        }
    }
  }

  fun deletePost(postId: String) {
    viewModelScope.launch {
      repository
        .deletePost(postId)
        .onSuccess { _statusBanner.value = "Post deleted" }
        .onFailure { _statusBanner.value = "Failed to delete post" }
    }
  }

  fun markNotificationRead(notificationId: String) {
    viewModelScope.launch { repository.markNotificationRead(notificationId) }
  }

  fun sendMessage(otherProfile: UserProfile, text: String, sharedPostId: String = "") {
    val myProfile = uiState.value.myProfile ?: return
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return

    viewModelScope.launch {
      repository
        .sendDirectMessage(myProfile, otherProfile, trimmed, sharedPostId)
        .onSuccess { convId -> _selectedConversationId.value = convId }
        .onFailure { _statusBanner.value = "Failed to send message" }
    }
  }

  private data class AccountBundle(
    val myProfile: UserProfile?,
    val userPrivate: UserPrivate?,
    val allProfiles: List<UserProfile>,
    val profilesById: Map<String, UserProfile>,
    val followingIds: Set<String>,
  )

  private data class ContentBundle(
    val remotePosts: List<Post>,
    val myPosts: List<Post>,
    val postsLoaded: Boolean,
    val remoteStories: List<Story>,
    val remoteLikes: Set<String>,
    val remoteSaved: List<SavedPost>,
  )

  private data class ExtraBundle(
    val likeOverrides: Map<String, Boolean>,
    val saveOverrides: Map<String, Boolean>,
    val seenStories: Set<String>,
    val remoteNotifs: List<ActivityNotification>,
    val remoteConvs: List<Conversation>,
  )

  private companion object {
    const val TAG = "AuraVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
