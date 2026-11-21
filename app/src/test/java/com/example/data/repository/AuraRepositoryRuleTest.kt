package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuraRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureUserInitialized_createsProfileAndIsolatesPrivatePii() = runBlocking {
    val alice = signInTestUser("alice_${UUID.randomUUID()}@test.com")
    val aliceRepo = AuraRepository(firestore, auth)

    val initResult = withTimeout(TIMEOUT_MS) { aliceRepo.ensureUserInitialized(alice) }
    assertTrue(initResult.isSuccess)

    val profile = withTimeout(FLOW_TIMEOUT_MS) { aliceRepo.observeMyProfile().first { it != null } }
    assertEquals(alice.uid, profile?.userId)

    // Alice can update Instagram Settings & Account Privacy
    val settingsResult =
      withTimeout(TIMEOUT_MS) {
        aliceRepo.updateUserSettings(
          mapOf(
            "themeMode" to "dark",
            "hideLikeCounts" to true,
            "hiddenWords" to listOf("spam"),
            "dailyTimeLimitMinutes" to 30L,
          )
        )
      }
    assertTrue(
      "updateUserSettings failed: ${settingsResult.exceptionOrNull()?.stackTraceToString()}",
      settingsResult.isSuccess,
    )
    val updatedPrivate =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeUserPrivate().first { it?.themeMode == "dark" && it.hideLikeCounts }
      }!!
    assertEquals(listOf("spam"), updatedPrivate.hiddenWords)
    assertEquals(30L, updatedPrivate.dailyTimeLimitMinutes)

    val privacyResult = withTimeout(TIMEOUT_MS) { aliceRepo.setAccountPrivacy(isPublic = false) }
    assertTrue(
      "setAccountPrivacy failed: ${privacyResult.exceptionOrNull()?.stackTraceToString()}",
      privacyResult.isSuccess,
    )

    // Bob cannot read Alice's private PII doc
    signInTestUser("bob_${UUID.randomUUID()}@test.com")
    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("users").document(alice.uid).get().await() }
      fail("Expected PERMISSION_DENIED when reading another user's private document")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }
  }

  @Test
  fun createPost_like_comment_and_crossUserDeleteRejected() = runBlocking {
    val alice = signInTestUser("alice_post_${UUID.randomUUID()}@test.com")
    val repo = AuraRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) { repo.ensureUserInitialized(alice).getOrThrow() }
    val aliceProfile =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeMyProfile().first { it != null } }!!

    val postId =
      withTimeout(TIMEOUT_MS) {
        repo.createPost(
          authorProfile = aliceProfile,
          caption = "Golden hour in the peaks #travel",
          mediaType = "photo",
          imageData = "drawable:img_post_mountain_golden",
          filterName = "Original",
          location = "Swiss Alps",
          audioTitle = "",
          tags = listOf("travel", "nature"),
        ).getOrThrow()
      }

    val publicPosts =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePublicPosts().first { posts -> posts.any { it.id == postId } }
      }
    val createdPost = publicPosts.first { it.id == postId }

    // Bob signs in, likes, and comments on Alice's post
    val bob = signInTestUser("bob_post_${UUID.randomUUID()}@test.com")
    withTimeout(TIMEOUT_MS) { repo.ensureUserInitialized(bob).getOrThrow() }
    val bobProfile =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeMyProfile().first { it != null } }!!

    withTimeout(TIMEOUT_MS) {
      repo.toggleLike(createdPost, currentlyLiked = false, actorProfile = bobProfile).getOrThrow()
    }
    withTimeout(TIMEOUT_MS) {
      repo.addComment(createdPost, actorProfile = bobProfile, text = "Stunning shot!").getOrThrow()
    }

    val comments =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeComments(postId).first { it.isNotEmpty() }
      }
    assertEquals("Stunning shot!", comments.first().text)

    // Bob cannot delete Alice's post
    val deleteResult = withTimeout(TIMEOUT_MS) { repo.deletePost(postId) }
    assertTrue(deleteResult.isFailure)
    val fsEx = deleteResult.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  @Test
  fun toggleFollow_and_directMessage_succeedWithParticipantIsolation() = runBlocking {
    val alice = signInTestUser("alice_dm_${UUID.randomUUID()}@test.com")
    val repo = AuraRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) { repo.ensureUserInitialized(alice).getOrThrow() }
    val aliceProfile =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeMyProfile().first { it != null } }!!

    val bob = signInTestUser("bob_dm_${UUID.randomUUID()}@test.com")
    withTimeout(TIMEOUT_MS) { repo.ensureUserInitialized(bob).getOrThrow() }
    val bobProfile =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeMyProfile().first { it != null } }!!

    // Bob follows Alice
    withTimeout(TIMEOUT_MS) {
      repo.toggleFollow(aliceProfile, currentlyFollowing = false, actorProfile = bobProfile)
        .getOrThrow()
    }
    val following =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeFollowing().first { list -> list.any { it.targetUserId == alice.uid } }
      }
    assertEquals(1, following.size)

    // Bob sends DM to Alice
    val convId =
      withTimeout(TIMEOUT_MS) {
        repo.sendDirectMessage(bobProfile, aliceProfile, "Salam! Nice feed!").getOrThrow()
      }
    val messages =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMessages(convId).first { it.isNotEmpty() }
      }
    assertEquals("Salam! Nice feed!", messages.first().text)

    // Charlie cannot read Alice-Bob conversation
    signInTestUser("charlie_dm_${UUID.randomUUID()}@test.com")
    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("conversations").document(convId).get().await() }
      fail("Expected PERMISSION_DENIED for non-participant")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }
  }

  @Test
  fun observePublicPosts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = AuraRepository(firestore, auth)
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePublicPosts().first() }
      fail("Expected failure when unauthenticated")
    } catch (e: Exception) {
      // requireUserId() throws IllegalStateException or Firestore throws PERMISSION_DENIED
      assertTrue(e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }

  private companion object {
    const val TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
