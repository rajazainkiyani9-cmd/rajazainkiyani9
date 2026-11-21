package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.isSaveLoginEnabled
import com.example.auth.setSaveLoginEnabled
import com.example.data.model.Post
import com.example.data.model.UserPrivate
import com.example.data.model.UserProfile
import com.example.ui.AuraHomeUiState
import com.example.ui.components.AuraAvatar
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral

internal enum class SettingsPage {
  MAIN,
  ACCOUNTS_CENTER,
  SAVED,
  ARCHIVE,
  YOUR_ACTIVITY,
  NOTIFICATIONS,
  TIME_MANAGEMENT,
  FAVORITES,
  MUTED,
  CONTENT_PREFS,
  LIKE_COUNTS,
  ACCOUNT_PRIVACY,
  CLOSE_FRIENDS,
  BLOCKED,
  HIDE_STORY,
  MESSAGES_REPLIES,
  TAGS_MENTIONS,
  COMMENTS_CONTROLS,
  SHARING_REUSE,
  RESTRICTED,
  HIDDEN_WORDS,
  APPEARANCE,
  ARCHIVING_DOWNLOADS,
  ACCESSIBILITY,
  LANGUAGE,
  DATA_USAGE,
  ACCOUNT_STATUS,
  HELP,
  ABOUT,
}

private data class SettingsRowItem(
  val id: String,
  val section: String,
  val title: String,
  val icon: ImageVector,
  val trailingValue: String = "",
  val subtitle: String = "",
  val page: SettingsPage,
)

@Composable
fun SettingsScreen(
  state: AuraHomeUiState,
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
  onToggleUserInList: (fieldKey: String, currentList: List<String>, targetUserId: String, addedMsg: String, removedMsg: String) -> Unit,
  onAddHiddenWord: (String) -> Unit,
  onRemoveHiddenWord: (String) -> Unit,
  onUpdateProfile: (String, String, String, String, String) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  var activePage by remember { mutableStateOf(SettingsPage.MAIN) }
  var searchQuery by remember { mutableStateOf("") }
  var showLogoutConfirm by remember { mutableStateOf(false) }
  var rememberLoginOnLogout by remember { mutableStateOf(isSaveLoginEnabled(context)) }

  BackHandler {
    if (activePage != SettingsPage.MAIN) {
      activePage = SettingsPage.MAIN
    } else {
      onBack()
    }
  }

  val prefs = state.userPrivate ?: UserPrivate(userId = currentUserId)
  val profile = state.myProfile ?: UserProfile(userId = currentUserId, username = "aura_creator")

  if (activePage != SettingsPage.MAIN) {
    SettingsSubPageRouter(
      page = activePage,
      state = state,
      prefs = prefs,
      profile = profile,
      currentUserId = currentUserId,
      sessionStartMs = sessionStartMs,
      onBack = { activePage = SettingsPage.MAIN },
      onOpenPostDetail = onOpenPostDetail,
      onToggleSave = onToggleSave,
      onToggleLike = onToggleLike,
      onToggleArchivePost = onToggleArchivePost,
      onDeleteStory = onDeleteStory,
      onUpdateSettings = onUpdateSettings,
      onSetThemeMode = onSetThemeMode,
      onSetAccountPrivacy = onSetAccountPrivacy,
      onToggleUserInList = onToggleUserInList,
      onAddHiddenWord = onAddHiddenWord,
      onRemoveHiddenWord = onRemoveHiddenWord,
      onUpdateProfile = onUpdateProfile,
      onRequestLogout = { showLogoutConfirm = true },
    )
  } else {
    val allRows =
      remember(state, prefs, profile) {
        val isPrivate = !profile.isPublic
        val themeLabel =
          when (prefs.themeMode) {
            "dark" -> "Dark"
            "light" -> "Light"
            else -> "System"
          }
        val timeLimitLabel =
          if (prefs.dailyTimeLimitMinutes <= 0L) "Off"
          else "${prefs.dailyTimeLimitMinutes}m"

        listOf(
          SettingsRowItem("saved", "How you use Aura", "Saved", Icons.Outlined.BookmarkBorder, state.savedPosts.size.takeIf { it > 0 }?.toString() ?: "", "", SettingsPage.SAVED),
          SettingsRowItem("archive", "How you use Aura", "Archive", Icons.Outlined.History, (state.archivedPosts.size + state.myStories.size).takeIf { it > 0 }?.toString() ?: "", "", SettingsPage.ARCHIVE),
          SettingsRowItem("activity", "How you use Aura", "Your activity", Icons.Outlined.Timeline, "", "", SettingsPage.YOUR_ACTIVITY),
          SettingsRowItem("notifs", "How you use Aura", "Notifications", Icons.Outlined.Notifications, if (prefs.notificationsMuted) "Paused" else "Active", "", SettingsPage.NOTIFICATIONS),
          SettingsRowItem("time", "How you use Aura", "Time management", Icons.Outlined.Schedule, timeLimitLabel, "", SettingsPage.TIME_MANAGEMENT),

          SettingsRowItem("favorites", "What you see", "Favorites", Icons.Outlined.StarBorder, prefs.favoriteUserIds.size.toString(), "", SettingsPage.FAVORITES),
          SettingsRowItem("muted", "What you see", "Muted accounts", Icons.Outlined.VolumeOff, prefs.mutedUserIds.size.toString(), "", SettingsPage.MUTED),
          SettingsRowItem("content", "What you see", "Content preferences", Icons.Outlined.Tune, prefs.sensitiveContentLevel, "", SettingsPage.CONTENT_PREFS),
          SettingsRowItem("likes", "What you see", "Like and share counts", Icons.Outlined.FavoriteBorder, if (prefs.hideLikeCounts) "Hidden" else "Visible", "", SettingsPage.LIKE_COUNTS),

          SettingsRowItem("privacy", "Who can see your content", "Account privacy", if (isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public, if (isPrivate) "Private" else "Public", "", SettingsPage.ACCOUNT_PRIVACY),
          SettingsRowItem("close_friends", "Who can see your content", "Close Friends", Icons.Outlined.Stars, prefs.closeFriendIds.size.toString(), "", SettingsPage.CLOSE_FRIENDS),
          SettingsRowItem("blocked", "Who can see your content", "Blocked", Icons.Outlined.Block, prefs.blockedUserIds.size.toString(), "", SettingsPage.BLOCKED),
          SettingsRowItem("hide_story", "Who can see your content", "Hide story and live", Icons.Outlined.VisibilityOff, prefs.hideStoryFromIds.size.toString(), "", SettingsPage.HIDE_STORY),

          SettingsRowItem("messages", "How others can interact with you", "Messages and story replies", Icons.Outlined.ChatBubbleOutline, prefs.allowMessagesFrom, "", SettingsPage.MESSAGES_REPLIES),
          SettingsRowItem("tags", "How others can interact with you", "Tags and mentions", Icons.Outlined.AlternateEmail, prefs.allowTagsFrom, "", SettingsPage.TAGS_MENTIONS),
          SettingsRowItem("comments", "How others can interact with you", "Comments", Icons.Outlined.ModeComment, prefs.allowCommentsFrom, "", SettingsPage.COMMENTS_CONTROLS),
          SettingsRowItem("sharing", "How others can interact with you", "Sharing and reuse", Icons.Outlined.Repeat, "", "", SettingsPage.SHARING_REUSE),
          SettingsRowItem("restricted", "How others can interact with you", "Restricted", Icons.Outlined.PersonOff, prefs.restrictedUserIds.size.toString(), "", SettingsPage.RESTRICTED),
          SettingsRowItem("hidden_words", "How others can interact with you", "Hidden Words", Icons.Outlined.TextFields, prefs.hiddenWords.size.takeIf { it > 0 }?.toString() ?: "", "", SettingsPage.HIDDEN_WORDS),

          SettingsRowItem("appearance", "Your app and media", "Appearance", Icons.Outlined.DarkMode, themeLabel, "", SettingsPage.APPEARANCE),
          SettingsRowItem("archiving", "Your app and media", "Archiving and downloading", Icons.Outlined.Download, "", "", SettingsPage.ARCHIVING_DOWNLOADS),
          SettingsRowItem("accessibility", "Your app and media", "Accessibility", Icons.Outlined.AccessibilityNew, "", "", SettingsPage.ACCESSIBILITY),
          SettingsRowItem("language", "Your app and media", "Language", Icons.Outlined.Language, prefs.appLanguage, "", SettingsPage.LANGUAGE),
          SettingsRowItem("data_usage", "Your app and media", "Data usage and media quality", Icons.Outlined.SignalCellularAlt, if (prefs.dataSaverEnabled) "Saver" else "Standard", "", SettingsPage.DATA_USAGE),

          SettingsRowItem("status", "More info and support", "Account status", Icons.Outlined.VerifiedUser, "", "", SettingsPage.ACCOUNT_STATUS),
          SettingsRowItem("help", "More info and support", "Help", Icons.AutoMirrored.Outlined.HelpOutline, "", "", SettingsPage.HELP),
          SettingsRowItem("about", "More info and support", "About", Icons.Outlined.Info, "v1.0", "", SettingsPage.ABOUT),
        )
      }

    val query = searchQuery.trim().lowercase()
    val filteredRows =
      remember(allRows, query) {
        if (query.isEmpty()) allRows
        else allRows.filter { it.title.lowercase().contains(query) || it.section.lowercase().contains(query) }
      }
    val sections = remember(filteredRows) { filteredRows.groupBy { it.section } }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .testTag("settings_main_screen")
    ) {
      // Instagram Settings Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Settings and activity",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }

      LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Instagram Search Bar
        item(key = "settings_search") {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search settings") },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("settings_search_input"),
          )
        }

        // Instagram Accounts Center Card
        if (query.isEmpty() || "accounts center".contains(query) || "your account".contains(query)) {
          item(key = "accounts_center") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Your account",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = AuraCoral,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Aura",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { activePage = SettingsPage.ACCOUNTS_CENTER }
                    .testTag("accounts_center_card"),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  AuraAvatar(
                    avatarUrl = profile.avatarUrl,
                    username = profile.username,
                    size = 44.dp,
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Accounts Center",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Password, security, personal details, ad preferences",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        // All Instagram Sections
        sections.forEach { (sectionName, rows) ->
          item(key = "header_$sectionName") {
            HorizontalDivider(
              thickness = 4.dp,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.padding(vertical = 6.dp),
            )
            Text(
              text = sectionName,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
          }
          items(rows, key = { it.id }, contentType = { "settings_row" }) { row ->
            InstagramSettingsRow(
              icon = row.icon,
              title = row.title,
              trailingValue = row.trailingValue,
              testTag = "settings_row_${row.id}",
              onClick = { activePage = row.page },
            )
          }
        }

        // Login Section
        if (query.isEmpty() || "log out".contains(query) || "login".contains(query) || "switch".contains(query)) {
          item(key = "login_section") {
            HorizontalDivider(
              thickness = 4.dp,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.padding(vertical = 6.dp),
            )
            Text(
              text = "Login",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { onSignOut() }
                  .padding(horizontal = 16.dp, vertical = 14.dp)
                  .testTag("settings_switch_account"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.PersonAdd,
                contentDescription = null,
                tint = AuraBlue,
                modifier = Modifier.size(24.dp),
              )
              Spacer(modifier = Modifier.width(14.dp))
              Text(
                text = "Add or switch account",
                style = MaterialTheme.typography.bodyLarge,
                color = AuraBlue,
                fontWeight = FontWeight.Medium,
              )
            }
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { showLogoutConfirm = true }
                  .padding(horizontal = 16.dp, vertical = 14.dp)
                  .testTag("sign_out_button"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Log out @${profile.username.ifBlank { "aura_user" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Spacer(modifier = Modifier.height(28.dp))
          }
        }
      }
    }
  }

  if (showLogoutConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirm = false },
      title = { Text("Log out of @${profile.username}?") },
      text = {
        Column {
          Text("If login info is saved, you can sign back in with 1 tap next time.")
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { rememberLoginOnLogout = !rememberLoginOnLogout },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = rememberLoginOnLogout,
              onCheckedChange = { rememberLoginOnLogout = it },
            )
            Text(
              text = "Remember login info",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            setSaveLoginEnabled(context, rememberLoginOnLogout)
            showLogoutConfirm = false
            onSignOut()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Log out")
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirm = false }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
internal fun InstagramSettingsRow(
  icon: ImageVector,
  title: String,
  trailingValue: String = "",
  testTag: String = "",
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 13.dp)
        .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    if (trailingValue.isNotBlank()) {
      Text(
        text = trailingValue,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(6.dp))
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
internal fun SettingsSwitchRow(
  title: String,
  subtitle: String = "",
  checked: Boolean,
  testTag: String = "",
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
      if (subtitle.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier,
    )
  }
}
