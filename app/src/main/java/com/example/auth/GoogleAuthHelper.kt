package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val AUTH_PREFS = "aura_auth_prefs"
private const val KEY_DRAFT_NAME = "draft_display_name"
private const val KEY_DRAFT_USERNAME = "draft_username"
private const val KEY_DRAFT_PHONE = "draft_phone"
private const val KEY_SAVE_LOGIN_ENABLED = "save_login_enabled"
private const val KEY_SAVED_ACCOUNTS = "saved_accounts_json"

data class SavedAuthAccount(
  val uid: String,
  val displayName: String,
  val username: String,
  val avatarUrl: String,
  val email: String,
  val phoneNumber: String,
  val providerId: String,
)

fun isSaveLoginEnabled(context: Context): Boolean =
  context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SAVE_LOGIN_ENABLED, true)

fun setSaveLoginEnabled(context: Context, enabled: Boolean) {
  context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SAVE_LOGIN_ENABLED, enabled).apply()
}

fun getSavedAccounts(context: Context): List<SavedAuthAccount> {
  val raw = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).getString(KEY_SAVED_ACCOUNTS, null)
    ?: return emptyList()
  return runCatching {
    val arr = JSONArray(raw)
    buildList {
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val uid = obj.optString("uid")
        if (uid.isBlank()) continue
        add(
          SavedAuthAccount(
            uid = uid,
            displayName = obj.optString("displayName"),
            username = obj.optString("username"),
            avatarUrl = obj.optString("avatarUrl"),
            email = obj.optString("email"),
            phoneNumber = obj.optString("phoneNumber"),
            providerId = obj.optString("providerId"),
          )
        )
      }
    }
  }.getOrDefault(emptyList())
}

fun upsertSavedAccount(
  context: Context,
  user: FirebaseUser,
  username: String? = null,
  displayName: String? = null,
  avatarUrl: String? = null,
  phoneOverride: String? = null,
) {
  if (!isSaveLoginEnabled(context)) return
  val existingPhone = getSavedAccounts(context).firstOrNull { it.uid == user.uid }?.phoneNumber.orEmpty()
  val resolvedPhone =
    phoneOverride?.takeIf { it.isNotBlank() }
      ?: user.phoneNumber?.takeIf { it.isNotBlank() }
      ?: existingPhone
  val providerId =
    user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "google.com"
  val resolvedName =
    displayName?.takeIf { it.isNotBlank() }
      ?: user.displayName?.takeIf { it.isNotBlank() }
      ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
      ?: resolvedPhone.takeIf { it.isNotBlank() }?.let { "Aura ${it.takeLast(4)}" }
      ?: "Aura Creator"
  val resolvedUsername =
    username?.takeIf { it.isNotBlank() }
      ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
      ?: resolvedPhone.takeIf { it.isNotBlank() }?.let { "aura_${it.takeLast(4)}" }
      ?: "aura_user"
  val entry =
    SavedAuthAccount(
      uid = user.uid,
      displayName = resolvedName.take(50),
      username = resolvedUsername.take(30),
      avatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: user.photoUrl?.toString().orEmpty(),
      email = user.email.orEmpty(),
      phoneNumber = resolvedPhone,
      providerId = providerId,
    )
  val updated = (listOf(entry) + getSavedAccounts(context).filterNot { it.uid == entry.uid }).take(5)
  saveAccountsList(context, updated)
}

fun removeSavedAccount(context: Context, uid: String) {
  val updated = getSavedAccounts(context).filterNot { it.uid == uid }
  saveAccountsList(context, updated)
}

private fun saveAccountsList(context: Context, list: List<SavedAuthAccount>) {
  val arr = JSONArray()
  list.forEach { acc ->
    arr.put(
      JSONObject().apply {
        put("uid", acc.uid)
        put("displayName", acc.displayName)
        put("username", acc.username)
        put("avatarUrl", acc.avatarUrl)
        put("email", acc.email)
        put("phoneNumber", acc.phoneNumber)
        put("providerId", acc.providerId)
      }
    )
  }
  context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SAVED_ACCOUNTS, arr.toString()).apply()
}

fun normalizeE164PhoneNumber(countryCode: String, rawPhone: String): String {
  val cleanedRaw = rawPhone.trim().replace(Regex("[\\s\\-()]"), "")
  if (cleanedRaw.isBlank()) return ""
  if (cleanedRaw.startsWith("+")) {
    return "+" + cleanedRaw.drop(1).filter { it.isDigit() }
  }
  val ccDigits = countryCode.filter { it.isDigit() }.ifBlank { "92" }
  val localDigits = cleanedRaw.filter { it.isDigit() }.trimStart('0')
  return if (localDigits.isNotBlank()) "+$ccDigits$localDigits" else ""
}

data class AuthProfileDraft(
  val displayName: String? = null,
  val username: String? = null,
  val phoneNumber: String? = null,
)

fun saveAuthProfileDraft(
  context: Context,
  displayName: String,
  username: String,
  phoneNumber: String = "",
) {
  val trimmedPhone = phoneNumber.trim()
  val resolvedName =
    displayName.trim().ifBlank {
      if (trimmedPhone.length >= 4) "Aura ${trimmedPhone.takeLast(4)}" else ""
    }
  val resolvedUsername =
    username.trim().ifBlank {
      if (trimmedPhone.length >= 4) "aura_${trimmedPhone.takeLast(4)}" else ""
    }
  context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().apply {
    putString(KEY_DRAFT_NAME, resolvedName)
    putString(KEY_DRAFT_USERNAME, resolvedUsername)
    putString(KEY_DRAFT_PHONE, trimmedPhone)
    apply()
  }
}

fun consumeAuthProfileDraft(context: Context): AuthProfileDraft {
  val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
  val name = prefs.getString(KEY_DRAFT_NAME, null)?.takeIf { it.isNotBlank() }
  val username = prefs.getString(KEY_DRAFT_USERNAME, null)?.takeIf { it.isNotBlank() }
  val phone = prefs.getString(KEY_DRAFT_PHONE, null)?.takeIf { it.isNotBlank() }
  if (name != null || username != null || phone != null) {
    prefs.edit().remove(KEY_DRAFT_NAME).remove(KEY_DRAFT_USERNAME).remove(KEY_DRAFT_PHONE).apply()
  }
  return AuthProfileDraft(displayName = name, username = username, phoneNumber = phone)
}

fun getLinkedProviders(user: FirebaseUser?): Set<String> =
  user?.providerData?.mapNotNull { it.providerId }?.toSet() ?: emptySet()

private fun isProviderDisabledError(e: Exception): Boolean {
  val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
  val msg = e.localizedMessage.orEmpty()
  return code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
    code.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
    msg.contains("not allowed", ignoreCase = true) ||
    msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
    msg.contains("provider is not enabled", ignoreCase = true)
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  if (!isSaveLoginEnabled(context)) {
    onUnauthenticated()
    return
  }
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      onUnauthenticated()
      return
    }

  val googleIdOption =
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()

  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (e: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
      return
    }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  scope.launch {
    try {
      val activityContext = (context as? Activity) ?: context
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type returned")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.w("Auth", "Google Sign-In failed: ${e.message}")
      onAuthError(e.localizedMessage ?: "Sign in failed")
    }
  }
}

fun onSocialOAuthClicked(
  providerId: String,
  providerLabel: String,
  context: Context,
  displayNameDraft: String = "",
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
) {
  val activity = context as? Activity
  if (activity == null) {
    onAuthError("Activity context required for $providerLabel")
    return
  }

  val builder = OAuthProvider.newBuilder(providerId)
  if (providerId == "apple.com") {
    builder.scopes = listOf("email", "name")
  }
  val provider = builder.build()

  scope.launch {
    try {
      val pendingTask = Firebase.auth.pendingAuthResult
      val result =
        if (pendingTask != null) {
          pendingTask.await()
        } else {
          Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
        }
      if (displayNameDraft.isNotBlank() && result.user?.displayName.isNullOrBlank()) {
        runCatching {
          result.user
            ?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayNameDraft.trim()).build())
            ?.await()
        }
      }
      onAuthSuccess()
    } catch (e: Exception) {
      Log.w("Auth", "$providerLabel sign-in status: ${e.message}")
      val msg =
        if (isProviderDisabledError(e)) {
          "$providerLabel needs to be turned on in Firebase Console -> Authentication -> Sign-in method. Use Continue with Google below for instant sign-in."
        } else {
          e.localizedMessage ?: "$providerLabel sign-in cancelled or failed."
        }
      onAuthError(msg)
    }
  }
}

fun linkSocialOAuthProvider(
  providerId: String,
  providerLabel: String,
  context: Context,
  scope: CoroutineScope,
  onLinked: () -> Unit,
  onError: (String) -> Unit,
) {
  val activity = context as? Activity
  val currentUser = Firebase.auth.currentUser
  if (activity == null || currentUser == null) {
    onError("Sign in first to connect $providerLabel.")
    return
  }

  val builder = OAuthProvider.newBuilder(providerId)
  if (providerId == "apple.com") {
    builder.scopes = listOf("email", "name")
  }
  val provider = builder.build()

  scope.launch {
    try {
      currentUser.startActivityForLinkWithProvider(activity, provider).await()
      onLinked()
    } catch (e: Exception) {
      Log.w("Auth", "$providerLabel link status: ${e.message}")
      val msg =
        if (isProviderDisabledError(e)) {
          "Enable $providerLabel in Firebase Console -> Authentication -> Sign-in method first to connect it."
        } else {
          e.localizedMessage ?: "Could not connect $providerLabel."
        }
      onError(msg)
    }
  }
}

fun signOutUser(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  clearSavedCredentials: Boolean = false,
  onSignOutComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        if (clearSavedCredentials) {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}
