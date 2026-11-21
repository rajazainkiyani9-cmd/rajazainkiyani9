package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ActivityNotification
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.DirectMessage
import com.example.data.model.FollowingEdge
import com.example.data.model.LikedPost
import com.example.data.model.Post
import com.example.data.model.SavedPost
import com.example.data.model.Story
import com.example.data.model.UserPrivate
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AuraRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun <T> Flow<T>.logFirestoreErrors(
    operationType: OperationType,
    path: String,
    uid: String,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path, auth)
    }
    throw error
  }

  suspend fun ensureUserInitialized(
    user: FirebaseUser,
    preferredDisplayName: String? = null,
    preferredUsername: String? = null,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val privateRef = db.collection("users").document(uid)
    val profileRef = db.collection("profiles").document(uid)

    try {
      val (privateSnap, profileSnap) =
        coroutineScope {
          val pDeferred = async { privateRef.get().await() }
          val profDeferred = async { profileRef.get().await() }
          pDeferred.await() to profDeferred.await()
        }

      if (!privateSnap.exists()) {
        val privatePayload =
          mapOf(
            "userId" to uid,
            "email" to (user.email ?: ""),
            "darkModeEnabled" to false,
            "notificationsMuted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        privateRef.set(privatePayload).await()
      }

      if (!profileSnap.exists()) {
        val rawName =
          preferredDisplayName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: user.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Aura ${it.takeLast(4)}" }
            ?: "aura_creator"
        val fallbackUsernameSeed =
          user.phoneNumber?.takeIf { it.isNotBlank() }?.let { "aura_${it.takeLast(4)}_${uid.take(4)}" }
            ?: rawName
        val usernameSeed =
          preferredUsername?.takeIf { it.isNotBlank() } ?: fallbackUsernameSeed
        val sanitizedUsername =
          usernameSeed.lowercase().replace(Regex("[^a-z0-9._]"), "_").take(24).ifBlank { "aura_${uid.take(6)}" }
            .let { if (it.length < 2) "${it}_aura" else it }
        val profilePayload =
          mapOf(
            "userId" to uid,
            "username" to sanitizedUsername,
            "displayName" to rawName.take(50),
            "bio" to "Sharing moments on Aura",
            "avatarUrl" to (user.photoUrl?.toString() ?: ""),
            "website" to "",
            "followersCount" to 0L,
            "followingCount" to 0L,
            "postsCount" to 0L,
            "isVerified" to false,
            "isPublic" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        profileRef.set(profilePayload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid", auth)
      throw e
    }
  }

  fun observeUserPrivate(): Flow<UserPrivate?> = flow {
    val uid = requireUserId()
    val path = "users/$uid"
    emitAll(
      db.collection("users")
        .document(uid)
        .snapshots()
        .map { snap ->
          snap.toObject(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.GET, path, uid)
    )
  }

  fun observeMyProfile(): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    val path = "profiles/$uid"
    emitAll(
      db.collection("profiles")
        .document(uid)
        .snapshots()
        .map { snap ->
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.GET, path, uid)
    )
  }

  fun observePublicProfiles(): Flow<List<UserProfile>> = flow {
    val uid = requireUserId()
    val path = "profiles"
    emitAll(
      db.collection("profiles")
        .whereEqualTo("isPublic", true)
        .limit(80)
        .snapshots()
        .map { snap ->
          snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observePublicPosts(): Flow<List<Post>> = flow {
    val uid = requireUserId()
    val path = "posts"
    emitAll(
      db.collection("posts")
        .whereEqualTo("visibility", "public")
        .limit(80)
        .snapshots()
        .map { snap ->
          snap.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeMyPosts(): Flow<List<Post>> = flow {
    val uid = requireUserId()
    val path = "posts"
    emitAll(
      db.collection("posts")
        .whereEqualTo("userId", uid)
        .limit(80)
        .snapshots()
        .map { snap ->
          snap.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observePublicStories(): Flow<List<Story>> = flow {
    val uid = requireUserId()
    val path = "stories"
    emitAll(
      db.collection("stories")
        .whereEqualTo("visibility", "public")
        .limit(60)
        .snapshots()
        .map { snap ->
          snap.toObjects(Story::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeLikedPostIds(): Flow<Set<String>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/liked_posts"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("liked_posts")
        .snapshots()
        .map { snap ->
          snap.toObjects(LikedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .map { it.postId }
            .toSet()
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeSavedPosts(): Flow<List<SavedPost>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/saved_posts"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("saved_posts")
        .snapshots()
        .map { snap ->
          snap.toObjects(SavedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeFollowing(): Flow<List<FollowingEdge>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/following"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("following")
        .snapshots()
        .map { snap ->
          snap.toObjects(FollowingEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeNotifications(): Flow<List<ActivityNotification>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/notifications"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("notifications")
        .limit(50)
        .snapshots()
        .map { snap ->
          snap.toObjects(
            ActivityNotification::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeComments(postId: String): Flow<List<Comment>> = flow {
    val uid = requireUserId()
    val path = "posts/$postId/comments"
    emitAll(
      db.collection("posts")
        .document(postId)
        .collection("comments")
        .whereEqualTo("visibility", "public")
        .limit(100)
        .snapshots()
        .map { snap ->
          snap.toObjects(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeConversations(): Flow<List<Conversation>> = flow {
    val uid = requireUserId()
    val path = "conversations"
    emitAll(
      db.collection("conversations")
        .whereArrayContains("participantIds", uid)
        .limit(50)
        .snapshots()
        .map { snap ->
          snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  fun observeMessages(conversationId: String): Flow<List<DirectMessage>> = flow {
    val uid = requireUserId()
    val path = "conversations/$conversationId/messages"
    emitAll(
      db.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .whereArrayContains("participantIds", uid)
        .limit(100)
        .snapshots()
        .map { snap ->
          snap.toObjects(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  suspend fun updateProfile(
    username: String,
    displayName: String,
    bio: String,
    website: String,
    avatarUrl: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val ref = db.collection("profiles").document(uid)
    val updates =
      mapOf(
        "username" to username.trim().take(30),
        "displayName" to displayName.trim().take(50),
        "bio" to bio.trim().take(200),
        "website" to website.trim().take(120),
        "avatarUrl" to avatarUrl,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      ref.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path, auth)
      throw e
    }
  }

  suspend fun setDarkMode(enabled: Boolean): Result<Unit> =
    updateUserSettings(
      mapOf(
        "darkModeEnabled" to enabled,
        "themeMode" to if (enabled) "dark" else "light",
      )
    )

  suspend fun updateUserSettings(updates: Map<String, Any>): Result<Unit> = runCatching {
    val uid = requireUserId()
    val ref = db.collection("users").document(uid)
    val payload = updates.toMutableMap().apply {
      put("updatedAt", FieldValue.serverTimestamp())
    }
    try {
      ref.update(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path, auth)
      throw e
    }
  }

  suspend fun setAccountPrivacy(isPublic: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val ref = db.collection("profiles").document(uid)
    try {
      ref.update(
        mapOf(
          "isPublic" to isPublic,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path, auth)
      throw e
    }
  }

  suspend fun setPostVisibility(postId: String, visibility: String): Result<Unit> = runCatching {
    requireUserId()
    val ref = db.collection("posts").document(postId)
    try {
      ref.update(
        mapOf(
          "visibility" to visibility,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path, auth)
      throw e
    }
  }

  suspend fun deleteStory(storyId: String): Result<Unit> = runCatching {
    requireUserId()
    val ref = db.collection("stories").document(storyId)
    try {
      ref.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path, auth)
      throw e
    }
  }

  suspend fun createPost(
    authorProfile: UserProfile,
    caption: String,
    mediaType: String,
    imageData: String,
    filterName: String,
    location: String,
    audioTitle: String,
    tags: List<String>,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("posts").document()
    val profileRef = db.collection("profiles").document(uid)
    val payload =
      mapOf(
        "userId" to uid,
        "authorUsername" to authorProfile.username.ifBlank { "aura_user" },
        "authorAvatarUrl" to authorProfile.avatarUrl,
        "authorVerified" to authorProfile.isVerified,
        "caption" to caption.take(2200),
        "mediaType" to mediaType,
        "imageData" to imageData,
        "filterName" to filterName.take(40),
        "location" to location.take(80),
        "audioTitle" to audioTitle.take(100),
        "tags" to tags.take(10),
        "likesCount" to 0L,
        "commentsCount" to 0L,
        "visibility" to "public",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      val batch = db.batch()
      batch.set(docRef, payload)
      batch.update(
        profileRef,
        mapOf(
          "postsCount" to FieldValue.increment(1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun deletePost(postId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val postRef = db.collection("posts").document(postId)
    val profileRef = db.collection("profiles").document(uid)
    try {
      val batch = db.batch()
      batch.delete(postRef)
      batch.update(
        profileRef,
        mapOf(
          "postsCount" to FieldValue.increment(-1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, postRef.path, auth)
      throw e
    }
  }

  suspend fun createStory(
    authorProfile: UserProfile,
    imageData: String,
    captionText: String,
    backgroundHex: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("stories").document()
    val payload =
      mapOf(
        "userId" to uid,
        "authorUsername" to authorProfile.username.ifBlank { "aura_user" },
        "authorAvatarUrl" to authorProfile.avatarUrl,
        "imageData" to imageData,
        "captionText" to captionText.take(200),
        "backgroundHex" to backgroundHex.take(20),
        "visibility" to "public",
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun toggleLike(
    post: Post,
    currentlyLiked: Boolean,
    actorProfile: UserProfile?,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val likeRef = db.collection("users").document(uid).collection("liked_posts").document(post.id)
    val postRef = db.collection("posts").document(post.id)
    try {
      val batch = db.batch()
      if (currentlyLiked) {
        batch.delete(likeRef)
        batch.update(postRef, "likesCount", FieldValue.increment(-1L))
      } else {
        batch.set(
          likeRef,
          mapOf(
            "userId" to uid,
            "postId" to post.id,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.update(postRef, "likesCount", FieldValue.increment(1L))
      }
      batch.commit().await()

      if (!currentlyLiked && post.userId != uid && actorProfile != null) {
        sendNotification(
          recipientId = post.userId,
          actorProfile = actorProfile,
          type = "like",
          postId = post.id,
          previewText = "liked your ${if (post.mediaType == "reel") "reel" else "post"}",
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, postRef.path, auth)
      throw e
    }
  }

  suspend fun toggleSavePost(post: Post, currentlySaved: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val savedRef = db.collection("users").document(uid).collection("saved_posts").document(post.id)
    try {
      if (currentlySaved) {
        savedRef.delete().await()
      } else {
        savedRef.set(
          mapOf(
            "userId" to uid,
            "postId" to post.id,
            "imageData" to post.imageData,
            "mediaType" to post.mediaType,
            "caption" to post.caption.take(2200),
            "authorUsername" to post.authorUsername,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, savedRef.path, auth)
      throw e
    }
  }

  suspend fun addComment(
    post: Post,
    actorProfile: UserProfile,
    text: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val trimmed = text.trim()
    require(trimmed.isNotEmpty()) { "Comment cannot be empty" }
    val commentRef = db.collection("posts").document(post.id).collection("comments").document()
    val postRef = db.collection("posts").document(post.id)
    try {
      val batch = db.batch()
      batch.set(
        commentRef,
        mapOf(
          "userId" to uid,
          "postId" to post.id,
          "authorUsername" to actorProfile.username.ifBlank { "aura_user" },
          "authorAvatarUrl" to actorProfile.avatarUrl,
          "text" to trimmed.take(500),
          "visibility" to "public",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(postRef, "commentsCount", FieldValue.increment(1L))
      batch.commit().await()

      if (post.userId != uid) {
        sendNotification(
          recipientId = post.userId,
          actorProfile = actorProfile,
          type = "comment",
          postId = post.id,
          previewText = "commented: ${trimmed.take(80)}",
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, commentRef.path, auth)
      throw e
    }
  }

  suspend fun toggleFollow(
    targetProfile: UserProfile,
    currentlyFollowing: Boolean,
    actorProfile: UserProfile,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(targetProfile.userId != uid) { "Cannot follow yourself" }
    val followRef =
      db.collection("users").document(uid).collection("following").document(targetProfile.userId)
    val targetProfileRef = db.collection("profiles").document(targetProfile.userId)
    val myProfileRef = db.collection("profiles").document(uid)
    try {
      val batch = db.batch()
      if (currentlyFollowing) {
        batch.delete(followRef)
        batch.update(
          targetProfileRef,
          mapOf(
            "followersCount" to FieldValue.increment(-1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.update(
          myProfileRef,
          mapOf(
            "followingCount" to FieldValue.increment(-1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.set(
          followRef,
          mapOf(
            "userId" to uid,
            "targetUserId" to targetProfile.userId,
            "targetUsername" to targetProfile.username,
            "targetDisplayName" to targetProfile.displayName,
            "targetAvatarUrl" to targetProfile.avatarUrl,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.update(
          targetProfileRef,
          mapOf(
            "followersCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.update(
          myProfileRef,
          mapOf(
            "followingCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()

      if (!currentlyFollowing) {
        sendNotification(
          recipientId = targetProfile.userId,
          actorProfile = actorProfile,
          type = "follow",
          postId = "",
          previewText = "started following you",
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, followRef.path, auth)
      throw e
    }
  }

  private suspend fun sendNotification(
    recipientId: String,
    actorProfile: UserProfile,
    type: String,
    postId: String,
    previewText: String,
  ) {
    val uid = requireUserId()
    val notifRef = db.collection("users").document(recipientId).collection("notifications").document()
    try {
      notifRef.set(
        mapOf(
          "recipientId" to recipientId,
          "actorId" to uid,
          "actorUsername" to actorProfile.username.ifBlank { "aura_user" },
          "actorAvatarUrl" to actorProfile.avatarUrl,
          "type" to type,
          "postId" to postId,
          "previewText" to previewText.take(120),
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (_: Exception) {
      // Non-fatal notification failure
    }
  }

  suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val ref = db.collection("users").document(uid).collection("notifications").document(notificationId)
    try {
      ref.update("isRead", true).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path, auth)
      throw e
    }
  }

  suspend fun sendDirectMessage(
    myProfile: UserProfile,
    otherProfile: UserProfile,
    text: String,
    sharedPostId: String = "",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val trimmed = text.trim()
    require(trimmed.isNotEmpty()) { "Message cannot be empty" }
    val participants = listOf(uid, otherProfile.userId).sorted()
    val conversationId = "${participants[0]}_${participants[1]}"
    val convRef = db.collection("conversations").document(conversationId)
    val msgRef = convRef.collection("messages").document()
    val userAIsMe = participants[0] == uid

    try {
      val existingSnap = convRef.get().await()
      val batch = db.batch()
      if (!existingSnap.exists()) {
        batch.set(
          convRef,
          mapOf(
            "participantIds" to participants,
            "userAId" to if (userAIsMe) uid else otherProfile.userId,
            "userAName" to if (userAIsMe) myProfile.username else otherProfile.username,
            "userAAvatar" to if (userAIsMe) myProfile.avatarUrl else otherProfile.avatarUrl,
            "userBId" to if (userAIsMe) otherProfile.userId else uid,
            "userBName" to if (userAIsMe) otherProfile.username else myProfile.username,
            "userBAvatar" to if (userAIsMe) otherProfile.avatarUrl else myProfile.avatarUrl,
            "lastMessage" to trimmed.take(1000),
            "lastSenderId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.update(
          convRef,
          mapOf(
            "lastMessage" to trimmed.take(1000),
            "lastSenderId" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.set(
        msgRef,
        mapOf(
          "senderId" to uid,
          "conversationId" to conversationId,
          "participantIds" to participants,
          "text" to trimmed.take(1000),
          "sharedPostId" to sharedPostId,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      conversationId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, msgRef.path, auth)
      throw e
    }
  }
}
