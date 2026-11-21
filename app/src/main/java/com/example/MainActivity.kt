package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.data.repository.AuraRepository
import com.example.ui.AuraViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CommentsBottomSheet
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PostDetailDialog
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.SharePostSheet
import com.example.ui.screens.StoryViewerOverlay
import com.example.ui.theme.AuraTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.ui.screens.SettingsScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_EXPLORE = "explore"
private const val ROUTE_CREATE = "create"
private const val ROUTE_REELS = "reels"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_INBOX = "inbox"
private const val ROUTE_USER_PROFILE = "user_profile"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { AuraRoot() }
  }
}

@Composable
fun AuraRoot(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuraTheme {
      AuthScreen(onAuthSuccess = {})
    }
  } else {
    val uid = user.uid
    key(uid) {
      val viewModel: AuraViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                runCatching {
                  db.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(
                      PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024 * 1024)
                        .build()
                    )
                  }
                }
                val draft = com.example.auth.consumeAuthProfileDraft(app)
                val firebaseUser = Firebase.auth.currentUser
                if (firebaseUser != null && !draft.phoneNumber.isNullOrBlank()) {
                  com.example.auth.upsertSavedAccount(
                    context = app,
                    user = firebaseUser,
                    username = draft.username,
                    displayName = draft.displayName,
                    phoneOverride = draft.phoneNumber,
                  )
                }
                AuraViewModel(
                  repository = AuraRepository(db, auth),
                  currentUserId = uid,
                  preferredDisplayName = draft.displayName,
                  preferredUsername = draft.username,
                )
              }
            },
        )
      AuraAppShell(currentUserId = uid, viewModel = viewModel)
    }
  }
}

private data class AuraBottomTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

@Composable
private fun AuraAppShell(currentUserId: String, viewModel: AuraViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val activeComments by viewModel.activeComments.collectAsStateWithLifecycle()
  val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val isDarkTheme =
    when (state.userPrivate?.themeMode) {
      "dark" -> true
      "light" -> false
      else -> state.userPrivate?.darkModeEnabled ?: isSystemInDarkTheme()
    }
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  var activeStoryOwnerId by remember { mutableStateOf<String?>(null) }
  var commentTargetPost by remember { mutableStateOf<Post?>(null) }
  var detailTargetPost by remember { mutableStateOf<Post?>(null) }
  var shareTargetPost by remember { mutableStateOf<Post?>(null) }
  var initialCreateMode by remember { mutableStateOf("Post") }
  var initialReelId by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(statusBanner) {
    statusBanner?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBanner()
    }
  }

  LaunchedEffect(currentUserId, state.myProfile) {
    val firebaseUser = Firebase.auth.currentUser
    val prof = state.myProfile
    if (firebaseUser != null && prof != null) {
      withContext(Dispatchers.IO) {
        com.example.auth.upsertSavedAccount(
          context = context,
          user = firebaseUser,
          username = prof.username,
          displayName = prof.displayName,
          avatarUrl = prof.avatarUrl,
        )
      }
    }
  }

  val bottomTabs = remember {
    listOf(
      AuraBottomTab(ROUTE_HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
      AuraBottomTab(ROUTE_EXPLORE, "Explore", Icons.Filled.Search, Icons.Outlined.Search),
      AuraBottomTab(ROUTE_CREATE, "Create", Icons.Filled.AddBox, Icons.Outlined.AddBox),
      AuraBottomTab(ROUTE_REELS, "Reels", Icons.Filled.Movie, Icons.Outlined.Movie),
      AuraBottomTab(ROUTE_PROFILE, "Profile", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    )
  }

  val showBottomBar =
    currentRoute in setOf(ROUTE_HOME, ROUTE_EXPLORE, ROUTE_CREATE, ROUTE_REELS, ROUTE_PROFILE) &&
      activeStoryOwnerId == null

  AuraTheme(darkTheme = isDarkTheme) {
    Box(modifier = Modifier.fillMaxSize()) {
      Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
          if (showBottomBar) {
            NavigationBar(
              containerColor =
                if (currentRoute == ROUTE_REELS) androidx.compose.ui.graphics.Color.Black
                else MaterialTheme.colorScheme.background,
              tonalElevation = 0.dp,
            ) {
              bottomTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint =
                  if (currentRoute == ROUTE_REELS) androidx.compose.ui.graphics.Color.White
                  else MaterialTheme.colorScheme.onBackground
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    if (tab.route == ROUTE_CREATE) {
                      initialCreateMode = "Post"
                    }
                    navController.navigate(tab.route) {
                      popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                      }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                      modifier = Modifier.size(26.dp),
                    )
                  },
                  label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                  alwaysShowLabel = false,
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = tint,
                      unselectedIconColor = tint.copy(alpha = 0.65f),
                      indicatorColor = tint.copy(alpha = 0.12f),
                    ),
                  modifier = Modifier.testTag("nav_${tab.route}"),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = ROUTE_HOME,
          enterTransition = { EnterTransition.None },
          exitTransition = { ExitTransition.None },
          popEnterTransition = { EnterTransition.None },
          popExitTransition = { ExitTransition.None },
          modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
          composable(ROUTE_HOME) {
            HomeFeedScreen(
              state = state,
              currentUserId = currentUserId,
              onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
              onOpenMessages = { navController.navigate(ROUTE_INBOX) },
              onOpenStory = { ownerId ->
                viewModel.markStorySeen(ownerId)
                activeStoryOwnerId = ownerId
              },
              onAddStoryClick = {
                initialCreateMode = "Story"
                navController.navigate(ROUTE_CREATE)
              },
              onCreatePostClick = {
                initialCreateMode = "Post"
                navController.navigate(ROUTE_CREATE)
              },
              onOpenProfile = { uid ->
                if (uid == currentUserId) {
                  navController.navigate(ROUTE_PROFILE)
                } else {
                  navController.navigate("$ROUTE_USER_PROFILE/$uid")
                }
              },
              onToggleLike = { post, force -> viewModel.toggleLike(post, force) },
              onToggleSave = { post -> viewModel.toggleSave(post) },
              onOpenComments = { post ->
                commentTargetPost = post
                viewModel.openCommentsForPost(post.id)
              },
              onSharePost = { post -> shareTargetPost = post },
              onDeletePost = { post -> viewModel.deletePost(post.id) },
              onToggleArchivePost = { post -> viewModel.toggleArchivePost(post) },
              onMuteUser = { targetUid ->
                val list = state.userPrivate?.mutedUserIds.orEmpty()
                viewModel.toggleUserInSettingList("mutedUserIds", list, targetUid, "Account muted", "Account unmuted")
              },
              onBlockUser = { targetUid ->
                val list = state.userPrivate?.blockedUserIds.orEmpty()
                viewModel.toggleUserInSettingList("blockedUserIds", list, targetUid, "Account blocked", "Account unblocked")
              },
              onOpenReel = { post ->
                initialReelId = post.id
                navController.navigate(ROUTE_REELS)
              },
            )
          }

          composable(ROUTE_EXPLORE) {
            ExploreScreen(
              state = state,
              currentUserId = currentUserId,
              onOpenProfile = { uid ->
                if (uid == currentUserId) navController.navigate(ROUTE_PROFILE)
                else navController.navigate("$ROUTE_USER_PROFILE/$uid")
              },
              onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
              onOpenPostDetail = { post -> detailTargetPost = post },
            )
          }

          composable(ROUTE_CREATE) {
            val highQuality =
              !(state.userPrivate?.dataSaverEnabled ?: false) &&
                (state.userPrivate?.uploadHighQuality ?: true)
            CreatePostScreen(
              initialMode = initialCreateMode,
              highQualityUpload = highQuality,
              onPublishPost = { caption, mediaType, img, filter, loc, audio, onDone ->
                viewModel.publishPost(caption, mediaType, img, filter, loc, audio, onDone)
              },
              onPublishStory = { img, cap, bg, onDone ->
                viewModel.publishStory(img, cap, bg, onDone)
              },
              onPublishedNavigateHome = {
                navController.navigate(ROUTE_HOME) {
                  popUpTo(ROUTE_HOME) { inclusive = false }
                  launchSingleTop = true
                }
              },
            )
          }

          composable(ROUTE_REELS) {
            ReelsScreen(
              state = state,
              currentUserId = currentUserId,
              initialReelId = initialReelId,
              onOpenProfile = { uid ->
                if (uid == currentUserId) navController.navigate(ROUTE_PROFILE)
                else navController.navigate("$ROUTE_USER_PROFILE/$uid")
              },
              onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
              onToggleLike = { post, force -> viewModel.toggleLike(post, force) },
              onToggleSave = { post -> viewModel.toggleSave(post) },
              onOpenComments = { post ->
                commentTargetPost = post
                viewModel.openCommentsForPost(post.id)
              },
              onSharePost = { post -> shareTargetPost = post },
            )
          }

          composable(ROUTE_PROFILE) {
            ProfileScreen(
              profileUserId = currentUserId,
              currentUserId = currentUserId,
              state = state,
              isDarkTheme = isDarkTheme,
              onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
              onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
              onMessageUser = { },
              onUpdateProfile = { u, d, b, w, a -> viewModel.updateMyProfile(u, d, b, w, a) },
              onToggleDarkMode = { enabled -> viewModel.toggleDarkMode(enabled) },
              onOpenPostDetail = { post -> detailTargetPost = post },
              onOpenStory = { ownerId ->
                viewModel.markStorySeen(ownerId)
                activeStoryOwnerId = ownerId
              },
              onSignOut = {
                val keep = com.example.auth.isSaveLoginEnabled(context)
                if (!keep) com.example.auth.removeSavedAccount(context, currentUserId)
                signOutUser(credentialManager = credentialManager, scope = scope, clearSavedCredentials = !keep)
              },
            )
          }

          composable(ROUTE_SETTINGS) {
            SettingsScreen(
              state = state,
              currentUserId = currentUserId,
              sessionStartMs = viewModel.sessionStartMs,
              onBack = { navController.popBackStack() },
              onOpenPostDetail = { post -> detailTargetPost = post },
              onToggleSave = { post -> viewModel.toggleSave(post) },
              onToggleLike = { post, force -> viewModel.toggleLike(post, force) },
              onToggleArchivePost = { post -> viewModel.toggleArchivePost(post) },
              onDeleteStory = { storyId -> viewModel.deleteStory(storyId) },
              onUpdateSettings = { updates, toast -> viewModel.updateSettings(updates, toast) },
              onSetThemeMode = { mode -> viewModel.setThemeMode(mode) },
              onSetAccountPrivacy = { isPrivate -> viewModel.setAccountPrivacy(isPrivate) },
              onToggleUserInList = { field, list, uid, addMsg, remMsg ->
                viewModel.toggleUserInSettingList(field, list, uid, addMsg, remMsg)
              },
              onAddHiddenWord = { w -> viewModel.addHiddenWord(w) },
              onRemoveHiddenWord = { w -> viewModel.removeHiddenWord(w) },
              onUpdateProfile = { u, d, b, w, a -> viewModel.updateMyProfile(u, d, b, w, a) },
              onSignOut = {
                val keep = com.example.auth.isSaveLoginEnabled(context)
                if (!keep) com.example.auth.removeSavedAccount(context, currentUserId)
                signOutUser(credentialManager = credentialManager, scope = scope, clearSavedCredentials = !keep)
              },
            )
          }

          composable(
            route = "$ROUTE_USER_PROFILE/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
          ) { entry ->
            val targetUid = entry.arguments?.getString("userId") ?: return@composable
            ProfileScreen(
              profileUserId = targetUid,
              currentUserId = currentUserId,
              state = state,
              isDarkTheme = isDarkTheme,
              onBack = { navController.popBackStack() },
              onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
              onMessageUser = { userProfile ->
                val convId = listOf(currentUserId, userProfile.userId).sorted().joinToString("_")
                viewModel.openConversation(convId)
                navController.navigate("$ROUTE_CHAT/${userProfile.userId}")
              },
              onUpdateProfile = { _, _, _, _, _ -> },
              onToggleDarkMode = {},
              onToggleUserSetting = { field, list, uid, addMsg, remMsg ->
                viewModel.toggleUserInSettingList(field, list, uid, addMsg, remMsg)
              },
              onOpenPostDetail = { post -> detailTargetPost = post },
              onOpenStory = { ownerId ->
                viewModel.markStorySeen(ownerId)
                activeStoryOwnerId = ownerId
              },
              onSignOut = {},
            )
          }

          composable(ROUTE_NOTIFICATIONS) {
            NotificationsScreen(
              state = state,
              onBack = { navController.popBackStack() },
              onOpenProfile = { uid ->
                if (uid == currentUserId) navController.navigate(ROUTE_PROFILE)
                else navController.navigate("$ROUTE_USER_PROFILE/$uid")
              },
              onToggleFollow = { profile -> viewModel.toggleFollow(profile) },
              onMarkRead = { notifId -> viewModel.markNotificationRead(notifId) },
            )
          }

          composable(ROUTE_INBOX) {
            InboxScreen(
              state = state,
              currentUserId = currentUserId,
              onBack = { navController.popBackStack() },
              onOpenChat = { recipient ->
                val convId = listOf(currentUserId, recipient.userId).sorted().joinToString("_")
                viewModel.openConversation(convId)
                navController.navigate("$ROUTE_CHAT/${recipient.userId}")
              },
            )
          }

          composable(
            route = "$ROUTE_CHAT/{recipientId}",
            arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
          ) { entry ->
            val recipientId = entry.arguments?.getString("recipientId") ?: return@composable
            val recipient =
              state.profilesById[recipientId]
                ?: UserProfile(userId = recipientId, username = "aura_user", displayName = "Aura User")

            ChatScreen(
              recipient = recipient,
              currentUserId = currentUserId,
              messagesState = activeMessages,
              showActivityStatus = state.userPrivate?.showActivityStatus ?: true,
              onBack = { navController.popBackStack() },
              onOpenProfile = { uid -> navController.navigate("$ROUTE_USER_PROFILE/$uid") },
              onSendMessage = { text -> viewModel.sendMessage(recipient, text) },
            )
          }
        }
      }

      // Full-screen Story Viewer Overlay
      activeStoryOwnerId?.let { ownerId ->
        val stories = state.storiesByUser[ownerId].orEmpty()
        StoryViewerOverlay(
          stories = stories,
          onClose = { activeStoryOwnerId = null },
          onReplyToStory = { story, text ->
            val targetProfile =
              state.profilesById[story.userId]
                ?: UserProfile(
                  userId = story.userId,
                  username = story.authorUsername,
                  displayName = story.authorUsername,
                  avatarUrl = story.authorAvatarUrl,
                )
            viewModel.sendMessage(targetProfile, "Replied to your story: $text")
          },
          onOpenAuthorProfile = { uid ->
            activeStoryOwnerId = null
            if (uid == currentUserId) navController.navigate(ROUTE_PROFILE)
            else navController.navigate("$ROUTE_USER_PROFILE/$uid")
          },
        )
      }

      // Comments Bottom Sheet
      commentTargetPost?.let { post ->
        val prefs = state.userPrivate
        val activeHiddenWords =
          if (prefs?.hideOffensiveComments == false) emptyList() else prefs?.hiddenWords.orEmpty()
        CommentsBottomSheet(
          post = post,
          commentsState = activeComments,
          blockedUserIds = prefs?.blockedUserIds?.toSet().orEmpty(),
          hiddenWords = activeHiddenWords,
          onDismiss = {
            commentTargetPost = null
            viewModel.openCommentsForPost(null)
          },
          onSubmitComment = { text -> viewModel.addComment(post, text) },
          onOpenProfile = { uid ->
            if (uid == currentUserId) navController.navigate(ROUTE_PROFILE)
            else navController.navigate("$ROUTE_USER_PROFILE/$uid")
          },
        )
      }

      // Post Detail Dialog
      detailTargetPost?.let { post ->
        PostDetailDialog(
          post = post,
          isLiked = state.likedPostIds.contains(post.id),
          isSaved = state.savedPostIds.contains(post.id),
          isOwnPost = post.userId == currentUserId,
          hideLikeCounts = state.userPrivate?.hideLikeCounts == true && post.userId != currentUserId,
          onDismiss = { detailTargetPost = null },
          onAuthorClick = {
            if (post.userId == currentUserId) navController.navigate(ROUTE_PROFILE)
            else navController.navigate("$ROUTE_USER_PROFILE/${post.userId}")
          },
          onToggleLike = { force -> viewModel.toggleLike(post, force) },
          onToggleSave = { viewModel.toggleSave(post) },
          onOpenComments = {
            commentTargetPost = post
            viewModel.openCommentsForPost(post.id)
          },
          onShare = { shareTargetPost = post },
          onDelete = { viewModel.deletePost(post.id) },
          onToggleArchive = { viewModel.toggleArchivePost(post) },
          onOpenReel = {
            initialReelId = post.id
            navController.navigate(ROUTE_REELS)
          },
        )
      }

      // Share Sheet
      shareTargetPost?.let { post ->
        SharePostSheet(
          post = post,
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          onDismiss = { shareTargetPost = null },
          onSendToUser = { recipient, sharedPost ->
            viewModel.sendMessage(
              otherProfile = recipient,
              text = "Shared @${sharedPost.authorUsername}'s post: ${sharedPost.caption.take(80)}",
              sharedPostId = sharedPost.id,
            )
          },
        )
      }
    }
  }
}
