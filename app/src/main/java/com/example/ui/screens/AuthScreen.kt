package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.auth.SavedAuthAccount
import com.example.auth.attemptAutoSignIn
import com.example.auth.getSavedAccounts
import com.example.auth.isSaveLoginEnabled
import com.example.auth.normalizeE164PhoneNumber
import com.example.auth.onGoogleSignInClicked
import com.example.auth.onSocialOAuthClicked
import com.example.auth.removeSavedAccount
import com.example.auth.saveAuthProfileDraft
import com.example.auth.setSaveLoginEnabled
import com.example.ui.components.AuraAvatar
import com.example.ui.components.getAuraImageLoader
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraButtonGradient
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraStoryGradient
import com.example.ui.theme.AuraViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class AuthFlowMode {
  LOGIN,
  CREATE,
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var mode by remember { mutableStateOf(AuthFlowMode.LOGIN) }
  var saveLoginEnabled by remember { mutableStateOf(true) }
  var savedAccounts by remember { mutableStateOf<List<SavedAuthAccount>>(emptyList()) }

  // ID / Profile setup fields (including Mobile Number)
  var fullName by remember { mutableStateOf("") }
  var desiredUsername by remember { mutableStateOf("") }
  var countryCode by remember { mutableStateOf("+92") }
  var phoneDigits by remember { mutableStateOf("") }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    val (saveEnabled, accounts) =
      withContext(Dispatchers.IO) {
        isSaveLoginEnabled(context) to getSavedAccounts(context)
      }
    saveLoginEnabled = saveEnabled
    savedAccounts = accounts
    delay(350L)
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  fun launchGoogleAuth(withDraft: Boolean) {
    errorMessage = null
    if (withDraft) {
      val normalizedPhone = normalizeE164PhoneNumber(countryCode, phoneDigits)
      saveAuthProfileDraft(
        context = context,
        displayName = fullName,
        username = desiredUsername,
        phoneNumber = normalizedPhone,
      )
    }
    isLoading = true
    onGoogleSignInClicked(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isLoading = false
        onAuthSuccess()
      },
      onAuthError = { msg ->
        isLoading = false
        errorMessage = msg
      },
      scope = scope,
      onAuthCancelled = { isLoading = false },
    )
  }

  val isCreate = mode == AuthFlowMode.CREATE

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.background,
                AuraViolet.copy(alpha = 0.08f),
                AuraCoral.copy(alpha = 0.08f),
              )
          )
        )
        .safeDrawingPadding()
        .imePadding(),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
          modifier =
            Modifier.size(80.dp)
              .border(2.5.dp, AuraStoryGradient, CircleShape)
              .padding(5.dp)
              .clip(CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          val logoLoader = remember(context) { getAuraImageLoader(context) }
          val logoRequest =
            remember(context) {
              ImageRequest.Builder(context)
                .data(R.drawable.aura_icon_foreground)
                .size(240)
                .crossfade(false)
                .build()
            }
          AsyncImage(
            model = logoRequest,
            imageLoader = logoLoader,
            contentDescription = "Aura app logo",
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "Aura",
          style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
          color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
          text =
            if (isCreate) "Create your Aura ID with your phone number, name & Google."
            else "Sign in once — stay logged in automatically on this device.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        // 1-Tap Saved Logins Card
        if (savedAccounts.isNotEmpty()) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "SAVED LOGINS",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            savedAccounts.forEach { acc ->
              SavedAccountQuickCard(
                account = acc,
                enabled = !isLoading,
                onContinue = {
                  setSaveLoginEnabled(context, true)
                  saveLoginEnabled = true
                  if (acc.providerId == "facebook.com" || acc.providerId == "apple.com") {
                    errorMessage = null
                    isLoading = true
                    onSocialOAuthClicked(
                      providerId = acc.providerId,
                      providerLabel = if (acc.providerId == "facebook.com") "Facebook" else "Apple ID",
                      context = context,
                      onAuthSuccess = { isLoading = false; onAuthSuccess() },
                      onAuthError = { msg -> isLoading = false; errorMessage = msg },
                      scope = scope,
                    )
                  } else {
                    launchGoogleAuth(withDraft = false)
                  }
                },
                onRemove = {
                  removeSavedAccount(context, acc.uid)
                  savedAccounts = getSavedAccounts(context)
                },
              )
            }
          }
        }

        // Log in / Create account switcher
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            AuthModeTab(
              label = "Log in",
              selected = !isCreate,
              onClick = {
                mode = AuthFlowMode.LOGIN
                errorMessage = null
              },
              modifier = Modifier.weight(1f).testTag("tab_login"),
            )
            AuthModeTab(
              label = "Create ID",
              selected = isCreate,
              onClick = {
                mode = AuthFlowMode.CREATE
                errorMessage = null
              },
              modifier = Modifier.weight(1f).testTag("tab_create_account"),
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (isCreate) {
              Text(
                text = "Create ID with Phone Number & Handle",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp),
              )

              Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                listOf("+92" to "PK", "+1" to "US", "+44" to "UK", "+971" to "UAE", "+91" to "IN").forEach { (code, tag) ->
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color =
                      if (countryCode == code) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { countryCode = code },
                  ) {
                    Text(
                      text = "$code $tag",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = if (countryCode == code) FontWeight.Bold else FontWeight.Medium,
                      textAlign = TextAlign.Center,
                      modifier = Modifier.padding(vertical = 6.dp),
                    )
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = countryCode,
                  onValueChange = { countryCode = it },
                  label = { Text("Code") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                  modifier = Modifier.width(84.dp),
                )
                OutlinedTextField(
                  value = phoneDigits,
                  onValueChange = { phoneDigits = it },
                  label = { Text("Mobile (e.g. 3001234567)") },
                  leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                  modifier = Modifier.weight(1f).testTag("phone_number_input"),
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full name") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("create_fullname_input"),
              )
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedTextField(
                value = desiredUsername,
                onValueChange = { desiredUsername = it },
                label = { Text("Username (optional)") },
                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("create_username_input"),
              )
              Spacer(modifier = Modifier.height(12.dp))
            }

            // Primary Google Sign-In button (backed by CredentialManager GetSignInWithGoogleOption)
            Button(
              onClick = { launchGoogleAuth(withDraft = isCreate) },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("google_sign_in_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
              contentPadding = PaddingValues(),
            ) {
              Box(
                modifier = Modifier.fillMaxSize().background(AuraButtonGradient, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
              ) {
                if (isLoading) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text(
                    text = if (isCreate) "Create Aura ID with Google" else "Continue with Google",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }

            if (isCreate) {
              Text(
                text = "Your phone number, name & @username are linked to your Aura ID automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              HorizontalDivider(modifier = Modifier.weight(1f))
              Text(
                text = "  OR CONNECT WITH  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  errorMessage = null
                  if (isCreate) {
                    saveAuthProfileDraft(
                      context = context,
                      displayName = fullName,
                      username = desiredUsername,
                      phoneNumber = normalizeE164PhoneNumber(countryCode, phoneDigits),
                    )
                  }
                  isLoading = true
                  onSocialOAuthClicked(
                    providerId = "facebook.com",
                    providerLabel = "Facebook",
                    context = context,
                    displayNameDraft = fullName,
                    onAuthSuccess = {
                      isLoading = false
                      onAuthSuccess()
                    },
                    onAuthError = { msg ->
                      isLoading = false
                      errorMessage = msg
                    },
                    scope = scope,
                  )
                },
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(46.dp).testTag("facebook_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
              ) {
                Text("Facebook", color = Color.White, fontWeight = FontWeight.SemiBold)
              }

              OutlinedButton(
                onClick = {
                  errorMessage = null
                  if (isCreate) {
                    saveAuthProfileDraft(
                      context = context,
                      displayName = fullName,
                      username = desiredUsername,
                      phoneNumber = normalizeE164PhoneNumber(countryCode, phoneDigits),
                    )
                  }
                  isLoading = true
                  onSocialOAuthClicked(
                    providerId = "apple.com",
                    providerLabel = "Apple ID",
                    context = context,
                    displayNameDraft = fullName,
                    onAuthSuccess = {
                      isLoading = false
                      onAuthSuccess()
                    },
                    onAuthError = { msg ->
                      isLoading = false
                      errorMessage = msg
                    },
                    scope = scope,
                  )
                },
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(46.dp).testTag("apple_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              ) {
                Text(
                  text = "Apple ID",
                  color = MaterialTheme.colorScheme.onBackground,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Login Info Toggle
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable {
                    saveLoginEnabled = !saveLoginEnabled
                    setSaveLoginEnabled(context, saveLoginEnabled)
                  }
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = saveLoginEnabled,
                onCheckedChange = {
                  saveLoginEnabled = it
                  setSaveLoginEnabled(context, it)
                },
                modifier = Modifier.testTag("save_login_checkbox"),
              )
              Column {
                Text(
                  text = "Save login info",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Stay signed in — no need to log in every time",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
              textAlign = TextAlign.Center,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
          onClick = {
            mode = if (isCreate) AuthFlowMode.LOGIN else AuthFlowMode.CREATE
            errorMessage = null
          },
          modifier = Modifier.testTag("switch_auth_mode_footer"),
        ) {
          Text(
            text =
              if (isCreate) "Already have an Aura ID? Log in"
              else "New here? Create Aura ID with Phone & Name",
            color = AuraBlue,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun AuthModeTab(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) MaterialTheme.colorScheme.background else Color.Transparent,
    shadowElevation = if (selected) 1.dp else 0.dp,
    modifier = modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color =
          if (selected) MaterialTheme.colorScheme.onBackground
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SavedAccountQuickCard(
  account: SavedAuthAccount,
  enabled: Boolean,
  onContinue: () -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AuraAvatar(
        avatarUrl = account.avatarUrl,
        username = account.username,
        size = 42.dp,
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "@${account.username.ifBlank { account.displayName }}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = account.phoneNumber.ifBlank { account.email.ifBlank { account.providerId } },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Button(
        onClick = onContinue,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("Log in", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      }
      IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Rounded.Close, contentDescription = "Remove saved login", modifier = Modifier.size(16.dp))
      }
    }
  }
}
