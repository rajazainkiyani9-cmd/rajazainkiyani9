package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.rememberCoroutineScope
import com.example.auth.getLinkedProviders
import com.example.auth.getSavedAccounts
import com.example.auth.isSaveLoginEnabled
import com.example.auth.linkSocialOAuthProvider
import com.example.auth.removeSavedAccount
import com.example.auth.setSaveLoginEnabled
import com.example.auth.upsertSavedAccount
import com.example.data.model.Post
import com.example.data.model.UserPrivate
import com.example.data.model.UserProfile
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.components.AuraImage
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
internal fun SettingsSubPageRouter(
  page: SettingsPage,
  state: AuraHomeUiState,
  prefs: UserPrivate,
  profile: UserProfile,
  currentUserId: String,
  sessionStartMs: Long,
  onBack: () -> Unit,
  onOpenPostDetail: (Post) -> Unit,
  onToggleSave: (Post) -> Unit,
  onToggleLike: (Post, Boolean) -> Unit,
  onToggleArchivePost: (Post) -> Unit,
  onDeleteStory: (String) -> Unit,
  onUpdateSettings: (Map<String, Any>, String?) -> Unit,
  onSetThemeMode: (String) -> Unit,
  onSetAccountPrivacy: (Boolean) -> Unit,
  onToggleUserInList: (String, List<String>, String, String, String) -> Unit,
  onAddHiddenWord: (String) -> Unit,
  onRemoveHiddenWord: (String) -> Unit,
  onUpdateProfile: (String, String, String, String, String) -> Unit,
  onRequestLogout: () -> Unit,
) {
  val title =
    when (page) {
      SettingsPage.ACCOUNTS_CENTER -> "Accounts Center"
      SettingsPage.SAVED -> "Saved"
      SettingsPage.ARCHIVE -> "Archive"
      SettingsPage.YOUR_ACTIVITY -> "Your activity"
      SettingsPage.NOTIFICATIONS -> "Notifications"
      SettingsPage.TIME_MANAGEMENT -> "Time management"
      SettingsPage.FAVORITES -> "Favorites"
      SettingsPage.MUTED -> "Muted accounts"
      SettingsPage.CONTENT_PREFS -> "Content preferences"
      SettingsPage.LIKE_COUNTS -> "Like and share counts"
      SettingsPage.ACCOUNT_PRIVACY -> "Account privacy"
      SettingsPage.CLOSE_FRIENDS -> "Close Friends"
      SettingsPage.BLOCKED -> "Blocked accounts"
      SettingsPage.HIDE_STORY -> "Hide story and live"
      SettingsPage.MESSAGES_REPLIES -> "Messages and story replies"
      SettingsPage.TAGS_MENTIONS -> "Tags and mentions"
      SettingsPage.COMMENTS_CONTROLS -> "Comments"
      SettingsPage.SHARING_REUSE -> "Sharing and reuse"
      SettingsPage.RESTRICTED -> "Restricted accounts"
      SettingsPage.HIDDEN_WORDS -> "Hidden Words"
      SettingsPage.APPEARANCE -> "Appearance"
      SettingsPage.ARCHIVING_DOWNLOADS -> "Archiving and downloading"
      SettingsPage.ACCESSIBILITY -> "Accessibility"
      SettingsPage.LANGUAGE -> "Language"
      SettingsPage.DATA_USAGE -> "Data usage and media quality"
      SettingsPage.ACCOUNT_STATUS -> "Account status"
      SettingsPage.HELP -> "Help"
      SettingsPage.ABOUT -> "About"
      SettingsPage.MAIN -> "Settings and activity"
    }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("settings_subpage_back")) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Spacer(modifier = Modifier.width(6.dp))
      Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()

    when (page) {
      SettingsPage.ACCOUNTS_CENTER ->
        AccountsCenterSubScreen(prefs, profile, onUpdateProfile, onRequestLogout)

      SettingsPage.SAVED ->
        SavedSettingsSubScreen(state, onOpenPostDetail, onToggleSave)

      SettingsPage.ARCHIVE ->
        ArchiveSubScreen(state, onOpenPostDetail, onToggleArchivePost, onDeleteStory)

      SettingsPage.YOUR_ACTIVITY ->
        YourActivitySubScreen(state, currentUserId, sessionStartMs, onOpenPostDetail, onToggleLike)

      SettingsPage.NOTIFICATIONS ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Pause all notifications",
            subtitle = "Temporarily mute push and activity alerts",
            checked = prefs.notificationsMuted,
            onCheckedChange = { onUpdateSettings(mapOf("notificationsMuted" to it), null) },
          )
          HorizontalDivider()
          SettingsSwitchRow(
            title = "Likes",
            subtitle = "Notify when someone likes your post or reel",
            checked = prefs.notifyLikes,
            onCheckedChange = { onUpdateSettings(mapOf("notifyLikes" to it), null) },
          )
          SettingsSwitchRow(
            title = "Comments",
            subtitle = "Notify when someone comments on your content",
            checked = prefs.notifyComments,
            onCheckedChange = { onUpdateSettings(mapOf("notifyComments" to it), null) },
          )
          SettingsSwitchRow(
            title = "Following and followers",
            subtitle = "Notify when new people follow you",
            checked = prefs.notifyFollows,
            onCheckedChange = { onUpdateSettings(mapOf("notifyFollows" to it), null) },
          )
          SettingsSwitchRow(
            title = "Messages",
            subtitle = "Notify on new direct messages and story replies",
            checked = prefs.notifyMessages,
            onCheckedChange = { onUpdateSettings(mapOf("notifyMessages" to it), null) },
          )
        }

      SettingsPage.TIME_MANAGEMENT ->
        TimeManagementSubScreen(prefs, sessionStartMs, onUpdateSettings)

      SettingsPage.FAVORITES ->
        UserListManagerSubScreen(
          subtitle = "Posts from Favorites appear with a star badge and dedicated feed filter.",
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          selectedIds = prefs.favoriteUserIds,
          actionAddLabel = "Add",
          actionRemoveLabel = "Remove",
          onToggle = { uid ->
            onToggleUserInList("favoriteUserIds", prefs.favoriteUserIds, uid, "Added to Favorites", "Removed from Favorites")
          },
        )

      SettingsPage.MUTED ->
        UserListManagerSubScreen(
          subtitle = "Hide posts and stories from muted accounts without unfollowing them.",
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          selectedIds = prefs.mutedUserIds,
          actionAddLabel = "Mute",
          actionRemoveLabel = "Unmute",
          onToggle = { uid ->
            onToggleUserInList("mutedUserIds", prefs.mutedUserIds, uid, "Account muted", "Account unmuted")
          },
        )

      SettingsPage.CONTENT_PREFS ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          Text(
            text = "Sensitive content",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
          )
          listOf("More", "Standard", "Less").forEach { level ->
            RadioSettingRow(
              title = level,
              subtitle =
                when (level) {
                  "More" -> "See more content that may be sensitive"
                  "Less" -> "Filter out potentially sensitive posts in Explore"
                  else -> "Balanced default feed moderation"
                },
              selected = prefs.sensitiveContentLevel == level,
              onClick = { onUpdateSettings(mapOf("sensitiveContentLevel" to level), "Content preference updated") },
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          SettingsSwitchRow(
            title = "Autoplay Reels",
            subtitle = "Animate reels automatically when visible",
            checked = prefs.autoplayReels,
            onCheckedChange = { onUpdateSettings(mapOf("autoplayReels" to it), null) },
          )
        }

      SettingsPage.LIKE_COUNTS ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Hide like and share counts",
            subtitle = "You won't see the total number of likes on posts and reels from other accounts.",
            checked = prefs.hideLikeCounts,
            testTag = "hide_likes_switch",
            onCheckedChange = { onUpdateSettings(mapOf("hideLikeCounts" to it), null) },
          )
        }

      SettingsPage.ACCOUNT_PRIVACY ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Private account",
            subtitle =
              "When your account is public, anyone on Aura can discover your profile and posts. When private, your profile is hidden from public discovery.",
            checked = !profile.isPublic,
            testTag = "private_account_switch",
            onCheckedChange = { onSetAccountPrivacy(it) },
          )
        }

      SettingsPage.CLOSE_FRIENDS ->
        UserListManagerSubScreen(
          subtitle = "Share exclusive stories with your Close Friends circle.",
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          selectedIds = prefs.closeFriendIds,
          actionAddLabel = "Add",
          actionRemoveLabel = "Remove",
          onToggle = { uid ->
            onToggleUserInList("closeFriendIds", prefs.closeFriendIds, uid, "Added to Close Friends", "Removed from Close Friends")
          },
        )

      SettingsPage.BLOCKED ->
        UserListManagerSubScreen(
          subtitle = "Blocked accounts cannot appear in your Feed, Reels, Stories, Comments, or Messages.",
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          selectedIds = prefs.blockedUserIds,
          actionAddLabel = "Block",
          actionRemoveLabel = "Unblock",
          onToggle = { uid ->
            onToggleUserInList("blockedUserIds", prefs.blockedUserIds, uid, "Account blocked", "Account unblocked")
          },
        )

      SettingsPage.HIDE_STORY ->
        UserListManagerSubScreen(
          subtitle = "Hide all stories you add from selected people.",
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          selectedIds = prefs.hideStoryFromIds,
          actionAddLabel = "Hide",
          actionRemoveLabel = "Unhide",
          onToggle = { uid ->
            onToggleUserInList("hideStoryFromIds", prefs.hideStoryFromIds, uid, "Story hidden for user", "Story unhidden for user")
          },
        )

      SettingsPage.MESSAGES_REPLIES ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          Text(
            text = "Who can message you",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
          )
          listOf("Everyone", "People you follow", "No one").forEach { option ->
            RadioSettingRow(
              title = option,
              selected = prefs.allowMessagesFrom == option,
              onClick = { onUpdateSettings(mapOf("allowMessagesFrom" to option), "Messaging preference saved") },
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          SettingsSwitchRow(
            title = "Show activity status",
            subtitle = "Allow accounts you follow and message to see when you're active",
            checked = prefs.showActivityStatus,
            onCheckedChange = { onUpdateSettings(mapOf("showActivityStatus" to it), null) },
          )
          SettingsSwitchRow(
            title = "Show read receipts",
            subtitle = "Let people see when you've seen their messages in chats",
            checked = prefs.showReadReceipts,
            onCheckedChange = { onUpdateSettings(mapOf("showReadReceipts" to it), null) },
          )
        }

      SettingsPage.TAGS_MENTIONS ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          Text(
            text = "Allow @mentions and tags from",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
          )
          listOf("Everyone", "People you follow", "No one").forEach { option ->
            RadioSettingRow(
              title = option,
              selected = prefs.allowTagsFrom == option,
              onClick = { onUpdateSettings(mapOf("allowTagsFrom" to option), "Tags preference saved") },
            )
          }
        }

      SettingsPage.COMMENTS_CONTROLS ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          Text(
            text = "Allow comments from",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
          )
          listOf("Everyone", "People you follow", "No one").forEach { option ->
            RadioSettingRow(
              title = option,
              selected = prefs.allowCommentsFrom == option,
              onClick = { onUpdateSettings(mapOf("allowCommentsFrom" to option), "Comment controls saved") },
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          SettingsSwitchRow(
            title = "Filter offensive comments",
            subtitle = "Automatically hide comments containing words from your Hidden Words list",
            checked = prefs.hideOffensiveComments,
            onCheckedChange = { onUpdateSettings(mapOf("hideOffensiveComments" to it), null) },
          )
        }

      SettingsPage.SHARING_REUSE ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Allow sharing to Stories",
            subtitle = "Let others share your public posts or reply to your stories",
            checked = prefs.allowStorySharing,
            onCheckedChange = { onUpdateSettings(mapOf("allowStorySharing" to it), null) },
          )
          SettingsSwitchRow(
            title = "Allow post and reel sharing",
            subtitle = "Allow others to send your posts in DMs or share externally",
            checked = prefs.allowPostSharing,
            onCheckedChange = { onUpdateSettings(mapOf("allowPostSharing" to it), null) },
          )
        }

      SettingsPage.RESTRICTED ->
        UserListManagerSubScreen(
          subtitle = "Limit unwanted interactions from specific accounts without blocking them.",
          profiles = state.allProfiles,
          currentUserId = currentUserId,
          selectedIds = prefs.restrictedUserIds,
          actionAddLabel = "Restrict",
          actionRemoveLabel = "Unrestrict",
          onToggle = { uid ->
            onToggleUserInList("restrictedUserIds", prefs.restrictedUserIds, uid, "Account restricted", "Restriction removed")
          },
        )

      SettingsPage.HIDDEN_WORDS ->
        HiddenWordsSubScreen(prefs, onUpdateSettings, onAddHiddenWord, onRemoveHiddenWord)

      SettingsPage.APPEARANCE ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          listOf(
            Triple("system", "System default", "Follow your device Light/Dark theme"),
            Triple("light", "Light", "Crisp bright Instagram appearance"),
            Triple("dark", "Dark", "True OLED dark appearance"),
          ).forEach { (mode, label, desc) ->
            RadioSettingRow(
              title = label,
              subtitle = desc,
              selected = prefs.themeMode == mode,
              testTag = "theme_radio_$mode",
              onClick = { onSetThemeMode(mode) },
            )
          }
        }

      SettingsPage.ARCHIVING_DOWNLOADS ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Save story to Archive",
            subtitle = "Keep your stories in your private Archive tab automatically",
            checked = prefs.saveStoryToArchive,
            onCheckedChange = { onUpdateSettings(mapOf("saveStoryToArchive" to it), null) },
          )
          SettingsSwitchRow(
            title = "Save original photos",
            subtitle = "Keep high-resolution copies of your camera uploads",
            checked = prefs.saveToCameraRoll,
            onCheckedChange = { onUpdateSettings(mapOf("saveToCameraRoll" to it), null) },
          )
        }

      SettingsPage.ACCESSIBILITY ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Always show captions",
            subtitle = "Display expanded captions and audio titles on Reels",
            checked = prefs.alwaysShowCaptions,
            onCheckedChange = { onUpdateSettings(mapOf("alwaysShowCaptions" to it), null) },
          )
          SettingsSwitchRow(
            title = "Reduced motion",
            subtitle = "Minimize visual motion effects across Reels and transitions",
            checked = prefs.reducedMotion,
            onCheckedChange = { onUpdateSettings(mapOf("reducedMotion" to it), null) },
          )
        }

      SettingsPage.LANGUAGE ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          listOf("English", "Urdu (Roman)", "Hindi", "Arabic", "Spanish").forEach { lang ->
            RadioSettingRow(
              title = lang,
              selected = prefs.appLanguage == lang,
              onClick = { onUpdateSettings(mapOf("appLanguage" to lang), "Language set to $lang") },
            )
          }
        }

      SettingsPage.DATA_USAGE ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
          SettingsSwitchRow(
            title = "Data Saver",
            subtitle = "Use lower bandwidth compression when uploading photos and reels",
            checked = prefs.dataSaverEnabled,
            onCheckedChange = {
              onUpdateSettings(mapOf("dataSaverEnabled" to it, "uploadHighQuality" to !it), null)
            },
          )
          SettingsSwitchRow(
            title = "Upload at highest quality",
            subtitle = "Always upload sharpest photo clarity over mobile & Wi-Fi",
            checked = prefs.uploadHighQuality,
            onCheckedChange = { onUpdateSettings(mapOf("uploadHighQuality" to it), null) },
          )
        }

      SettingsPage.ACCOUNT_STATUS ->
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              AuraAvatar(profile.avatarUrl, profile.username, size = 50.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("@${profile.username}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                  text = if (profile.isPublic) "Public Account · Good Standing" else "Private Account · Good Standing",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          StatusCheckCard("Removed content", "Your account has not violated Community Guidelines.")
          StatusCheckCard("Features you can use", "Posts, Reels, Stories, Comments, and Direct Messages are all active.")
        }

      SettingsPage.HELP ->
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          StatusCheckCard("How do I archive a post?", "Tap the 3-dot menu on any of your posts and choose 'Move to Archive'. Restore it anytime from Settings > Archive.")
          StatusCheckCard("How do Hidden Words work?", "Go to Settings > Hidden Words and add words you want filtered out from comments and feed captions.")
          StatusCheckCard("How do I switch between Dark and Light mode?", "Open Settings > Appearance and choose System, Light, or Dark.")
        }

      SettingsPage.ABOUT ->
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          StatusCheckCard("Aura Social", "Version 1.0 · Live Cloud Sync enabled")
          StatusCheckCard("Signed-in Email", prefs.email.ifBlank { "Google Account" })
          StatusCheckCard("Account ID", profile.userId)
        }

      SettingsPage.MAIN -> Unit
    }
  }
}

@Composable
private fun AccountsCenterSubScreen(
  prefs: UserPrivate,
  profile: UserProfile,
  onUpdateProfile: (String, String, String, String, String) -> Unit,
  onRequestLogout: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentFirebaseUser = remember { Firebase.auth.currentUser }
  var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
  var username by remember(profile.username) { mutableStateOf(profile.username) }
  var bio by remember(profile.bio) { mutableStateOf(profile.bio) }
  var saveLogin by remember { mutableStateOf(isSaveLoginEnabled(context)) }
  var savedAccounts by remember { mutableStateOf(getSavedAccounts(context)) }
  var linkedProviders by remember { mutableStateOf(getLinkedProviders(currentFirebaseUser)) }
  var connectNotice by remember { mutableStateOf<String?>(null) }

  val linkedIdentifier =
    currentFirebaseUser?.phoneNumber?.takeIf { it.isNotBlank() }
      ?: prefs.email.takeIf { it.isNotBlank() }
      ?: currentFirebaseUser?.email?.takeIf { it.isNotBlank() }
      ?: "Account Linked"

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AuraAvatar(profile.avatarUrl, profile.username, size = 56.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(profile.displayName.ifBlank { profile.username }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(linkedIdentifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    Text("Connected accounts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    listOf(
      Triple("google.com", "Google", "Linked for 1-tap sign-in"),
      Triple("facebook.com", "Facebook", "Connect your Facebook account"),
      Triple("apple.com", "Apple ID", "Connect your Apple ID"),
    ).forEach { (providerId, label, hint) ->
      val isLinked = linkedProviders.contains(providerId)
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
              text = if (isLinked) "Connected" else hint,
              style = MaterialTheme.typography.bodySmall,
              color = if (isLinked) AuraBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isLinked) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = AuraBlue)
          } else {
            OutlinedButton(
              onClick = {
                connectNotice = null
                linkSocialOAuthProvider(
                  providerId = providerId,
                  providerLabel = label,
                  context = context,
                  scope = scope,
                  onLinked = {
                    linkedProviders = getLinkedProviders(Firebase.auth.currentUser)
                    connectNotice = "$label connected successfully!"
                  },
                  onError = { msg -> connectNotice = msg },
                )
              },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              modifier = Modifier.height(34.dp),
            ) {
              Text("Connect", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }

    if (connectNotice != null) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = connectNotice!!,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(10.dp),
        )
      }
    }

    SettingsSwitchRow(
      title = "Save login info",
      subtitle = "Stay signed in and keep 1-tap login ready on this device",
      checked = saveLogin,
      onCheckedChange = { enabled ->
        saveLogin = enabled
        setSaveLoginEnabled(context, enabled)
        if (enabled && currentFirebaseUser != null) {
          upsertSavedAccount(context, currentFirebaseUser, profile.username, profile.displayName, profile.avatarUrl)
        }
        savedAccounts = getSavedAccounts(context)
      },
    )

    if (savedAccounts.isNotEmpty()) {
      Text("Saved accounts on this device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      savedAccounts.forEach { acc ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AuraAvatar(acc.avatarUrl, acc.username, size = 38.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("@${acc.username.ifBlank { acc.displayName }}", fontWeight = FontWeight.SemiBold)
            Text(
              acc.phoneNumber.ifBlank { acc.email.ifBlank { acc.providerId } },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = {
            removeSavedAccount(context, acc.uid)
            savedAccounts = getSavedAccounts(context)
          }) {
            Text("Remove")
          }
        }
      }
    }

    HorizontalDivider()
    Text("Personal details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    OutlinedTextField(
      value = displayName,
      onValueChange = { displayName = it },
      label = { Text("Full Name") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = username,
      onValueChange = { username = it.lowercase().replace(" ", "_") },
      label = { Text("Username") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = bio,
      onValueChange = { bio = it },
      label = { Text("Bio") },
      maxLines = 3,
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = {
        val cleanUser = username.trim().let { if (it.length < 2) "aura_user" else it }
        onUpdateProfile(cleanUser, displayName.trim().ifBlank { cleanUser }, bio, profile.website, profile.avatarUrl)
      },
      modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      Text("Save Account Details")
    }
    OutlinedButton(
      onClick = onRequestLogout,
      modifier = Modifier.fillMaxWidth().height(48.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Text("Log out of @${profile.username}")
    }
  }
}

@Composable
private fun SavedSettingsSubScreen(
  state: AuraHomeUiState,
  onOpenPostDetail: (Post) -> Unit,
  onToggleSave: (Post) -> Unit,
) {
  if (state.savedPosts.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("No saved posts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    return
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    modifier = Modifier.fillMaxSize(),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    items(state.savedPosts, key = { it.postId }) { saved ->
      val post =
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
      Box(
        modifier =
          Modifier.aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpenPostDetail(post) }
      ) {
        AuraImage(
          imageData = saved.imageData,
          contentDescription = saved.caption,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
          targetSizePx = 540,
        )
        IconButton(
          onClick = { onToggleSave(post) },
          modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = "Unsave",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ArchiveSubScreen(
  state: AuraHomeUiState,
  onOpenPostDetail: (Post) -> Unit,
  onToggleArchivePost: (Post) -> Unit,
  onDeleteStory: (String) -> Unit,
) {
  var tab by remember { mutableIntStateOf(0) }
  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = tab) {
      Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Posts Archive (${state.archivedPosts.size})") })
      Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Stories (${state.myStories.size})") })
    }
    if (tab == 0) {
      if (state.archivedPosts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            "No archived posts. Use 'Move to Archive' in any of your post menus.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
          )
        }
      } else {
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(state.archivedPosts, key = { it.id }) { post ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth().clickable { onOpenPostDetail(post) },
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AuraImage(
                  imageData = post.imageData,
                  contentDescription = post.caption,
                  modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                  targetSizePx = 256,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(post.caption.ifBlank { "Archived ${post.mediaType}" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(formatRelativeTime(post.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { onToggleArchivePost(post) }) {
                  Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Restore")
                }
              }
            }
          }
        }
      }
    } else {
      if (state.myStories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No archived stories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      } else {
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(state.myStories, key = { it.id }) { story ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AuraImage(
                  imageData = story.imageData,
                  contentDescription = story.captionText,
                  modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                  targetSizePx = 256,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(story.captionText.ifBlank { "Story" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(formatRelativeTime(story.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDeleteStory(story.id) }) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete story", tint = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun YourActivitySubScreen(
  state: AuraHomeUiState,
  currentUserId: String,
  sessionStartMs: Long,
  onOpenPostDetail: (Post) -> Unit,
  onToggleLike: (Post, Boolean) -> Unit,
) {
  var tab by remember { mutableIntStateOf(0) }
  val likedPosts = remember(state.feedPosts, state.likedPostIds) {
    state.feedPosts.filter { state.likedPostIds.contains(it.id) }
  }
  val myPosts = remember(state.feedPosts, currentUserId) {
    state.feedPosts.filter { it.userId == currentUserId }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = tab) {
      Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Likes (${likedPosts.size})") })
      Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Posts (${myPosts.size})") })
      Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Time spent") })
    }
    when (tab) {
      0 -> {
        if (likedPosts.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You haven't liked any posts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            items(likedPosts, key = { it.id }) { post ->
              Box(
                modifier =
                  Modifier.aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenPostDetail(post) }
              ) {
                AuraImage(
                  imageData = post.imageData,
                  contentDescription = post.caption,
                  modifier = Modifier.fillMaxSize(),
                  targetSizePx = 540,
                )
              }
            }
          }
        }
      }
      1 -> {
        if (myPosts.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You haven't shared any posts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            items(myPosts, key = { it.id }) { post ->
              Box(
                modifier =
                  Modifier.aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenPostDetail(post) }
              ) {
                AuraImage(
                  imageData = post.imageData,
                  contentDescription = post.caption,
                  modifier = Modifier.fillMaxSize(),
                  targetSizePx = 540,
                )
              }
            }
          }
        }
      }
      else -> {
        var sessionSeconds by remember(sessionStartMs) {
          mutableLongStateOf(((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L))
        }
        LaunchedEffect(sessionStartMs) {
          while (true) {
            sessionSeconds = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            delay(1000L)
          }
        }
        val mins = sessionSeconds / 60L
        val secs = sessionSeconds % 60L
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          StatusCheckCard("Today's active session", "${mins}m ${secs}s spent on Aura")
          StatusCheckCard("Following", "${state.followingIds.size} creators followed")
          StatusCheckCard("Saved items", "${state.savedPosts.size} posts bookmarked")
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeManagementSubScreen(
  prefs: UserPrivate,
  sessionStartMs: Long,
  onUpdateSettings: (Map<String, Any>, String?) -> Unit,
) {
  var sessionSeconds by remember(sessionStartMs) {
    mutableLongStateOf(((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L))
  }
  LaunchedEffect(sessionStartMs) {
    while (true) {
      sessionSeconds = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
      delay(1000L)
    }
  }
  val mins = sessionSeconds / 60L
  val secs = sessionSeconds % 60L
  val limitOptions = remember { listOf(0L to "Off", 15L to "15m", 30L to "30m", 45L to "45m", 60L to "1 hr", 120L to "2 hr") }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Time on Aura today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Text("${mins}m ${secs}s", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AuraCoral)
      }
    }

    Spacer(modifier = Modifier.height(18.dp))
    Text("Daily time limit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      limitOptions.forEach { (minutes, label) ->
        FilterChip(
          selected = prefs.dailyTimeLimitMinutes == minutes,
          onClick = { onUpdateSettings(mapOf("dailyTimeLimitMinutes" to minutes), "Daily limit: $label") },
          label = { Text(label) },
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    SettingsSwitchRow(
      title = "Sleep mode",
      subtitle = "Mute notifications automatically during late-night rest hours",
      checked = prefs.sleepModeEnabled,
      onCheckedChange = { onUpdateSettings(mapOf("sleepModeEnabled" to it), null) },
    )
  }
}

@Composable
private fun UserListManagerSubScreen(
  subtitle: String,
  profiles: List<UserProfile>,
  currentUserId: String,
  selectedIds: List<String>,
  actionAddLabel: String,
  actionRemoveLabel: String,
  onToggle: (String) -> Unit,
) {
  var search by remember { mutableStateOf("") }
  val candidates =
    remember(profiles, currentUserId, search) {
      val q = search.trim().lowercase()
      profiles
        .filter { it.userId != currentUserId }
        .filter { q.isEmpty() || it.username.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
    }

  Column(modifier = Modifier.fillMaxSize()) {
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
    OutlinedTextField(
      value = search,
      onValueChange = { search = it },
      placeholder = { Text("Search users...") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (candidates.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No other users found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(candidates, key = { it.userId }) { user ->
          val isAdded = selectedIds.contains(user.userId)
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            AuraAvatar(user.avatarUrl, user.username, size = 44.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (isAdded) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Icon(Icons.Filled.Star, contentDescription = null, tint = AuraCoral, modifier = Modifier.size(14.dp))
                }
              }
              Text(user.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isAdded) {
              OutlinedButton(onClick = { onToggle(user.userId) }) {
                Text(actionRemoveLabel)
              }
            } else {
              Button(onClick = { onToggle(user.userId) }, colors = ButtonDefaults.buttonColors(containerColor = AuraBlue)) {
                Text(actionAddLabel, color = Color.White)
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HiddenWordsSubScreen(
  prefs: UserPrivate,
  onUpdateSettings: (Map<String, Any>, String?) -> Unit,
  onAddHiddenWord: (String) -> Unit,
  onRemoveHiddenWord: (String) -> Unit,
) {
  var wordInput by remember { mutableStateOf("") }
  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    SettingsSwitchRow(
      title = "Hide comments & posts with custom words",
      subtitle = "Automatically filter out comments and posts matching any word below.",
      checked = prefs.hideOffensiveComments,
      onCheckedChange = { onUpdateSettings(mapOf("hideOffensiveComments" to it), null) },
    )
    HorizontalDivider()
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Manage custom words", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = wordInput,
          onValueChange = { wordInput = it },
          placeholder = { Text("Add word or phrase...") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("hidden_word_input"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = {
            onAddHiddenWord(wordInput)
            wordInput = ""
          },
          enabled = wordInput.isNotBlank(),
          modifier = Modifier.height(50.dp).testTag("hidden_word_add_button"),
        ) {
          Text("Add")
        }
      }
      if (prefs.hiddenWords.isEmpty()) {
        Text("No hidden words added yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          prefs.hiddenWords.forEach { word ->
            InputChip(
              selected = true,
              onClick = { onRemoveHiddenWord(word) },
              label = { Text(word) },
              trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $word", modifier = Modifier.size(16.dp)) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RadioSettingRow(
  title: String,
  subtitle: String = "",
  selected: Boolean,
  testTag: String = "",
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
      if (subtitle.isNotBlank()) {
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    RadioButton(selected = selected, onClick = onClick)
  }
}

@Composable
private fun StatusCheckCard(title: String, detail: String) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
