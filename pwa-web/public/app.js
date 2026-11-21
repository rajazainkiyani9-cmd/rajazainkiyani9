import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js";
import {
  initializeAuth,
  getAuth,
  indexedDBLocalPersistence,
  browserLocalPersistence,
  browserSessionPersistence,
  setPersistence,
  GoogleAuthProvider,
  FacebookAuthProvider,
  OAuthProvider,
  RecaptchaVerifier,
  signInWithPhoneNumber,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendPasswordResetEmail,
  updateProfile,
  signInWithPopup,
  signInWithRedirect,
  linkWithPopup,
  getRedirectResult,
  onAuthStateChanged,
  signOut,
} from "https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js";
import {
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  collection,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  writeBatch,
  increment,
  serverTimestamp,
} from "https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js";
import {
  getStorage,
  ref as storageRef,
  uploadBytesResumable,
  getDownloadURL,
} from "https://www.gstatic.com/firebasejs/11.6.0/firebase-storage.js";
import { firebaseConfig, FIRESTORE_DATABASE_ID } from "/firebase-config.js";

const app = initializeApp(firebaseConfig);
let auth;
try {
  auth = initializeAuth(app, {
    persistence: [indexedDBLocalPersistence, browserLocalPersistence],
  });
} catch (_) {
  auth = getAuth(app);
}
let db;
try {
  db = initializeFirestore(
    app,
    { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) },
    FIRESTORE_DATABASE_ID
  );
} catch (_) {
  db = initializeFirestore(app, {}, FIRESTORE_DATABASE_ID);
}
const storage = getStorage(app);

const AURA_FILTERS = ["Original", "Clarendon", "Juno", "Lark", "Moon", "Vintage"];
const DRAWABLE_MAP = {
  "drawable:img_post_coastal_sunset": "/assets/img_post_coastal_sunset.jpg",
  "drawable:img_post_coffee_studio": "/assets/img_post_coffee_studio.jpg",
  "drawable:img_post_mountain_golden": "/assets/img_post_mountain_golden.jpg",
  "drawable:img_post_neon_street": "/assets/img_post_neon_street.jpg",
};

const initialUrlTab = new URLSearchParams(window.location.search).get("tab");

const state = {
  user: null,
  tab: ["home", "explore", "create", "reels", "profile"].includes(initialUrlTab) ? initialUrlTab : "home",
  subView: initialUrlTab === "inbox" ? "inbox" : null, // null | settings | notifications | inbox | chat | user_profile
  selectedUserId: null,
  selectedChatUser: null,
  settingsSubKey: null,
  settingsSearch: "",
  profileTab: "posts",
  exploreQuery: "",
  feedFilter: "all",
  myPrivate: null,
  myProfile: null,
  profiles: [],
  posts: [],
  myPosts: [],
  stories: [],
  notifications: [],
  conversations: [],
  chatMessages: [],
  chatUnsub: null,
  likedPostIds: new Set(),
  savedPosts: [],
  followingIds: new Set(),
  unsubs: [],
  deferredInstallPrompt: null,
  installDismissed: false,
  sessionStartMs: Date.now(),
  createType: "photo",
  selectedFilter: "Original",
  selectedFile: null,
  previewObjectUrl: null,
  uploadProgress: 0,
  isUploading: false,
};

// DOM references
const splashEl = document.getElementById("splash-screen");
const authEl = document.getElementById("auth-screen");
const shellEl = document.getElementById("app-shell");
const viewEl = document.getElementById("view-container");
const modalBackdrop = document.getElementById("modal-backdrop");
const modalContent = document.getElementById("modal-content");
const toastEl = document.getElementById("toast");
const authErrorEl = document.getElementById("auth-error");
const authInfoEl = document.getElementById("auth-info");
const installBanner = document.getElementById("install-banner");
const installHintEl = document.getElementById("install-banner-hint");
const installAuthBtn = document.getElementById("install-pwa-auth-btn");
const navAvatarEl = document.getElementById("nav-profile-avatar");

// Service Worker registration
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  });
}

// Install prompt handling (Android Chrome + Desktop Chrome + iOS Safari)
const isIos = /iphone|ipad|ipod/i.test(window.navigator.userAgent);
const isStandalone =
  window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone === true;

window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  state.deferredInstallPrompt = e;
  updateInstallUI();
});

window.addEventListener("appinstalled", () => {
  state.deferredInstallPrompt = null;
  installBanner.classList.add("hidden");
  installAuthBtn.classList.add("hidden");
  showToast("Aura installed on your device");
});

function updateInstallUI() {
  if (isStandalone || state.installDismissed) {
    installBanner.classList.add("hidden");
    installAuthBtn.classList.add("hidden");
    return;
  }
  if (state.deferredInstallPrompt) {
    installHintEl.textContent = "Install Aura via Chrome on your home screen";
    installBanner.classList.remove("hidden");
    installAuthBtn.classList.remove("hidden");
  } else if (isIos) {
    installHintEl.textContent = "iPhone: Tap Share -> Add to Home Screen";
    installBanner.classList.remove("hidden");
    installAuthBtn.classList.remove("hidden");
  }
}

async function triggerInstall() {
  if (state.deferredInstallPrompt) {
    state.deferredInstallPrompt.prompt();
    await state.deferredInstallPrompt.userChoice;
    state.deferredInstallPrompt = null;
    updateInstallUI();
  } else if (isIos) {
    openModal(`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <h3 style="margin:0">Install Aura on iPhone</h3>
        <button class="icon-btn" id="close-ios-guide">&times;</button>
      </div>
      <p style="color:var(--text-muted);line-height:1.6">
        1. Open this link in <strong>Safari</strong>.<br/>
        2. Tap the <strong>Share</strong> icon (square with upward arrow).<br/>
        3. Tap <strong>Add to Home Screen</strong> -> <strong>Add</strong>.<br/>
      </p>
      <button class="btn-primary" id="ios-guide-ok">Got it</button>
    `);
    document.getElementById("close-ios-guide").onclick = closeModal;
    document.getElementById("ios-guide-ok").onclick = closeModal;
  } else {
    openModal(`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <h3 style="margin:0">Install Aura in Chrome</h3>
        <button class="icon-btn" id="close-chrome-guide">&times;</button>
      </div>
      <p style="color:var(--text-muted);line-height:1.6">
        <strong>Android Chrome:</strong> Tap the <strong>&vellip;</strong> menu in Chrome -> <strong>Install app</strong> (or <strong>Add to Home screen</strong>).<br/><br/>
        <strong>Desktop Chrome:</strong> Click the <strong>Install</strong> icon inside Chrome's address bar on the right.
      </p>
      <button class="btn-primary" id="chrome-guide-ok">Got it</button>
    `);
    document.getElementById("close-chrome-guide").onclick = closeModal;
    document.getElementById("chrome-guide-ok").onclick = closeModal;
  }
}

document.getElementById("install-action-btn").onclick = triggerInstall;
installAuthBtn.onclick = triggerInstall;
document.getElementById("install-dismiss-btn").onclick = () => {
  state.installDismissed = true;
  installBanner.classList.add("hidden");
};

// Helpers
function esc(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function showToast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.remove("hidden");
  clearTimeout(showToast._timer);
  showToast._timer = setTimeout(() => toastEl.classList.add("hidden"), 2600);
}

function mediaUrl(raw) {
  if (!raw) return "/icons/icon-192.png";
  if (DRAWABLE_MAP[raw]) return DRAWABLE_MAP[raw];
  return raw;
}

function filterClass(name) {
  return AURA_FILTERS.includes(name) && name !== "Original" ? `filter-${name}` : "";
}

function isVideoMedia(post) {
  const url = post.imageData || "";
  if (post.mediaType !== "reel") return false;
  if (url.startsWith("drawable:") || url.startsWith("data:image/")) return false;
  if (/\.(jpg|jpeg|png|webp|gif)(\?|$)/i.test(url)) return false;
  return url.includes("video") || /\.(mp4|mov|webm|m4v)(\?|$)/i.test(url) || url.includes("/posts/") || url.includes("%2Fposts%2F");
}

function avatarUrl(url) {
  return url && url.trim() ? mediaUrl(url) : "/icons/icon-192.png";
}

function applyTheme() {
  const mode = state.myPrivate?.themeMode || "dark";
  const prefersLight = window.matchMedia("(prefers-color-scheme: light)").matches;
  const useLight = mode === "light" || (mode === "system" && prefersLight);
  document.body.classList.toggle("theme-light", useLight);
  document.body.classList.toggle("theme-dark", !useLight);
  document.getElementById("theme-color-meta").setAttribute("content", useLight ? "#FAFAFA" : "#0B0912");
}

function containsHiddenWord(text) {
  const prefs = state.myPrivate;
  if (prefs?.hideOffensiveComments === false) return false;
  const words = prefs?.hiddenWords || [];
  if (!words.length || !text) return false;
  const lower = text.toLowerCase();
  return words.some((w) => w && lower.includes(w.toLowerCase()));
}

function formatTime(ts) {
  if (!ts) return "Just now";
  const date = ts.toDate ? ts.toDate() : new Date(ts);
  const diffSec = Math.max(1, Math.floor((Date.now() - date.getTime()) / 1000));
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

function openModal(html) {
  modalContent.innerHTML = html;
  modalBackdrop.classList.remove("hidden");
}

function closeModal() {
  modalBackdrop.classList.add("hidden");
  modalContent.innerHTML = "";
}

modalBackdrop.addEventListener("click", (e) => {
  if (e.target === modalBackdrop) closeModal();
});

// Saved Login Info & Saved Accounts (Instagram-style Stay Signed In + 1-Tap Login)
const SAVE_LOGIN_KEY = "aura_save_login_enabled";
const SAVED_ACCOUNTS_KEY = "aura_saved_accounts";

function isSaveLoginEnabled() {
  const raw = localStorage.getItem(SAVE_LOGIN_KEY);
  return raw === null ? true : raw === "true";
}

function setSaveLoginEnabled(enabled) {
  localStorage.setItem(SAVE_LOGIN_KEY, enabled ? "true" : "false");
  const cb = document.getElementById("save-login-checkbox");
  if (cb) cb.checked = enabled;
}

function getSavedAccounts() {
  try {
    const parsed = JSON.parse(localStorage.getItem(SAVED_ACCOUNTS_KEY) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch (_) {
    return [];
  }
}

function upsertSavedAccount(user, profile = null) {
  if (!user || !isSaveLoginEnabled()) return;
  const providerId = user.providerData?.[0]?.providerId || (user.phoneNumber ? "phone" : "password");
  const entry = {
    uid: user.uid,
    displayName: (profile?.displayName || user.displayName || user.email?.split("@")[0] || (user.phoneNumber ? `Aura ${user.phoneNumber.slice(-4)}` : "Aura User")).slice(0, 50),
    username: (profile?.username || user.email?.split("@")[0] || (user.phoneNumber ? `aura_${user.phoneNumber.slice(-4)}` : "aura_user")).slice(0, 30),
    avatarUrl: profile?.avatarUrl || user.photoURL || "",
    email: user.email || "",
    phoneNumber: user.phoneNumber || "",
    providerId,
    lastUsedAt: Date.now(),
  };
  const next = [entry, ...getSavedAccounts().filter((a) => a.uid !== user.uid)].slice(0, 5);
  localStorage.setItem(SAVED_ACCOUNTS_KEY, JSON.stringify(next));
}

function removeSavedAccount(uid) {
  const next = getSavedAccounts().filter((a) => a.uid !== uid);
  localStorage.setItem(SAVED_ACCOUNTS_KEY, JSON.stringify(next));
  renderSavedAccounts();
}

async function applyAuthPersistence() {
  try {
    await setPersistence(auth, isSaveLoginEnabled() ? browserLocalPersistence : browserSessionPersistence);
  } catch (_) {}
}

function normalizeE164Phone(countryCode, rawDigits) {
  const raw = String(rawDigits || "").trim().replace(/[\s\-()]/g, "");
  if (!raw) return "";
  if (raw.startsWith("+")) return `+${raw.slice(1).replace(/\D/g, "")}`;
  const ccDigits = String(countryCode || "+92").replace(/\D/g, "");
  const localDigits = raw.replace(/\D/g, "").replace(/^0+/, "");
  return localDigits ? `+${ccDigits}${localDigits}` : "";
}

function renderSavedAccounts() {
  const box = document.getElementById("saved-accounts-box");
  if (!box) return;
  const list = getSavedAccounts();
  if (!list.length) {
    box.classList.add("hidden");
    box.innerHTML = "";
    return;
  }
  box.classList.remove("hidden");
  box.innerHTML = `
    <div class="saved-accounts-label">Saved logins (1-tap sign in)</div>
    ${list
      .map((acc) => {
        const sub = acc.phoneNumber || acc.email || acc.providerId || "Google Account";
        return `
      <div class="saved-account-card">
        <div class="saved-account-info">
          <img src="${esc(avatarUrl(acc.avatarUrl))}" class="avatar-sm" alt="" />
          <div class="saved-account-meta">
            <strong>@${esc(acc.username || acc.displayName)}</strong>
            <small>${esc(sub)}</small>
          </div>
        </div>
        <div class="saved-account-actions">
          <button type="button" class="btn-pill saved-login-go" data-uid="${esc(acc.uid)}">Log in</button>
          <button type="button" class="saved-account-remove" data-uid="${esc(acc.uid)}" aria-label="Remove saved account">&times;</button>
        </div>
      </div>`;
      })
      .join("")}
  `;
  box.querySelectorAll(".saved-account-remove").forEach((btn) => {
    btn.onclick = () => removeSavedAccount(btn.dataset.uid);
  });
  box.querySelectorAll(".saved-login-go").forEach((btn) => {
    btn.onclick = () => continueWithSavedAccount(list.find((a) => a.uid === btn.dataset.uid));
  });
}

async function continueWithSavedAccount(acc) {
  if (!acc) return;
  clearAuthMessages();
  setSaveLoginEnabled(true);
  await applyAuthPersistence();

  if (acc.providerId === "google.com") {
    const provider = new GoogleAuthProvider();
    if (acc.email) provider.setCustomParameters({ login_hint: acc.email });
    signInWithOAuthProvider(provider, "Google");
  } else if (acc.providerId === "facebook.com") {
    signInWithOAuthProvider(new FacebookAuthProvider(), "Facebook");
  } else if (acc.providerId === "apple.com") {
    const provider = new OAuthProvider("apple.com");
    provider.addScope("email");
    provider.addScope("name");
    signInWithOAuthProvider(provider, "Apple ID");
  } else if (acc.providerId === "phone" || acc.phoneNumber) {
    authMode = "login";
    authChannel = "phone";
    syncAuthUiMode();
    syncAuthChannel();
    const ccSel = document.getElementById("auth-country-code");
    const phoneInput = document.getElementById("auth-phone");
    const num = acc.phoneNumber || "";
    const matchedOpt = Array.from(ccSel.options).find((o) => num.startsWith(o.value));
    if (matchedOpt) {
      ccSel.value = matchedOpt.value;
      phoneInput.value = num.slice(matchedOpt.value.length);
    } else {
      phoneInput.value = num;
    }
    document.getElementById("send-otp-btn").click();
  } else {
    authMode = "login";
    authChannel = "email";
    syncAuthUiMode();
    syncAuthChannel();
    if (acc.email) document.getElementById("auth-email").value = acc.email;
    if (navigator.credentials?.get && window.PasswordCredential) {
      try {
        const cred = await navigator.credentials.get({ password: true, mediation: "optional" });
        if (cred?.id && cred?.password) {
          await signInWithEmailAndPassword(auth, cred.id, cred.password);
          return;
        }
      } catch (_) {}
    }
    document.getElementById("auth-password").focus();
    setAuthInfo(`Enter password for ${acc.email || acc.username} (or pick from Chrome autofill).`);
  }
}

// Authentication (Login & Create Account + Google / Facebook / Apple ID / Phone OTP / Email)
let authMode = "login";
let authChannel = "phone";
let phoneConfirmation = null;
let recaptchaVerifier = null;
let lastNormalizedPhone = "";
let resendTimerId = null;

function setAuthError(msg) {
  authInfoEl.classList.add("hidden");
  authErrorEl.textContent = msg;
  authErrorEl.classList.remove("hidden");
}

function setAuthInfo(msg) {
  authErrorEl.classList.add("hidden");
  authInfoEl.textContent = msg;
  authInfoEl.classList.remove("hidden");
}

function clearAuthMessages() {
  authErrorEl.classList.add("hidden");
  authInfoEl.classList.add("hidden");
}

function saveDraftIfCreating() {
  if (authMode !== "create") {
    localStorage.removeItem("aura_profile_draft");
    return {};
  }
  const fullName = document.getElementById("auth-fullname").value.trim();
  const username = document.getElementById("auth-username").value.trim();
  const draft = { fullName, username };
  localStorage.setItem("aura_profile_draft", JSON.stringify(draft));
  return draft;
}

function formatWebAuthError(err, providerLabel) {
  const code = err?.code || "";
  if (code === "auth/operation-not-allowed" || code === "auth/unauthorized-domain") {
    return `${providerLabel} isn't enabled yet in Firebase Console -> Authentication -> Sign-in method (or add this domain to Authorized Domains).`;
  }
  if (code === "auth/invalid-phone-number") {
    return "Invalid phone number. Select your country code and enter your mobile number without extra symbols.";
  }
  if (code === "auth/quota-exceeded" || code === "auth/too-many-requests") {
    return "Too many SMS attempts right now. Wait a bit or check Firebase Phone Auth quota.";
  }
  return err?.message || `${providerLabel} authentication failed.`;
}

function resetRecaptcha() {
  if (recaptchaVerifier) {
    try { recaptchaVerifier.clear(); } catch (_) {}
    recaptchaVerifier = null;
  }
  const container = document.getElementById("recaptcha-container");
  if (container) container.innerHTML = "";
}

function startResendCountdown(seconds = 30) {
  const btn = document.getElementById("resend-otp-btn");
  if (!btn) return;
  clearInterval(resendTimerId);
  let left = seconds;
  btn.disabled = true;
  btn.textContent = `Resend SMS Code (${left}s)`;
  resendTimerId = setInterval(() => {
    left -= 1;
    if (left <= 0) {
      clearInterval(resendTimerId);
      btn.disabled = false;
      btn.textContent = "Resend SMS Code";
    } else {
      btn.textContent = `Resend SMS Code (${left}s)`;
    }
  }, 1000);
}

function syncAuthUiMode() {
  const isCreate = authMode === "create";
  document.getElementById("mode-login-btn").classList.toggle("active", !isCreate);
  document.getElementById("mode-create-btn").classList.toggle("active", isCreate);
  document.getElementById("create-profile-fields").classList.toggle("hidden", !isCreate);
  document.getElementById("email-submit-btn").textContent = isCreate ? "Create ID with Email" : "Log in with Email";
  document.getElementById("forgot-pw-btn").classList.toggle("hidden", isCreate);
  document.getElementById("send-otp-btn").textContent = isCreate ? "Create ID with Phone Number" : "Send SMS Code";
  document.getElementById("google-btn-label").textContent = isCreate ? "Create account with Google" : "Continue with Google";
}

function syncAuthChannel() {
  const isEmail = authChannel === "email";
  document.getElementById("ch-email-btn").classList.toggle("active", isEmail);
  document.getElementById("ch-phone-btn").classList.toggle("active", !isEmail);
  document.getElementById("email-auth-form").classList.toggle("hidden", !isEmail);
  document.getElementById("phone-auth-box").classList.toggle("hidden", isEmail);
}

const saveLoginCb = document.getElementById("save-login-checkbox");
if (saveLoginCb) {
  saveLoginCb.checked = isSaveLoginEnabled();
  saveLoginCb.onchange = () => {
    setSaveLoginEnabled(saveLoginCb.checked);
  };
}
syncAuthUiMode();
syncAuthChannel();
renderSavedAccounts();

document.getElementById("mode-login-btn").onclick = () => {
  authMode = "login";
  clearAuthMessages();
  syncAuthUiMode();
};

document.getElementById("mode-create-btn").onclick = () => {
  authMode = "create";
  clearAuthMessages();
  syncAuthUiMode();
};

document.getElementById("ch-email-btn").onclick = () => {
  authChannel = "email";
  clearAuthMessages();
  syncAuthChannel();
};

document.getElementById("ch-phone-btn").onclick = () => {
  authChannel = "phone";
  clearAuthMessages();
  syncAuthChannel();
};

async function signInWithOAuthProvider(provider, label) {
  clearAuthMessages();
  saveDraftIfCreating();
  await applyAuthPersistence();
  try {
    await signInWithPopup(auth, provider);
  } catch (err) {
    if (err.code === "auth/popup-blocked" || err.code === "auth/operation-not-supported-in-this-browser") {
      try {
        await signInWithRedirect(auth, provider);
      } catch (redirErr) {
        setAuthError(formatWebAuthError(redirErr, label));
      }
    } else if (err.code !== "auth/popup-closed-by-user") {
      setAuthError(formatWebAuthError(err, label));
    }
  }
}

document.getElementById("google-signin-btn").onclick = () => {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  signInWithOAuthProvider(provider, "Google");
};

document.getElementById("facebook-signin-btn").onclick = () => {
  signInWithOAuthProvider(new FacebookAuthProvider(), "Facebook");
};

document.getElementById("apple-signin-btn").onclick = () => {
  const provider = new OAuthProvider("apple.com");
  provider.addScope("email");
  provider.addScope("name");
  signInWithOAuthProvider(provider, "Apple ID");
};

document.getElementById("email-auth-form").onsubmit = async (e) => {
  e.preventDefault();
  clearAuthMessages();
  await applyAuthPersistence();
  const email = document.getElementById("auth-email").value.trim();
  const password = document.getElementById("auth-password").value;
  const draft = saveDraftIfCreating();
  try {
    let cred;
    if (authMode === "create") {
      cred = await createUserWithEmailAndPassword(auth, email, password);
      if (draft.fullName) {
        await updateProfile(cred.user, { displayName: draft.fullName }).catch(() => {});
      }
    } else {
      cred = await signInWithEmailAndPassword(auth, email, password);
    }
    if (isSaveLoginEnabled() && navigator.credentials?.store && window.PasswordCredential) {
      try {
        await navigator.credentials.store(new window.PasswordCredential({
          id: email,
          password,
          name: draft.fullName || cred.user.displayName || email.split("@")[0],
        }));
      } catch (_) {}
    }
  } catch (err) {
    setAuthError(formatWebAuthError(err, "Email/Password"));
  }
};

document.getElementById("forgot-pw-btn").onclick = async () => {
  clearAuthMessages();
  const email = document.getElementById("auth-email").value.trim();
  if (!email) {
    setAuthError("Enter your email address above to receive a password reset email.");
    return;
  }
  try {
    await sendPasswordResetEmail(auth, email);
    setAuthInfo(`Password reset email sent to ${email}.`);
  } catch (err) {
    setAuthError(formatWebAuthError(err, "Password Reset"));
  }
};

async function sendPhoneOtpCode(phoneToUse = null) {
  clearAuthMessages();
  await applyAuthPersistence();
  const cc = document.getElementById("auth-country-code")?.value || "+92";
  const rawInput = document.getElementById("auth-phone")?.value || "";
  const phone = phoneToUse || normalizeE164Phone(cc, rawInput);
  if (!phone || !phone.startsWith("+") || phone.length < 9) {
    setAuthError("Enter a valid mobile number (e.g. 3001234567 or 03001234567).");
    return;
  }
  lastNormalizedPhone = phone;
  saveDraftIfCreating();
  const sendBtn = document.getElementById("send-otp-btn");
  if (sendBtn) {
    sendBtn.disabled = true;
    sendBtn.textContent = "Sending SMS...";
  }
  try {
    resetRecaptcha();
    recaptchaVerifier = new RecaptchaVerifier(auth, "recaptcha-container", { size: "invisible" });
    phoneConfirmation = await signInWithPhoneNumber(auth, phone, recaptchaVerifier);
    document.getElementById("phone-step-1").classList.add("hidden");
    document.getElementById("phone-step-2").classList.remove("hidden");
    setAuthInfo(`6-digit SMS verification code sent to ${phone}.`);
    startResendCountdown(30);
    document.getElementById("auth-otp")?.focus();
  } catch (err) {
    resetRecaptcha();
    setAuthError(formatWebAuthError(err, "Phone Auth"));
  } finally {
    if (sendBtn) {
      sendBtn.disabled = false;
      syncAuthUiMode();
    }
  }
}

document.getElementById("send-otp-btn").onclick = () => sendPhoneOtpCode();
document.getElementById("resend-otp-btn")?.addEventListener("click", () => {
  if (lastNormalizedPhone) sendPhoneOtpCode(lastNormalizedPhone);
});

document.getElementById("change-phone-btn").onclick = () => {
  clearInterval(resendTimerId);
  resetRecaptcha();
  document.getElementById("phone-step-2").classList.add("hidden");
  document.getElementById("phone-step-1").classList.remove("hidden");
  clearAuthMessages();
};

document.getElementById("verify-otp-btn").onclick = async () => {
  clearAuthMessages();
  const code = document.getElementById("auth-otp").value.trim();
  if (!phoneConfirmation || code.length < 6) {
    setAuthError("Enter the 6-digit SMS code sent to your phone.");
    return;
  }
  const verifyBtn = document.getElementById("verify-otp-btn");
  verifyBtn.disabled = true;
  verifyBtn.textContent = "Verifying...";
  try {
    const cred = await phoneConfirmation.confirm(code);
    const draft = saveDraftIfCreating();
    if (draft.fullName && !cred.user.displayName) {
      await updateProfile(cred.user, { displayName: draft.fullName }).catch(() => {});
    }
    clearInterval(resendTimerId);
    resetRecaptcha();
  } catch (err) {
    setAuthError(formatWebAuthError(err, "Phone OTP"));
  } finally {
    verifyBtn.disabled = false;
    verifyBtn.textContent = "Verify OTP";
  }
};

getRedirectResult(auth).catch((err) => {
  if (err?.message) setAuthError(formatWebAuthError(err, "Sign-In"));
});

async function ensureUserInitialized(user) {
  const uid = user.uid;
  const privateRef = doc(db, "users", uid);
  const profileRef = doc(db, "profiles", uid);
  const [privSnap, profSnap] = await Promise.all([getDoc(privateRef), getDoc(profileRef)]);

  let draft = {};
  try {
    draft = JSON.parse(localStorage.getItem("aura_profile_draft") || "{}");
  } catch (_) {}

  if (!privSnap.exists()) {
    await setDoc(privateRef, {
      userId: uid,
      email: user.email || "",
      darkModeEnabled: true,
      themeMode: "dark",
      notificationsMuted: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  }
  if (!profSnap.exists()) {
    const rawName = (
      draft.fullName ||
      user.displayName ||
      user.email?.split("@")[0] ||
      (user.phoneNumber ? `Aura ${user.phoneNumber.slice(-4)}` : "aura_creator")
    ).trim();
    const fallbackSeed = user.phoneNumber
      ? `aura_${user.phoneNumber.slice(-4)}_${uid.slice(0, 4)}`
      : rawName;
    const seed = (draft.username || fallbackSeed).trim();
    const sanitized = seed.toLowerCase().replace(/[^a-z0-9._]/g, "_").slice(0, 24) || `aura_${uid.slice(0, 6)}`;
    const newProfile = {
      userId: uid,
      username: sanitized.length < 2 ? `${sanitized}_aura` : sanitized,
      displayName: rawName.slice(0, 50),
      bio: "Sharing moments on Aura",
      avatarUrl: user.photoURL || "",
      website: "",
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      isVerified: false,
      isPublic: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    await setDoc(profileRef, newProfile);
    upsertSavedAccount(user, newProfile);
  } else {
    upsertSavedAccount(user, profSnap.data());
  }
  localStorage.removeItem("aura_profile_draft");
}

function clearSubscriptions() {
  state.unsubs.forEach((u) => {
    try { u(); } catch (_) {}
  });
  state.unsubs = [];
  if (state.chatUnsub) {
    try { state.chatUnsub(); } catch (_) {}
    state.chatUnsub = null;
  }
}

function startListeners(uid) {
  clearSubscriptions();
  const sortDesc = (a, b) => (b.createdAt?.seconds || 0) - (a.createdAt?.seconds || 0);

  state.unsubs.push(
    onSnapshot(doc(db, "users", uid), (snap) => {
      state.myPrivate = snap.exists() ? { id: snap.id, ...snap.data() } : null;
      applyTheme();
      render();
    }),
    onSnapshot(doc(db, "profiles", uid), (snap) => {
      state.myProfile = snap.exists() ? { id: snap.id, ...snap.data() } : null;
      if (state.user && state.myProfile) upsertSavedAccount(state.user, state.myProfile);
      if (state.myProfile?.avatarUrl) navAvatarEl.src = avatarUrl(state.myProfile.avatarUrl);
      render();
    }),
    onSnapshot(query(collection(db, "profiles"), where("isPublic", "==", true), limit(80)), (snap) => {
      state.profiles = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      render();
    }),
    onSnapshot(query(collection(db, "posts"), where("visibility", "==", "public"), limit(80)), (snap) => {
      state.posts = snap.docs.map((d) => ({ id: d.id, ...d.data() })).sort(sortDesc);
      render();
    }),
    onSnapshot(query(collection(db, "posts"), where("userId", "==", uid), limit(80)), (snap) => {
      state.myPosts = snap.docs.map((d) => ({ id: d.id, ...d.data() })).sort(sortDesc);
      render();
    }),
    onSnapshot(query(collection(db, "stories"), where("visibility", "==", "public"), limit(60)), (snap) => {
      state.stories = snap.docs.map((d) => ({ id: d.id, ...d.data() })).sort(sortDesc);
      render();
    }),
    onSnapshot(collection(db, "users", uid, "liked_posts"), (snap) => {
      state.likedPostIds = new Set(snap.docs.map((d) => d.id));
      render();
    }),
    onSnapshot(collection(db, "users", uid, "saved_posts"), (snap) => {
      state.savedPosts = snap.docs.map((d) => ({ id: d.id, ...d.data() })).sort(sortDesc);
      render();
    }),
    onSnapshot(collection(db, "users", uid, "following"), (snap) => {
      state.followingIds = new Set(snap.docs.map((d) => d.id));
      render();
    }),
    onSnapshot(query(collection(db, "users", uid, "notifications"), limit(60)), (snap) => {
      state.notifications = snap.docs.map((d) => ({ id: d.id, ...d.data() })).sort(sortDesc);
      render();
    }),
    onSnapshot(
      query(collection(db, "conversations"), where("participantIds", "array-contains", uid), limit(50)),
      (snap) => {
        state.conversations = snap.docs
          .map((d) => ({ id: d.id, ...d.data() }))
          .sort((a, b) => (b.updatedAt?.seconds || 0) - (a.updatedAt?.seconds || 0));
        render();
      }
    )
  );
}

onAuthStateChanged(auth, async (user) => {
  state.user = user;
  setTimeout(() => splashEl.classList.add("hidden"), 250);
  if (!user) {
    clearSubscriptions();
    shellEl.classList.add("hidden");
    authEl.classList.remove("hidden");
    renderSavedAccounts();
    updateInstallUI();
    return;
  }
  authEl.classList.add("hidden");
  shellEl.classList.remove("hidden");
  document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.tab === state.tab));
  updateInstallUI();
  try {
    await ensureUserInitialized(user);
    startListeners(user.uid);
  } catch (e) {
    showToast(e.message || "Failed to load account");
  }
});

// Bottom Navigation
document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
    state.tab = btn.dataset.tab;
    state.subView = null;
    state.settingsSubKey = null;
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b === btn));
    render();
  });
});

// Notification Helper
async function sendNotification(recipientId, type, postId = "", previewText = "") {
  const uid = state.user?.uid;
  if (!uid || !recipientId || recipientId === uid) return;
  try {
    const notifRef = doc(collection(db, "users", recipientId, "notifications"));
    await setDoc(notifRef, {
      recipientId,
      actorId: uid,
      actorUsername: state.myProfile?.username || "aura_user",
      actorAvatarUrl: state.myProfile?.avatarUrl || "",
      type,
      postId: postId || "",
      previewText: String(previewText || "").slice(0, 120),
      isRead: false,
      createdAt: serverTimestamp(),
    });
  } catch (_) {}
}

// Firestore Actions
async function toggleLike(post, forceLike = false) {
  const uid = state.user?.uid;
  if (!uid) return;
  const liked = state.likedPostIds.has(post.id);
  if (forceLike && liked) return;
  const batch = writeBatch(db);
  const likeRef = doc(db, "users", uid, "liked_posts", post.id);
  const postRef = doc(db, "posts", post.id);
  if (liked) {
    batch.delete(likeRef);
    batch.update(postRef, { likesCount: increment(-1) });
  } else {
    batch.set(likeRef, { userId: uid, postId: post.id, createdAt: serverTimestamp() });
    batch.update(postRef, { likesCount: increment(1) });
  }
  await batch.commit();
  if (!liked) sendNotification(post.userId, "like", post.id, post.caption || "Liked your post");
}

async function toggleSave(post) {
  const uid = state.user?.uid;
  if (!uid) return;
  const saved = state.savedPosts.some((s) => s.id === post.id);
  const ref = doc(db, "users", uid, "saved_posts", post.id);
  if (saved) {
    await deleteDoc(ref);
    showToast("Removed from Saved");
  } else {
    await setDoc(ref, {
      userId: uid,
      postId: post.id,
      imageData: post.imageData,
      mediaType: post.mediaType || "photo",
      caption: post.caption || "",
      authorUsername: post.authorUsername || "aura_user",
      createdAt: serverTimestamp(),
    });
    showToast("Saved");
  }
}

async function toggleFollow(targetUserId) {
  const uid = state.user?.uid;
  const target = state.profiles.find((p) => p.userId === targetUserId);
  if (!uid || !target || targetUserId === uid) return;
  const isFollowing = state.followingIds.has(targetUserId);
  const batch = writeBatch(db);
  const edgeRef = doc(db, "users", uid, "following", targetUserId);
  const targetRef = doc(db, "profiles", targetUserId);
  const myRef = doc(db, "profiles", uid);
  if (isFollowing) {
    batch.delete(edgeRef);
    batch.update(targetRef, { followersCount: increment(-1), updatedAt: serverTimestamp() });
    batch.update(myRef, { followingCount: increment(-1), updatedAt: serverTimestamp() });
  } else {
    batch.set(edgeRef, {
      userId: uid,
      targetUserId,
      targetUsername: target.username || "aura_user",
      targetDisplayName: target.displayName || "",
      targetAvatarUrl: target.avatarUrl || "",
      createdAt: serverTimestamp(),
    });
    batch.update(targetRef, { followersCount: increment(1), updatedAt: serverTimestamp() });
    batch.update(myRef, { followingCount: increment(1), updatedAt: serverTimestamp() });
  }
  await batch.commit();
  if (!isFollowing) sendNotification(targetUserId, "follow", "", "Started following you");
}

async function sendDirectMessage(otherProfile, text, sharedPostId = "") {
  const uid = state.user?.uid;
  if (!uid || !otherProfile || !text.trim()) return;
  const participants = [uid, otherProfile.userId].sort();
  const convId = participants.join("_");
  const convRef = doc(db, "conversations", convId);
  const msgRef = doc(collection(convRef, "messages"));
  const existing = await getDoc(convRef);
  const myProf = state.myProfile || { userId: uid, displayName: "Aura User", username: "aura_user", avatarUrl: "" };
  const userA = participants[0] === uid ? myProf : otherProfile;
  const userB = participants[1] === uid ? myProf : otherProfile;

  const batch = writeBatch(db);
  if (!existing.exists()) {
    batch.set(convRef, {
      participantIds: participants,
      userAId: participants[0],
      userAName: (userA.displayName || userA.username || "Aura User").slice(0, 50),
      userAAvatar: userA.avatarUrl || "",
      userBId: participants[1],
      userBName: (userB.displayName || userB.username || "Aura User").slice(0, 50),
      userBAvatar: userB.avatarUrl || "",
      lastMessage: text.trim().slice(0, 1000),
      lastSenderId: uid,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  } else {
    batch.update(convRef, {
      lastMessage: text.trim().slice(0, 1000),
      lastSenderId: uid,
      updatedAt: serverTimestamp(),
    });
  }
  batch.set(msgRef, {
    senderId: uid,
    conversationId: convId,
    participantIds: participants,
    text: text.trim().slice(0, 1000),
    sharedPostId: sharedPostId || "",
    createdAt: serverTimestamp(),
  });
  await batch.commit();
  return convId;
}

async function updateSettings(updates, msg) {
  const uid = state.user?.uid;
  if (!uid) return;
  await updateDoc(doc(db, "users", uid), { ...updates, updatedAt: serverTimestamp() });
  if (msg) showToast(msg);
}

async function toggleUserInList(fieldKey, currentList, targetUid, addMsg, remMsg) {
  const exists = (currentList || []).includes(targetUid);
  const next = exists ? currentList.filter((id) => id !== targetUid) : Array.from(new Set([...(currentList || []), targetUid])).slice(0, 200);
  await updateSettings({ [fieldKey]: next }, exists ? remMsg : addMsg);
}

async function setAccountPrivacy(isPublic) {
  const uid = state.user?.uid;
  if (!uid) return;
  await updateDoc(doc(db, "profiles", uid), { isPublic, updatedAt: serverTimestamp() });
  showToast(isPublic ? "Account switched to Public" : "Account switched to Private");
}

// Storage Upload Helper (with Ultra HD 1440px JPEG fallback)
function compressImageToDataUri(file, highQuality = true) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const maxDim = highQuality ? 1440 : 720;
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        const ctx = canvas.getContext("2d");
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        let q = highQuality ? 0.88 : 0.66;
        let out = canvas.toDataURL("image/jpeg", q);
        while (out.length > 330000 && q > 0.35) {
          q -= 0.1;
          out = canvas.toDataURL("image/jpeg", q);
        }
        resolve(out);
      };
      img.onerror = reject;
      img.src = reader.result;
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

async function uploadMediaFile(folder, file, onProgress) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Not signed in");
  const safeName = `${Date.now()}_${file.name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
  const ref = storageRef(storage, `users/${uid}/${folder}/${safeName}`);
  try {
    return await new Promise((resolve, reject) => {
      const task = uploadBytesResumable(ref, file);
      task.on(
        "state_changed",
        (snap) => {
          if (snap.totalBytes > 0) onProgress(Math.round((snap.bytesTransferred / snap.totalBytes) * 100));
        },
        (err) => reject(err),
        async () => {
          const url = await getDownloadURL(task.snapshot.ref);
          resolve(url);
        }
      );
    });
  } catch (err) {
    if (file.type.startsWith("image/")) {
      const highQuality = state.myPrivate?.uploadHighQuality !== false && !state.myPrivate?.dataSaverEnabled;
      return await compressImageToDataUri(file, highQuality);
    }
    throw new Error("Enable Firebase Storage in Firebase Console -> Build -> Storage to upload videos.");
  }
}

// Open Other User Profile Helper
function openUserProfile(uid) {
  if (!uid) return;
  if (uid === state.user?.uid) {
    state.subView = null;
    state.tab = "profile";
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.tab === "profile"));
    render();
    return;
  }
  state.selectedUserId = uid;
  state.subView = "user_profile";
  render();
}

function openChatWithUser(otherProfile) {
  state.selectedChatUser = otherProfile;
  state.subView = "chat";
  if (state.chatUnsub) {
    try { state.chatUnsub(); } catch (_) {}
    state.chatUnsub = null;
  }
  const uid = state.user?.uid;
  if (uid && otherProfile?.userId) {
    const convId = [uid, otherProfile.userId].sort().join("_");
    state.chatMessages = [];
    state.chatUnsub = onSnapshot(
      query(collection(db, "conversations", convId, "messages"), orderBy("createdAt", "asc"), limit(120)),
      (snap) => {
        state.chatMessages = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
        if (state.subView === "chat") renderChat();
      }
    );
  }
  render();
}

// Modals: Comments, Story Viewer, Share Post, Edit Profile
function openCommentsModal(post) {
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h3 style="margin:0">Comments</h3>
      <button class="icon-btn" id="close-comments">&times;</button>
    </div>
    <div id="comments-list" style="display:flex;flex-direction:column;gap:12px;margin-bottom:14px;min-height:100px">
      <p style="color:var(--text-muted)">Loading comments...</p>
    </div>
    <form id="comment-form" style="display:flex;gap:8px">
      <input id="comment-input" class="input-field" placeholder="Add a comment..." required maxlength="500" />
      <button class="btn-pill" type="submit">Post</button>
    </form>
  `);
  document.getElementById("close-comments").onclick = closeModal;

  const listEl = document.getElementById("comments-list");
  const blocked = new Set(state.myPrivate?.blockedUserIds || []);
  const unsub = onSnapshot(
    query(collection(db, "posts", post.id, "comments"), where("visibility", "==", "public"), limit(100)),
    (snap) => {
      const items = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .filter((c) => !blocked.has(c.userId) && !containsHiddenWord(c.text))
        .sort((a, b) => (a.createdAt?.seconds || 0) - (b.createdAt?.seconds || 0));
      listEl.innerHTML = items.length
        ? items
            .map(
              (c) => `
            <div style="display:flex;gap:10px;align-items:flex-start">
              <img src="${esc(avatarUrl(c.authorAvatarUrl))}" class="avatar-sm" alt="" />
              <div>
                <strong>${esc(c.authorUsername)}</strong>
                <span style="margin-left:6px">${esc(c.text)}</span>
                <div class="post-time">${esc(formatTime(c.createdAt))}</div>
              </div>
            </div>`
            )
            .join("")
        : `<p style="color:var(--text-muted)">No comments yet. Start the conversation.</p>`;
    }
  );
  modalBackdrop.addEventListener("click", function cleanup(e) {
    if (e.target === modalBackdrop) {
      unsub();
      modalBackdrop.removeEventListener("click", cleanup);
    }
  });
  document.getElementById("close-comments").addEventListener("click", () => unsub(), { once: true });

  document.getElementById("comment-form").onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById("comment-input");
    const text = input.value.trim();
    if (!text) return;
    const uid = state.user.uid;
    const commentRef = doc(collection(db, "posts", post.id, "comments"));
    const batch = writeBatch(db);
    batch.set(commentRef, {
      userId: uid,
      postId: post.id,
      authorUsername: state.myProfile?.username || "aura_user",
      authorAvatarUrl: state.myProfile?.avatarUrl || "",
      text: text.slice(0, 500),
      visibility: "public",
      createdAt: serverTimestamp(),
    });
    batch.update(doc(db, "posts", post.id), { commentsCount: increment(1) });
    input.value = "";
    await batch.commit();
    sendNotification(post.userId, "comment", post.id, text);
  };
}

function openShareModal(post) {
  const recipients = state.profiles.filter((p) => p.userId !== state.user?.uid);
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
      <h3 style="margin:0">Share post</h3>
      <button class="icon-btn" id="close-share">&times;</button>
    </div>
    <div style="display:flex;flex-direction:column;gap:10px;max-height:240px;overflow-y:auto">
      ${
        recipients.length
          ? recipients
              .map(
                (u) => `
          <div style="display:flex;align-items:center;justify-content:space-between;gap:10px">
            <div style="display:flex;align-items:center;gap:10px">
              <img src="${esc(avatarUrl(u.avatarUrl))}" class="avatar-sm" alt="" />
              <div><strong>${esc(u.username)}</strong><div style="font-size:12px;color:var(--text-muted)">${esc(u.displayName)}</div></div>
            </div>
            <button class="btn-pill send-dm-share" data-uid="${esc(u.userId)}">Send</button>
          </div>`
              )
              .join("")
          : `<p style="color:var(--text-muted)">No other Aura creators found yet.</p>`
      }
    </div>
    <button id="share-external-btn" class="btn-secondary">Share externally / Copy link</button>
  `);
  document.getElementById("close-share").onclick = closeModal;
  modalContent.querySelectorAll(".send-dm-share").forEach((btn) => {
    btn.onclick = async () => {
      const target = recipients.find((r) => r.userId === btn.dataset.uid);
      if (target) {
        await sendDirectMessage(target, `Shared @${post.authorUsername}'s post: ${post.caption || "Check this out"}`, post.id);
        closeModal();
        showToast(`Sent to @${target.username}`);
      }
    };
  });
  document.getElementById("share-external-btn").onclick = async () => {
    closeModal();
    const shareData = { title: "Aura", text: `Check out @${post.authorUsername} on Aura: ${post.caption || ""}`, url: window.location.origin };
    if (navigator.share) await navigator.share(shareData).catch(() => {});
    else {
      await navigator.clipboard?.writeText(window.location.origin);
      showToast("Post link copied!");
    }
  };
}

function openStoryViewer(startIndex, list = state.stories) {
  let idx = startIndex;
  const renderStory = () => {
    const story = list[idx];
    if (!story) {
      closeModal();
      return;
    }
    openModal(`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <div class="post-author">
          <img src="${esc(avatarUrl(story.authorAvatarUrl))}" class="avatar-sm" alt="" />
          <div>
            <div class="post-username">${esc(story.authorUsername)}</div>
            <div class="post-time">${esc(formatTime(story.createdAt))}</div>
          </div>
        </div>
        <div style="display:flex;gap:4px;align-items:center">
          ${story.userId === state.user?.uid ? `<button class="btn-secondary" id="delete-story-btn" style="width:auto;margin:0;padding:4px 10px;min-height:32px;font-size:12px;color:#EF4444">Delete</button>` : ""}
          <button class="icon-btn" id="close-story">&times;</button>
        </div>
      </div>
      <div style="position:relative;border-radius:16px;overflow:hidden;background:#000">
        <img src="${esc(mediaUrl(story.imageData))}" style="width:100%;max-height:56dvh;object-fit:cover;display:block" alt="" />
        ${story.captionText ? `<div style="position:absolute;bottom:14px;left:14px;right:14px;background:rgba(0,0,0,0.6);color:#fff;padding:8px 12px;border-radius:12px">${esc(story.captionText)}</div>` : ""}
      </div>
      ${
        story.userId !== state.user?.uid
          ? `<form id="story-reply-form" style="display:flex;gap:8px;margin-top:10px">
               <input id="story-reply-input" class="input-field" placeholder="Reply to story..." required />
               <button class="btn-pill" type="submit">Send</button>
             </form>`
          : ""
      }
      <div style="display:flex;justify-content:space-between;margin-top:10px">
        <button class="btn-secondary" style="width:auto;margin:0" id="story-prev" ${idx <= 0 ? "disabled" : ""}>Previous</button>
        <button class="btn-secondary" style="width:auto;margin:0" id="story-next">${idx + 1 < list.length ? "Next" : "Done"}</button>
      </div>
    `);
    document.getElementById("close-story").onclick = closeModal;
    document.getElementById("delete-story-btn")?.addEventListener("click", async () => {
      await deleteDoc(doc(db, "stories", story.id));
      closeModal();
      showToast("Story deleted");
    });
    document.getElementById("story-reply-form")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const txt = document.getElementById("story-reply-input").value.trim();
      if (!txt) return;
      const author = state.profiles.find((p) => p.userId === story.userId) || {
        userId: story.userId,
        username: story.authorUsername,
        displayName: story.authorUsername,
        avatarUrl: story.authorAvatarUrl || "",
      };
      await sendDirectMessage(author, `Replied to your story: ${txt}`);
      closeModal();
      showToast("Reply sent");
    });
    document.getElementById("story-prev").onclick = () => { idx = Math.max(0, idx - 1); renderStory(); };
    document.getElementById("story-next").onclick = () => {
      if (idx + 1 < list.length) { idx++; renderStory(); } else closeModal();
    };
  };
  renderStory();
}

function openEditProfileModal() {
  const p = state.myProfile || {};
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h3 style="margin:0">Edit profile</h3>
      <button class="icon-btn" id="close-edit-prof">&times;</button>
    </div>
    <form id="edit-prof-form" style="display:flex;flex-direction:column;gap:10px">
      <label style="font-size:12px;color:var(--text-muted)">Change profile photo</label>
      <input id="edit-avatar-file" type="file" accept="image/*" class="input-field" />
      <input id="edit-username" class="input-field" value="${esc(p.username || "")}" placeholder="Username" required minlength="2" maxlength="30" />
      <input id="edit-display" class="input-field" value="${esc(p.displayName || "")}" placeholder="Name" required maxlength="50" />
      <textarea id="edit-bio" class="textarea-field" rows="3" placeholder="Bio" maxlength="200">${esc(p.bio || "")}</textarea>
      <input id="edit-website" class="input-field" value="${esc(p.website || "")}" placeholder="Website URL" maxlength="120" />
      <button id="save-prof-btn" class="btn-primary" type="submit">Save changes</button>
    </form>
  `);
  document.getElementById("close-edit-prof").onclick = closeModal;
  document.getElementById("edit-prof-form").onsubmit = async (e) => {
    e.preventDefault();
    const btn = document.getElementById("save-prof-btn");
    btn.disabled = true;
    btn.textContent = "Saving...";
    try {
      let avatar = p.avatarUrl || "";
      const file = document.getElementById("edit-avatar-file").files[0];
      if (file) {
        avatar = await uploadMediaFile("avatars", file, () => {});
      }
      await updateDoc(doc(db, "profiles", state.user.uid), {
        username: document.getElementById("edit-username").value.trim(),
        displayName: document.getElementById("edit-display").value.trim(),
        bio: document.getElementById("edit-bio").value.trim(),
        website: document.getElementById("edit-website").value.trim(),
        avatarUrl: avatar,
        updatedAt: serverTimestamp(),
      });
      closeModal();
      showToast("Profile updated");
    } catch (err) {
      showToast(err.message || "Update failed");
      btn.disabled = false;
      btn.textContent = "Save changes";
    }
  };
}

// Feed Post Renderer
function visibleFeedPosts() {
  const blocked = new Set(state.myPrivate?.blockedUserIds || []);
  const muted = new Set(state.myPrivate?.mutedUserIds || []);
  const favs = new Set(state.myPrivate?.favoriteUserIds || []);
  return state.posts.filter((p) => {
    if (blocked.has(p.userId) || muted.has(p.userId)) return false;
    if (containsHiddenWord(p.caption)) return false;
    if (state.feedFilter === "following" && !state.followingIds.has(p.userId) && p.userId !== state.user?.uid) return false;
    if (state.feedFilter === "favorites" && !favs.has(p.userId) && p.userId !== state.user?.uid) return false;
    return true;
  });
}

function renderPostCard(post) {
  const liked = state.likedPostIds.has(post.id);
  const saved = state.savedPosts.some((s) => s.id === post.id);
  const hideLikes = state.myPrivate?.hideLikeCounts === true && post.userId !== state.user?.uid;
  const mediaSrc = mediaUrl(post.imageData);
  const isVid = isVideoMedia(post);
  const isMine = post.userId === state.user?.uid;
  const isFollowing = state.followingIds.has(post.userId);
  const fClass = filterClass(post.filterName);

  return `
    <article class="post-card" data-post-id="${esc(post.id)}">
      <div class="post-header">
        <div class="post-author open-author-btn" data-uid="${esc(post.userId)}" style="cursor:pointer">
          <img src="${esc(avatarUrl(post.authorAvatarUrl))}" class="avatar-sm" alt="" />
          <div>
            <div class="post-username">${esc(post.authorUsername)} ${post.authorVerified ? "&#10004;&#65039;" : ""}</div>
            ${post.location ? `<div class="post-location">${esc(post.location)}</div>` : ""}
          </div>
        </div>
        <div style="display:flex;gap:6px;align-items:center">
          ${!isMine ? `<button class="btn-pill follow-btn" data-uid="${esc(post.userId)}">${isFollowing ? "Following" : "Follow"}</button>` : `<button class="btn-secondary archive-post-btn" data-post="${esc(post.id)}" style="width:auto;margin:0;padding:4px 10px;min-height:30px;font-size:11px">${post.visibility === "private" ? "Restore" : "Archive"}</button><button class="btn-secondary delete-post-btn" data-post="${esc(post.id)}" style="width:auto;margin:0;padding:4px 10px;min-height:30px;font-size:11px;color:#EF4444">Delete</button>`}
        </div>
      </div>
      <div class="post-media-wrap dbltap-like" data-post="${esc(post.id)}">
        ${isVid
          ? `<video class="post-media ${fClass}" src="${esc(mediaSrc)}" controls playsinline preload="metadata"></video>`
          : `<img class="post-media ${fClass}" src="${esc(mediaSrc)}" alt="${esc(post.caption || "Post")}" loading="lazy" />`}
        <div class="heart-burst">&#10084;&#65039;</div>
      </div>
      <div class="post-actions">
        <div class="post-actions-left">
          <button class="icon-btn like-btn" data-post="${esc(post.id)}" aria-label="Like" style="color:${liked ? "#FF4D6D" : "var(--text)"}">
            ${liked ? "&#10084;&#65039;" : "&#9825;"}
          </button>
          <button class="icon-btn comment-btn" data-post="${esc(post.id)}" aria-label="Comments">&#128172;</button>
          <button class="icon-btn share-btn" data-post="${esc(post.id)}" aria-label="Share">&#9992;</button>
        </div>
        <button class="icon-btn save-btn" data-post="${esc(post.id)}" aria-label="Save">
          ${saved ? "&#128278;" : "&#9734;"}
        </button>
      </div>
      <div class="post-meta">
        <div class="post-likes">${hideLikes ? (liked ? "Liked by you" : "Likes hidden") : `${post.likesCount || 0} likes`}</div>
        <div><span class="post-caption-user open-author-btn" data-uid="${esc(post.userId)}" style="cursor:pointer">${esc(post.authorUsername)}</span>${esc(post.caption)}</div>
        <div class="post-time">${esc(formatTime(post.createdAt))} &bull; ${post.commentsCount || 0} comments</div>
      </div>
    </article>
  `;
}

function findPostById(id) {
  return state.posts.find((x) => x.id === id) || state.myPosts.find((x) => x.id === id);
}

function bindPostHandlers(root) {
  root.querySelectorAll(".open-author-btn").forEach((el) => {
    el.onclick = () => openUserProfile(el.dataset.uid);
  });
  root.querySelectorAll(".dbltap-like").forEach((wrap) => {
    let lastTap = 0;
    wrap.addEventListener("click", () => {
      const now = Date.now();
      if (now - lastTap < 300) {
        const p = findPostById(wrap.dataset.post);
        if (p) toggleLike(p, true);
        const burst = wrap.querySelector(".heart-burst");
        if (burst) {
          burst.classList.add("active");
          setTimeout(() => burst.classList.remove("active"), 650);
        }
      }
      lastTap = now;
    });
  });
  root.querySelectorAll(".like-btn").forEach((btn) => {
    btn.onclick = () => {
      const p = findPostById(btn.dataset.post);
      if (p) toggleLike(p);
    };
  });
  root.querySelectorAll(".save-btn").forEach((btn) => {
    btn.onclick = () => {
      const p = findPostById(btn.dataset.post);
      if (p) toggleSave(p);
    };
  });
  root.querySelectorAll(".comment-btn").forEach((btn) => {
    btn.onclick = () => {
      const p = findPostById(btn.dataset.post);
      if (p) openCommentsModal(p);
    };
  });
  root.querySelectorAll(".share-btn").forEach((btn) => {
    btn.onclick = () => {
      const p = findPostById(btn.dataset.post);
      if (p) openShareModal(p);
    };
  });
  root.querySelectorAll(".follow-btn").forEach((btn) => {
    btn.onclick = () => toggleFollow(btn.dataset.uid);
  });
  root.querySelectorAll(".archive-post-btn").forEach((btn) => {
    btn.onclick = async () => {
      const p = findPostById(btn.dataset.post);
      if (!p) return;
      const nextVis = p.visibility === "private" ? "public" : "private";
      await updateDoc(doc(db, "posts", p.id), { visibility: nextVis, updatedAt: serverTimestamp() });
      showToast(nextVis === "private" ? "Moved to Archive" : "Restored to profile");
    };
  });
  root.querySelectorAll(".delete-post-btn").forEach((btn) => {
    btn.onclick = async () => {
      await deleteDoc(doc(db, "posts", btn.dataset.post));
      showToast("Post deleted");
    };
  });
}

// Views
function renderHome() {
  const posts = visibleFeedPosts();
  const blocked = new Set(state.myPrivate?.blockedUserIds || []);
  const muted = new Set(state.myPrivate?.mutedUserIds || []);
  const visibleStories = state.stories.filter((s) => !blocked.has(s.userId) && !muted.has(s.userId));
  const unreadNotifs = state.notifications.filter((n) => !n.isRead).length;

  viewEl.innerHTML = `
    <header class="top-bar">
      <div class="brand-wordmark">Aura</div>
      <div style="display:flex;gap:4px;align-items:center">
        <button class="btn-secondary" id="toggle-fav-filter" style="width:auto;margin:0;min-height:32px;padding:4px 10px;font-size:12px">
          ${state.feedFilter === "favorites" ? "Favorites" : state.feedFilter === "following" ? "Following" : "For You"}
        </button>
        <div class="icon-btn-wrap">
          <button class="icon-btn" id="open-notifs-btn" aria-label="Activity">&#9825;</button>
          ${unreadNotifs > 0 ? `<span class="badge-dot"></span>` : ""}
        </div>
        <div class="icon-btn-wrap">
          <button class="icon-btn" id="open-inbox-btn" aria-label="Messages">&#9992;</button>
          ${state.conversations.length > 0 ? `<span class="badge-count">${Math.min(9, state.conversations.length)}</span>` : ""}
        </div>
      </div>
    </header>
    <section class="stories-row">
      <button class="story-chip" id="add-story-chip">
        <div class="story-ring add-ring">
          <img src="${esc(avatarUrl(state.myProfile?.avatarUrl))}" alt="" />
          <span class="story-plus-badge">+</span>
        </div>
        <span class="story-label">Your story</span>
      </button>
      ${visibleStories
        .map(
          (s, i) => `
          <button class="story-chip open-story-btn" data-idx="${i}">
            <div class="story-ring">
              <img src="${esc(avatarUrl(s.authorAvatarUrl || s.imageData))}" alt="" />
            </div>
            <span class="story-label">${esc(s.authorUsername)}</span>
          </button>`
        )
        .join("")}
    </section>
    <section id="feed-list">
      ${posts.length ? posts.map(renderPostCard).join("") : `<div style="padding:40px 20px;text-align:center;color:var(--text-muted)">No posts yet. Tap + Create to share your first photo or Reel!</div>`}
    </section>
  `;

  document.getElementById("toggle-fav-filter").onclick = () => {
    const order = ["all", "following", "favorites"];
    state.feedFilter = order[(order.indexOf(state.feedFilter) + 1) % order.length];
    render();
  };
  document.getElementById("open-notifs-btn").onclick = () => {
    state.subView = "notifications";
    render();
  };
  document.getElementById("open-inbox-btn").onclick = () => {
    state.subView = "inbox";
    render();
  };
  document.getElementById("add-story-chip").onclick = () => {
    state.createType = "story";
    state.tab = "create";
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.tab === "create"));
    render();
  };
  viewEl.querySelectorAll(".open-story-btn").forEach((btn) => {
    btn.onclick = () => openStoryViewer(Number(btn.dataset.idx), visibleStories);
  });
  bindPostHandlers(viewEl);
}

function renderNotifications() {
  viewEl.innerHTML = `
    <header class="top-bar">
      <div style="display:flex;align-items:center;gap:8px">
        <button class="icon-btn" id="notifs-back">&larr;</button>
        <strong>Activity</strong>
      </div>
    </header>
    <div>
      ${
        state.notifications.length
          ? state.notifications
              .map(
                (n) => `
          <div class="notif-row ${n.isRead ? "" : "unread"} open-notif-item" data-id="${esc(n.id)}" data-uid="${esc(n.actorId)}" data-post="${esc(n.postId || "")}">
            <img src="${esc(avatarUrl(n.actorAvatarUrl))}" class="avatar-sm" alt="" />
            <div style="flex:1">
              <div><strong>@${esc(n.actorUsername)}</strong> ${n.type === "follow" ? "started following you" : n.type === "comment" ? `commented: "${esc(n.previewText || "")}"` : "liked your post"}</div>
              <div class="post-time">${esc(formatTime(n.createdAt))}</div>
            </div>
          </div>`
              )
              .join("")
          : `<p style="padding:28px;text-align:center;color:var(--text-muted)">No notifications yet.</p>`
      }
    </div>
  `;
  document.getElementById("notifs-back").onclick = () => {
    state.subView = null;
    render();
  };
  viewEl.querySelectorAll(".open-notif-item").forEach((row) => {
    row.onclick = async () => {
      updateDoc(doc(db, "users", state.user.uid, "notifications", row.dataset.id), { isRead: true }).catch(() => {});
      if (row.dataset.post) {
        const p = findPostById(row.dataset.post);
        if (p) {
          openModal(`<div style="display:flex;justify-content:flex-end"><button class="icon-btn" id="close-np">&times;</button></div>${renderPostCard(p)}`);
          document.getElementById("close-np").onclick = closeModal;
          bindPostHandlers(modalContent);
          return;
        }
      }
      openUserProfile(row.dataset.uid);
    };
  });
}

function renderInbox() {
  const uid = state.user?.uid;
  const blocked = new Set(state.myPrivate?.blockedUserIds || []);
  const others = state.profiles.filter((p) => p.userId !== uid && !blocked.has(p.userId));

  viewEl.innerHTML = `
    <header class="top-bar">
      <div style="display:flex;align-items:center;gap:8px">
        <button class="icon-btn" id="inbox-back">&larr;</button>
        <strong>${esc(state.myProfile?.username || "Messages")}</strong>
      </div>
    </header>
    <div style="padding:12px 16px;font-size:12px;font-weight:700;color:var(--text-muted)">ACTIVE CHATS</div>
    <div>
      ${
        state.conversations.length
          ? state.conversations
              .map((c) => {
                const isA = c.userAId === uid;
                const otherId = isA ? c.userBId : c.userAId;
                const otherName = isA ? c.userBName : c.userAName;
                const otherAvatar = isA ? c.userBAvatar : c.userAAvatar;
                return `
            <div class="notif-row open-chat-conv" data-uid="${esc(otherId)}" data-name="${esc(otherName)}" data-avatar="${esc(otherAvatar || "")}">
              <img src="${esc(avatarUrl(otherAvatar))}" class="avatar-sm" alt="" />
              <div style="flex:1;min-width:0">
                <strong>${esc(otherName)}</strong>
                <div style="font-size:13px;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(c.lastMessage || "")}</div>
              </div>
              <span style="font-size:11px;color:var(--text-muted)">${esc(formatTime(c.updatedAt))}</span>
            </div>`;
              })
              .join("")
          : `<p style="padding:10px 16px;color:var(--text-muted)">No conversations yet — tap someone below to message.</p>`
      }
    </div>
    <div style="padding:16px 16px 6px;font-size:12px;font-weight:700;color:var(--text-muted)">START NEW MESSAGE</div>
    <div>
      ${others
        .map(
          (u) => `
        <div class="notif-row open-chat-user" data-uid="${esc(u.userId)}">
          <img src="${esc(avatarUrl(u.avatarUrl))}" class="avatar-sm" alt="" />
          <div style="flex:1">
            <strong>${esc(u.username)}</strong>
            <div style="font-size:12px;color:var(--text-muted)">${esc(u.displayName)}</div>
          </div>
          <button class="btn-pill">Message</button>
        </div>`
        )
        .join("")}
    </div>
  `;
  document.getElementById("inbox-back").onclick = () => {
    state.subView = null;
    render();
  };
  viewEl.querySelectorAll(".open-chat-conv").forEach((el) => {
    el.onclick = () => {
      const matched = state.profiles.find((p) => p.userId === el.dataset.uid) || {
        userId: el.dataset.uid,
        username: el.dataset.name,
        displayName: el.dataset.name,
        avatarUrl: el.dataset.avatar,
      };
      openChatWithUser(matched);
    };
  });
  viewEl.querySelectorAll(".open-chat-user").forEach((el) => {
    el.onclick = () => {
      const matched = others.find((p) => p.userId === el.dataset.uid);
      if (matched) openChatWithUser(matched);
    };
  });
}

function renderChat() {
  const other = state.selectedChatUser || {};
  const uid = state.user?.uid;
  viewEl.innerHTML = `
    <div class="chat-wrap">
      <header class="top-bar">
        <div style="display:flex;align-items:center;gap:10px">
          <button class="icon-btn" id="chat-back">&larr;</button>
          <img src="${esc(avatarUrl(other.avatarUrl))}" class="avatar-sm" alt="" />
          <div>
            <strong>${esc(other.displayName || other.username || "Chat")}</strong>
            <div style="font-size:11px;color:var(--text-muted)">@${esc(other.username || "aura_user")}</div>
          </div>
        </div>
      </header>
      <div id="chat-scroll" class="chat-messages">
        ${
          state.chatMessages.length
            ? state.chatMessages
                .map(
                  (m) => `
            <div class="chat-bubble ${m.senderId === uid ? "mine" : ""}">
              ${esc(m.text)}
            </div>`
                )
                .join("")
            : `<p style="margin:auto;color:var(--text-muted)">Say hello to @${esc(other.username || "creator")}!</p>`
        }
      </div>
      <form id="chat-send-form" class="chat-input-bar">
        <input id="chat-msg-input" class="input-field" placeholder="Message..." required maxlength="1000" />
        <button class="btn-pill" type="submit">Send</button>
      </form>
    </div>
  `;
  const scrollBox = document.getElementById("chat-scroll");
  scrollBox.scrollTop = scrollBox.scrollHeight;

  document.getElementById("chat-back").onclick = () => {
    if (state.chatUnsub) {
      try { state.chatUnsub(); } catch (_) {}
      state.chatUnsub = null;
    }
    state.subView = "inbox";
    render();
  };
  document.getElementById("chat-send-form").onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById("chat-msg-input");
    const txt = input.value.trim();
    if (!txt) return;
    input.value = "";
    await sendDirectMessage(other, txt);
  };
}

function renderExplore() {
  const q = state.exploreQuery.trim().toLowerCase();
  const blocked = new Set(state.myPrivate?.blockedUserIds || []);
  const filtered = state.posts.filter((p) => {
    if (blocked.has(p.userId)) return false;
    if (!q) return true;
    return (
      (p.caption || "").toLowerCase().includes(q) ||
      (p.authorUsername || "").toLowerCase().includes(q) ||
      (p.location || "").toLowerCase().includes(q)
    );
  });

  viewEl.innerHTML = `
    <header class="top-bar">
      <input id="explore-search" class="settings-search" style="margin:0;width:100%" value="${esc(state.exploreQuery)}" placeholder="Search posts, creators, locations..." />
    </header>
    <div class="grid-3">
      ${filtered
        .map((p) => {
          const src = mediaUrl(p.imageData);
          const isVid = isVideoMedia(p);
          return `
            <div class="grid-tile open-post-tile" data-post="${esc(p.id)}">
              ${isVid ? `<video src="${esc(src)}" muted playsinline preload="metadata"></video>` : `<img class="${filterClass(p.filterName)}" src="${esc(src)}" alt="" loading="lazy" />`}
              ${p.mediaType === "reel" ? `<span class="grid-badge">&#9654; Reel</span>` : ""}
            </div>`;
        })
        .join("")}
    </div>
  `;

  const input = document.getElementById("explore-search");
  input.oninput = (e) => {
    state.exploreQuery = e.target.value;
    renderExplore();
    const nextInput = document.getElementById("explore-search");
    nextInput.focus();
    nextInput.setSelectionRange(nextInput.value.length, nextInput.value.length);
  };
  viewEl.querySelectorAll(".open-post-tile").forEach((tile) => {
    tile.onclick = () => {
      const p = state.posts.find((x) => x.id === tile.dataset.post);
      if (p) {
        openModal(`<div style="display:flex;justify-content:flex-end"><button class="icon-btn" id="close-pd">&times;</button></div>${renderPostCard(p)}`);
        document.getElementById("close-pd").onclick = closeModal;
        bindPostHandlers(modalContent);
      }
    };
  });
}

function renderReels() {
  const reels = state.posts.filter((p) => p.mediaType === "reel");
  const list = reels.length ? reels : state.posts;
  viewEl.innerHTML = `
    <div class="reels-feed">
      ${list
        .map((r) => {
          const src = mediaUrl(r.imageData);
          const isVid = isVideoMedia(r);
          const liked = state.likedPostIds.has(r.id);
          return `
            <section class="reel-slide">
              ${isVid
                ? `<video class="reel-media ${filterClass(r.filterName)}" src="${esc(src)}" autoplay loop muted playsinline onclick="this.muted=!this.muted"></video>`
                : `<img class="reel-media ${filterClass(r.filterName)}" src="${esc(src)}" alt="" />`}
              <div class="reel-overlay">
                <div class="open-author-btn" data-uid="${esc(r.userId)}" style="font-weight:800;font-size:15px;cursor:pointer">@${esc(r.authorUsername)}</div>
                <div style="font-size:13px;margin-top:4px">${esc(r.caption || "")}</div>
                <div style="font-size:11px;opacity:0.8;margin-top:6px">&#9835; ${esc(r.audioTitle || "Original Audio")}</div>
              </div>
              <div class="reel-rail">
                <button class="like-btn" data-post="${esc(r.id)}" style="color:${liked ? "#FF4D6D" : "#fff"}">${liked ? "&#10084;" : "&#9825;"}<div style="font-size:10px">${r.likesCount || 0}</div></button>
                <button class="comment-btn" data-post="${esc(r.id)}">&#128172;<div style="font-size:10px">${r.commentsCount || 0}</div></button>
                <button class="share-btn" data-post="${esc(r.id)}">&#9992;</button>
                <button class="save-btn" data-post="${esc(r.id)}">&#128278;</button>
              </div>
            </section>`;
        })
        .join("")}
    </div>
  `;
  bindPostHandlers(viewEl);
}

function renderCreate() {
  const accept = state.createType === "reel" ? "video/*,image/*" : "image/*";
  const showFilters = state.createType !== "story" && state.previewObjectUrl && !state.selectedFile?.type.startsWith("video/");
  viewEl.innerHTML = `
    <header class="top-bar">
      <strong>Create</strong>
      <span style="font-size:12px;color:var(--text-muted)">Ultra HD &bull; Cloud Sync</span>
    </header>
    <div class="create-card">
      <div class="seg-row">
        <button class="seg-btn ${state.createType === "photo" ? "active" : ""}" data-type="photo">Post</button>
        <button class="seg-btn ${state.createType === "reel" ? "active" : ""}" data-type="reel">Reel</button>
        <button class="seg-btn ${state.createType === "story" ? "active" : ""}" data-type="story">Story</button>
      </div>
      <label class="upload-box" for="media-picker">
        <input id="media-picker" type="file" accept="${accept}" hidden />
        <div><strong>${state.selectedFile ? esc(state.selectedFile.name) : `Tap to pick a ${state.createType === "reel" ? "Video or Photo" : "Photo"}`}</strong></div>
        <small style="color:var(--text-muted)">Supports phone camera &amp; gallery (Ultra HD 1440px)</small>
        ${state.previewObjectUrl
          ? (state.selectedFile?.type.startsWith("video/")
              ? `<video class="upload-preview" src="${esc(state.previewObjectUrl)}" controls playsinline></video>`
              : `<img class="upload-preview ${filterClass(state.selectedFilter)}" src="${esc(state.previewObjectUrl)}" alt="" />`)
          : ""}
      </label>
      ${
        showFilters
          ? `<div class="filter-strip">
               ${AURA_FILTERS.map(
                 (f) => `
                 <button type="button" class="filter-chip pick-filter ${state.selectedFilter === f ? "active" : ""}" data-filter="${f}">
                   <img class="filter-thumb ${filterClass(f)}" src="${esc(state.previewObjectUrl)}" alt="${f}" />
                   <span>${f}</span>
                 </button>`
               ).join("")}
             </div>`
          : ""
      }
      <textarea id="create-caption" class="textarea-field" rows="3" placeholder="Write a caption..."></textarea>
      ${state.createType !== "story" ? `<input id="create-location" class="input-field" placeholder="Add location (optional)" />` : ""}
      ${state.isUploading ? `<div class="progress-track"><div class="progress-fill" style="width:${state.uploadProgress}%"></div></div>` : ""}
      <button id="publish-btn" class="btn-primary" ${state.isUploading ? "disabled" : ""}>
        ${state.isUploading ? `Uploading ${state.uploadProgress}%...` : `Share ${state.createType.toUpperCase()}`}
      </button>
    </div>
  `;

  viewEl.querySelectorAll(".seg-btn").forEach((b) => {
    b.onclick = () => {
      state.createType = b.dataset.type;
      state.selectedFile = null;
      state.previewObjectUrl = null;
      state.selectedFilter = "Original";
      renderCreate();
    };
  });
  viewEl.querySelectorAll(".pick-filter").forEach((b) => {
    b.onclick = () => {
      state.selectedFilter = b.dataset.filter;
      renderCreate();
    };
  });
  document.getElementById("media-picker").onchange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    state.selectedFile = file;
    state.previewObjectUrl = URL.createObjectURL(file);
    renderCreate();
  };
  document.getElementById("publish-btn").onclick = async () => {
    if (!state.selectedFile) {
      showToast("Please select a photo or video first");
      return;
    }
    const uid = state.user.uid;
    const caption = document.getElementById("create-caption").value.trim();
    const location = document.getElementById("create-location")?.value.trim() || "";
    state.isUploading = true;
    state.uploadProgress = 5;
    renderCreate();

    try {
      const folder = state.createType === "story" ? "stories" : "posts";
      const uploadedUrl = await uploadMediaFile(folder, state.selectedFile, (pct) => {
        state.uploadProgress = pct;
        const fill = viewEl.querySelector(".progress-fill");
        const btn = document.getElementById("publish-btn");
        if (fill) fill.style.width = `${pct}%`;
        if (btn) btn.textContent = `Uploading ${pct}%...`;
      });

      if (state.createType === "story") {
        await setDoc(doc(collection(db, "stories")), {
          userId: uid,
          authorUsername: state.myProfile?.username || "aura_user",
          authorAvatarUrl: state.myProfile?.avatarUrl || "",
          imageData: uploadedUrl,
          captionText: caption.slice(0, 200),
          backgroundHex: "#110D1C",
          visibility: "public",
          createdAt: serverTimestamp(),
        });
      } else {
        const batch = writeBatch(db);
        const postRef = doc(collection(db, "posts"));
        batch.set(postRef, {
          userId: uid,
          authorUsername: state.myProfile?.username || "aura_user",
          authorAvatarUrl: state.myProfile?.avatarUrl || "",
          authorVerified: Boolean(state.myProfile?.isVerified),
          caption: caption.slice(0, 2200),
          mediaType: state.createType === "reel" ? "reel" : "photo",
          imageData: uploadedUrl,
          filterName: state.selectedFilter || "Original",
          location: location.slice(0, 80),
          audioTitle: state.createType === "reel" ? "Original Audio" : "",
          tags: [],
          likesCount: 0,
          commentsCount: 0,
          visibility: "public",
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
        batch.update(doc(db, "profiles", uid), {
          postsCount: increment(1),
          updatedAt: serverTimestamp(),
        });
        await batch.commit();
      }

      state.selectedFile = null;
      state.previewObjectUrl = null;
      state.selectedFilter = "Original";
      state.isUploading = false;
      state.tab = "home";
      document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.tab === "home"));
      showToast("Published to Aura!");
      render();
    } catch (err) {
      state.isUploading = false;
      renderCreate();
      showToast(err.message || "Upload failed");
    }
  };
}

function renderProfile(targetUid = state.user?.uid) {
  const isOwn = targetUid === state.user?.uid;
  const p = isOwn ? state.myProfile || {} : state.profiles.find((x) => x.userId === targetUid) || {};
  const sourcePosts = isOwn
    ? state.myPosts.filter((x) => x.visibility === "public")
    : state.posts.filter((x) => x.userId === targetUid && x.visibility === "public");
  const list =
    isOwn && state.profileTab === "saved"
      ? state.savedPosts
      : state.profileTab === "reels"
      ? sourcePosts.filter((x) => x.mediaType === "reel")
      : sourcePosts;
  const isFollowing = state.followingIds.has(targetUid);

  viewEl.innerHTML = `
    <header class="top-bar">
      <div style="display:flex;align-items:center;gap:8px">
        ${!isOwn ? `<button class="icon-btn" id="profile-back">&larr;</button>` : ""}
        <strong>${p.isPublic === false ? "&#128274; " : ""}${esc(p.username || "aura_creator")}</strong>
      </div>
      ${isOwn ? `<button class="icon-btn" id="open-settings-btn" aria-label="Settings and activity">&#9776;</button>` : ""}
    </header>
    <div class="profile-header">
      <div class="profile-top">
        <img src="${esc(avatarUrl(p.avatarUrl))}" class="avatar-lg" alt="" />
        <div class="profile-stats">
          <div><div class="stat-num">${sourcePosts.length}</div><div class="stat-label">Posts</div></div>
          <div><div class="stat-num">${p.followersCount || 0}</div><div class="stat-label">Followers</div></div>
          <div><div class="stat-num">${p.followingCount || 0}</div><div class="stat-label">Following</div></div>
        </div>
      </div>
      <div class="profile-bio">
        <strong>${esc(p.displayName || "Aura Creator")}</strong>
        <div>${esc(p.bio || "")}</div>
        ${p.website ? `<div style="color:var(--blue)">${esc(p.website)}</div>` : ""}
      </div>
      <div class="profile-actions">
        ${
          isOwn
            ? `<button class="btn-secondary" style="margin:0" id="edit-profile-btn">Edit profile</button>
               <button class="btn-secondary" style="margin:0" id="share-profile-btn">Share Aura</button>`
            : `<button class="btn-primary" style="min-height:40px" id="other-follow-btn">${isFollowing ? "Following" : "Follow"}</button>
               <button class="btn-secondary" style="margin:0" id="other-msg-btn">Message</button>`
        }
      </div>
    </div>
    <div class="profile-tabs">
      <button class="${state.profileTab === "posts" ? "active" : ""}" data-ptab="posts">Posts</button>
      <button class="${state.profileTab === "reels" ? "active" : ""}" data-ptab="reels">Reels</button>
      ${isOwn ? `<button class="${state.profileTab === "saved" ? "active" : ""}" data-ptab="saved">Saved</button>` : ""}
    </div>
    <div class="grid-3">
      ${list
        .map((item) => {
          const src = mediaUrl(item.imageData);
          const isVid = isVideoMedia(item);
          return `<div class="grid-tile open-profile-item" data-id="${esc(item.postId || item.id)}">
            ${isVid ? `<video src="${esc(src)}" muted playsinline preload="metadata"></video>` : `<img class="${filterClass(item.filterName)}" src="${esc(src)}" alt="" />`}
          </div>`;
        })
        .join("")}
    </div>
  `;

  document.getElementById("profile-back")?.addEventListener("click", () => {
    state.subView = null;
    render();
  });
  document.getElementById("open-settings-btn")?.addEventListener("click", () => {
    state.subView = "settings";
    render();
  });
  document.getElementById("edit-profile-btn")?.addEventListener("click", openEditProfileModal);
  document.getElementById("other-follow-btn")?.addEventListener("click", () => toggleFollow(targetUid));
  document.getElementById("other-msg-btn")?.addEventListener("click", () => openChatWithUser(p));
  document.getElementById("share-profile-btn")?.addEventListener("click", async () => {
    const shareData = { title: "Aura", text: `Follow @${p.username} on Aura`, url: window.location.origin };
    if (navigator.share) await navigator.share(shareData).catch(() => {});
    else {
      await navigator.clipboard?.writeText(window.location.origin);
      showToast("Aura link copied!");
    }
  });
  viewEl.querySelectorAll("[data-ptab]").forEach((b) => {
    b.onclick = () => {
      state.profileTab = b.dataset.ptab;
      renderProfile(targetUid);
    };
  });
  viewEl.querySelectorAll(".open-profile-item").forEach((tile) => {
    tile.onclick = () => {
      const post = findPostById(tile.dataset.id);
      if (post) {
        openModal(`<div style="display:flex;justify-content:flex-end"><button class="icon-btn" id="close-pp">&times;</button></div>${renderPostCard(post)}`);
        document.getElementById("close-pp").onclick = closeModal;
        bindPostHandlers(modalContent);
      }
    };
  });
}

// Instagram Settings & Activity (full parity with Android Firestore schema)
function renderSettings() {
  const priv = state.myPrivate || {};
  const prof = state.myProfile || {};
  const sessionMins = Math.floor((Date.now() - state.sessionStartMs) / 60000);

  if (state.settingsSubKey) {
    renderSettingsSub();
    return;
  }

  const rows = [
    { key: "accounts_center", sec: "Your account", title: "Accounts Center", val: prof.username || "" },
    { key: "saved", sec: "How you use Aura", title: "Saved", val: `${state.savedPosts.length}` },
    { key: "archive", sec: "How you use Aura", title: "Archive", val: `${state.myPosts.filter((p) => p.visibility === "private").length}` },
    { key: "activity", sec: "How you use Aura", title: "Your activity", val: `${sessionMins}m today` },
    { key: "notifications", sec: "How you use Aura", title: "Notifications", val: priv.notificationsMuted ? "Paused" : "Active" },
    { key: "time", sec: "How you use Aura", title: "Time management", val: priv.dailyTimeLimitMinutes ? `${priv.dailyTimeLimitMinutes}m` : "Off" },
    { key: "favorites", sec: "What you see", title: "Favorites", val: `${(priv.favoriteUserIds || []).length}` },
    { key: "muted", sec: "What you see", title: "Muted accounts", val: `${(priv.mutedUserIds || []).length}` },
    { key: "likes", sec: "What you see", title: "Like and share counts", val: priv.hideLikeCounts ? "Hidden" : "Visible" },
    { key: "privacy", sec: "Who can see your content", title: "Account privacy", val: prof.isPublic === false ? "Private" : "Public" },
    { key: "close_friends", sec: "Who can see your content", title: "Close Friends", val: `${(priv.closeFriendIds || []).length}` },
    { key: "blocked", sec: "Who can see your content", title: "Blocked", val: `${(priv.blockedUserIds || []).length}` },
    { key: "messages_ctrl", sec: "How others can interact", title: "Messages and story replies", val: priv.allowMessagesFrom || "Everyone" },
    { key: "comments_ctrl", sec: "How others can interact", title: "Comments", val: priv.allowCommentsFrom || "Everyone" },
    { key: "hidden_words", sec: "How others can interact", title: "Hidden Words", val: `${(priv.hiddenWords || []).length}` },
    { key: "appearance", sec: "Your app and media", title: "Appearance", val: priv.themeMode || "dark" },
    { key: "language", sec: "Your app and media", title: "Language", val: priv.appLanguage || "English" },
    { key: "data", sec: "Your app and media", title: "Data usage and media quality", val: priv.dataSaverEnabled ? "Saver" : "Ultra HD" },
    { key: "about", sec: "More info and support", title: "About Aura Web & Chrome App", val: "v1.0" },
  ].filter((r) => !state.settingsSearch || r.title.toLowerCase().includes(state.settingsSearch.toLowerCase()));

  const grouped = rows.reduce((acc, r) => {
    (acc[r.sec] = acc[r.sec] || []).push(r);
    return acc;
  }, {});

  viewEl.innerHTML = `
    <div class="settings-page">
      <header class="top-bar">
        <div style="display:flex;align-items:center;gap:8px">
          <button class="icon-btn" id="settings-back">&larr;</button>
          <strong>Settings and activity</strong>
        </div>
      </header>
      <input id="settings-search-input" class="settings-search" value="${esc(state.settingsSearch)}" placeholder="Search settings" />
      ${
        !isStandalone
          ? `<div style="padding:4px 16px 8px">
               <button id="settings-install-btn" class="btn-primary" style="min-height:42px;font-size:14px">Install Aura App in Chrome / Phone</button>
             </div>`
          : ""
      }
      ${Object.entries(grouped)
        .map(
          ([sec, items]) => `
          <div class="settings-section-title">${esc(sec)}</div>
          ${items
            .map(
              (item) => `
              <button class="settings-row open-sub-btn" data-key="${esc(item.key)}">
                <span>${esc(item.title)}</span>
                <span class="settings-row-right">${esc(item.val)} &rsaquo;</span>
              </button>`
            )
            .join("")}`
        )
        .join("")}
      <div style="padding:20px 16px">
        <button id="logout-btn" class="btn-secondary" style="color:#EF4444;border-color:rgba(239,68,68,0.3)">Log out</button>
      </div>
    </div>
  `;

  document.getElementById("settings-back").onclick = () => {
    state.subView = null;
    render();
  };
  document.getElementById("settings-install-btn")?.addEventListener("click", triggerInstall);
  document.getElementById("settings-search-input").oninput = (e) => {
    state.settingsSearch = e.target.value;
    renderSettings();
    const el = document.getElementById("settings-search-input");
    el.focus();
    el.setSelectionRange(el.value.length, el.value.length);
  };
  viewEl.querySelectorAll(".open-sub-btn").forEach((b) => {
    b.onclick = () => {
      state.settingsSubKey = b.dataset.key;
      renderSettings();
    };
  });
  document.getElementById("logout-btn").onclick = () => {
    const uname = state.myProfile?.username || "aura_user";
    openModal(`
      <h3 style="margin:0 0 8px">Log out of @${esc(uname)}?</h3>
      <p style="color:var(--text-muted);font-size:13px;margin:0 0 14px">If you remember your login info, you can sign back in with 1 tap without typing details again.</p>
      <label class="save-login-row" style="margin-bottom:16px">
        <input id="logout-remember-cb" type="checkbox" ${isSaveLoginEnabled() ? "checked" : ""} />
        <span><strong>Remember login info</strong> for @${esc(uname)}</span>
      </label>
      <div style="display:flex;gap:8px">
        <button id="cancel-logout-btn" class="btn-secondary" style="margin:0;flex:1">Cancel</button>
        <button id="confirm-logout-btn" class="btn-primary" style="flex:1;background:#EF4444">Log out</button>
      </div>
    `);
    document.getElementById("cancel-logout-btn").onclick = closeModal;
    document.getElementById("confirm-logout-btn").onclick = async () => {
      const keepSaved = document.getElementById("logout-remember-cb").checked;
      setSaveLoginEnabled(keepSaved);
      if (keepSaved && state.user) {
        upsertSavedAccount(state.user, state.myProfile);
      } else if (state.user) {
        removeSavedAccount(state.user.uid);
      }
      closeModal();
      await signOut(auth);
      showToast("Logged out");
    };
  };
}

function renderUserListSub(fieldKey, listIds, addLabel, remLabel, desc) {
  const candidates = state.profiles.filter((u) => u.userId !== state.user?.uid);
  return `
    <p style="padding:0 16px;color:var(--text-muted);font-size:13px">${esc(desc)}</p>
    ${
      candidates.length
        ? candidates
            .map((u) => {
              const added = (listIds || []).includes(u.userId);
              return `
          <div class="settings-row">
            <div style="display:flex;align-items:center;gap:10px">
              <img src="${esc(avatarUrl(u.avatarUrl))}" class="avatar-sm" alt="" />
              <div><strong>@${esc(u.username)}</strong><div style="font-size:12px;color:var(--text-muted)">${esc(u.displayName)}</div></div>
            </div>
            <button class="btn-pill toggle-ulist" data-field="${fieldKey}" data-uid="${esc(u.userId)}">${added ? remLabel : addLabel}</button>
          </div>`;
            })
            .join("")
        : `<p style="padding:16px;color:var(--text-muted)">No other users found yet.</p>`
    }
  `;
}

function renderSettingsSub() {
  const priv = state.myPrivate || {};
  const prof = state.myProfile || {};
  const key = state.settingsSubKey;
  let body = "";

  if (key === "accounts_center") {
    const savedList = getSavedAccounts();
    const linkedInfo = state.user?.phoneNumber || priv.email || state.user?.email || state.user?.uid;
    const linkedSet = new Set((state.user?.providerData || []).map((p) => p.providerId));
    body = `
      <div style="padding:16px">
        <p style="color:var(--text-muted);font-size:13px">Signed in as <strong>${esc(linkedInfo)}</strong></p>
        <button id="sub-edit-profile" class="btn-primary">Edit Account Details</button>
      </div>
      <div class="settings-section-title">Connected accounts</div>
      ${[
        { id: "google.com", label: "Google" },
        { id: "facebook.com", label: "Facebook" },
        { id: "apple.com", label: "Apple ID" },
      ]
        .map((p) => {
          const isLinked = linkedSet.has(p.id);
          return `
        <div class="settings-row">
          <div><strong>${p.label}</strong><div style="font-size:12px;color:var(--text-muted)">${isLinked ? "Connected" : `Connect with ${p.label}`}</div></div>
          ${
            isLinked
              ? `<span style="color:var(--blue);font-weight:700;font-size:13px">Connected &#10004;</span>`
              : `<button class="btn-pill link-provider-btn" data-pid="${p.id}" data-label="${p.label}">Connect</button>`
          }
        </div>`;
        })
        .join("")}
      <label class="settings-row">
        <div>
          <strong>Save login info</strong>
          <div style="font-size:12px;color:var(--text-muted);margin-top:4px">Stay signed in automatically &amp; keep 1-tap login ready</div>
        </div>
        <input id="toggle-save-login" type="checkbox" class="switch-toggle" ${isSaveLoginEnabled() ? "checked" : ""} />
      </label>
      <div class="settings-section-title">Saved accounts on this device</div>
      ${
        savedList.length
          ? savedList
              .map(
                (acc) => `
          <div class="settings-row">
            <div style="display:flex;align-items:center;gap:10px">
              <img src="${esc(avatarUrl(acc.avatarUrl))}" class="avatar-sm" alt="" />
              <div><strong>@${esc(acc.username || acc.displayName)}</strong><div style="font-size:12px;color:var(--text-muted)">${esc(acc.phoneNumber || acc.email || acc.providerId)}</div></div>
            </div>
            <button class="btn-secondary rm-saved-acc" data-uid="${esc(acc.uid)}" style="width:auto;margin:0;padding:4px 10px">Remove</button>
          </div>`
              )
              .join("")
          : `<p style="padding:12px 16px;color:var(--text-muted);font-size:13px">No saved accounts yet.</p>`
      }
    `;
  } else if (key === "privacy") {
    const isPriv = prof.isPublic === false;
    body = `
      <label class="settings-row">
        <div>
          <strong>Private account</strong>
          <div style="font-size:12px;color:var(--text-muted);margin-top:4px">Only approved followers see your profile and stories.</div>
        </div>
        <input id="toggle-private" type="checkbox" class="switch-toggle" ${isPriv ? "checked" : ""} />
      </label>
    `;
  } else if (key === "appearance") {
    const cur = priv.themeMode || "dark";
    body = ["system", "light", "dark"]
      .map(
        (m) => `
        <button class="settings-row pick-theme" data-mode="${m}">
          <span style="text-transform:capitalize">${m}</span>
          <span>${cur === m ? "&#10004;" : ""}</span>
        </button>`
      )
      .join("");
  } else if (key === "language") {
    const cur = priv.appLanguage || "English";
    body = ["English", "Urdu (Roman)", "Hindi", "Arabic", "Spanish"]
      .map(
        (l) => `
        <button class="settings-row pick-opt" data-field="appLanguage" data-val="${esc(l)}">
          <span>${esc(l)}</span>
          <span>${cur === l ? "&#10004;" : ""}</span>
        </button>`
      )
      .join("");
  } else if (key === "messages_ctrl") {
    const cur = priv.allowMessagesFrom || "Everyone";
    body = ["Everyone", "People you follow", "No one"]
      .map(
        (o) => `
        <button class="settings-row pick-opt" data-field="allowMessagesFrom" data-val="${esc(o)}">
          <span>${esc(o)}</span>
          <span>${cur === o ? "&#10004;" : ""}</span>
        </button>`
      )
      .join("");
  } else if (key === "comments_ctrl") {
    const cur = priv.allowCommentsFrom || "Everyone";
    body = ["Everyone", "People you follow", "No one"]
      .map(
        (o) => `
        <button class="settings-row pick-opt" data-field="allowCommentsFrom" data-val="${esc(o)}">
          <span>${esc(o)}</span>
          <span>${cur === o ? "&#10004;" : ""}</span>
        </button>`
      )
      .join("");
  } else if (key === "favorites") {
    body = renderUserListSub("favoriteUserIds", priv.favoriteUserIds, "Add", "Remove", "Favorites appear first in your feed filter.");
  } else if (key === "muted") {
    body = renderUserListSub("mutedUserIds", priv.mutedUserIds, "Mute", "Unmute", "Hide posts and stories from muted accounts.");
  } else if (key === "blocked") {
    body = renderUserListSub("blockedUserIds", priv.blockedUserIds, "Block", "Unblock", "Blocked users are hidden across Aura.");
  } else if (key === "close_friends") {
    body = renderUserListSub("closeFriendIds", priv.closeFriendIds, "Add", "Remove", "Share close-friends stories.");
  } else if (key === "likes") {
    body = `
      <label class="settings-row">
        <div>
          <strong>Hide like counts</strong>
          <div style="font-size:12px;color:var(--text-muted)">Hides like totals on feed posts from others.</div>
        </div>
        <input id="toggle-hide-likes" type="checkbox" class="switch-toggle" ${priv.hideLikeCounts ? "checked" : ""} />
      </label>
    `;
  } else if (key === "notifications") {
    body = `
      <label class="settings-row"><span>Pause all notifications</span><input type="checkbox" class="switch-toggle notif-t" data-field="notificationsMuted" ${priv.notificationsMuted ? "checked" : ""} /></label>
      <label class="settings-row"><span>Likes</span><input type="checkbox" class="switch-toggle notif-t" data-field="notifyLikes" ${priv.notifyLikes !== false ? "checked" : ""} /></label>
      <label class="settings-row"><span>Comments</span><input type="checkbox" class="switch-toggle notif-t" data-field="notifyComments" ${priv.notifyComments !== false ? "checked" : ""} /></label>
      <label class="settings-row"><span>New followers</span><input type="checkbox" class="switch-toggle notif-t" data-field="notifyFollows" ${priv.notifyFollows !== false ? "checked" : ""} /></label>
    `;
  } else if (key === "time") {
    const cur = priv.dailyTimeLimitMinutes || 0;
    body = [0, 15, 30, 60, 120]
      .map(
        (m) => `
        <button class="settings-row pick-limit" data-mins="${m}">
          <span>${m === 0 ? "Off" : `${m} minutes / day`}</span>
          <span>${cur === m ? "&#10004;" : ""}</span>
        </button>`
      )
      .join("");
  } else if (key === "hidden_words") {
    const words = priv.hiddenWords || [];
    body = `
      <div style="padding:16px">
        <form id="add-hw-form" style="display:flex;gap:8px;margin-bottom:14px">
          <input id="hw-input" class="input-field" placeholder="Add word or phrase..." required />
          <button class="btn-pill" type="submit">Add</button>
        </form>
        ${words.map((w) => `<div class="settings-row"><span>${esc(w)}</span><button class="btn-secondary rm-hw" data-word="${esc(w)}" style="width:auto;margin:0;padding:4px 10px">Remove</button></div>`).join("") || `<p style="color:var(--text-muted)">No hidden words yet.</p>`}
      </div>
    `;
  } else if (key === "data") {
    body = `
      <label class="settings-row"><span>Data saver</span><input type="checkbox" class="switch-toggle data-t" data-field="dataSaverEnabled" ${priv.dataSaverEnabled ? "checked" : ""} /></label>
      <label class="settings-row"><span>Upload in Ultra HD (1440px)</span><input type="checkbox" class="switch-toggle data-t" data-field="uploadHighQuality" ${priv.uploadHighQuality !== false ? "checked" : ""} /></label>
    `;
  } else if (key === "saved") {
    body = `<div class="grid-3">${state.savedPosts.map((s) => `<div class="grid-tile"><img src="${esc(mediaUrl(s.imageData))}" alt="" /></div>`).join("") || `<p style="padding:16px;color:var(--text-muted)">No saved posts yet.</p>`}</div>`;
  } else if (key === "archive") {
    const archived = state.myPosts.filter((p) => p.visibility === "private");
    body = archived.length
      ? archived.map((a) => `<div class="settings-row"><span>${esc(a.caption || "Archived post")}</span><button class="btn-pill unarchive-btn" data-id="${esc(a.id)}">Show on profile</button></div>`).join("")
      : `<p style="padding:16px;color:var(--text-muted)">No archived posts.</p>`;
  } else if (key === "about") {
    body = `
      <div style="padding:16px;line-height:1.6">
        <p><strong>Aura Chrome &amp; Web App (PWA)</strong></p>
        <p style="color:var(--text-muted)">Version 1.0 &bull; Real-time Cloud Firestore, Firebase Storage &amp; IndexedDB Offline Cache.</p>
        <button id="about-install-btn" class="btn-primary">Install on Chrome / Phone</button>
      </div>
    `;
  } else {
    body = `<div style="padding:16px">
      <p><strong>Posts published:</strong> ${state.myPosts.length}</p>
      <p><strong>Saved items:</strong> ${state.savedPosts.length}</p>
      <p><strong>Liked posts:</strong> ${state.likedPostIds.size}</p>
      <p><strong>Active session:</strong> ${Math.floor((Date.now() - state.sessionStartMs) / 60000)}m</p>
    </div>`;
  }

  viewEl.innerHTML = `
    <header class="top-bar">
      <div style="display:flex;align-items:center;gap:8px">
        <button class="icon-btn" id="sub-back">&larr;</button>
        <strong style="text-transform:capitalize">${esc(key.replace(/_/g, " "))}</strong>
      </div>
    </header>
    ${body}
  `;

  document.getElementById("sub-back").onclick = () => {
    state.settingsSubKey = null;
    renderSettings();
  };
  document.getElementById("sub-edit-profile")?.addEventListener("click", openEditProfileModal);
  viewEl.querySelectorAll(".link-provider-btn").forEach((b) => {
    b.onclick = async () => {
      if (!state.user) return;
      const pid = b.dataset.pid;
      const label = b.dataset.label;
      let provider;
      if (pid === "google.com") provider = new GoogleAuthProvider();
      else if (pid === "facebook.com") provider = new FacebookAuthProvider();
      else {
        provider = new OAuthProvider("apple.com");
        provider.addScope("email");
        provider.addScope("name");
      }
      try {
        await linkWithPopup(state.user, provider);
        showToast(`${label} connected!`);
        renderSettingsSub();
      } catch (err) {
        showToast(formatWebAuthError(err, label));
      }
    };
  });
  document.getElementById("toggle-save-login")?.addEventListener("change", (e) => {
    setSaveLoginEnabled(e.target.checked);
    if (e.target.checked && state.user) upsertSavedAccount(state.user, state.myProfile);
    showToast(e.target.checked ? "Login info saved on this device" : "Saved login turned off");
    renderSettingsSub();
  });
  viewEl.querySelectorAll(".rm-saved-acc").forEach((b) => {
    b.onclick = () => {
      removeSavedAccount(b.dataset.uid);
      showToast("Removed saved login");
      renderSettingsSub();
    };
  });
  document.getElementById("about-install-btn")?.addEventListener("click", triggerInstall);
  document.getElementById("toggle-private")?.addEventListener("change", (e) => setAccountPrivacy(!e.target.checked));
  document.getElementById("toggle-hide-likes")?.addEventListener("change", (e) => updateSettings({ hideLikeCounts: e.target.checked }, "Updated"));
  viewEl.querySelectorAll(".pick-theme").forEach((b) => {
    b.onclick = () => updateSettings({ themeMode: b.dataset.mode, darkModeEnabled: b.dataset.mode === "dark" }, `Theme: ${b.dataset.mode}`);
  });
  viewEl.querySelectorAll(".pick-opt").forEach((b) => {
    b.onclick = () => updateSettings({ [b.dataset.field]: b.dataset.val }, "Preference saved");
  });
  viewEl.querySelectorAll(".toggle-ulist").forEach((b) => {
    b.onclick = () => toggleUserInList(b.dataset.field, priv[b.dataset.field] || [], b.dataset.uid, "Updated list", "Removed from list");
  });
  viewEl.querySelectorAll(".notif-t, .data-t").forEach((t) => {
    t.onchange = () => updateSettings({ [t.dataset.field]: t.checked }, "Updated");
  });
  viewEl.querySelectorAll(".pick-limit").forEach((b) => {
    b.onclick = () => updateSettings({ dailyTimeLimitMinutes: Number(b.dataset.mins) }, "Time limit saved");
  });
  document.getElementById("add-hw-form")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const word = document.getElementById("hw-input").value.trim().toLowerCase();
    if (!word) return;
    const next = Array.from(new Set([...(priv.hiddenWords || []), word]));
    await updateSettings({ hiddenWords: next }, `Hidden "${word}"`);
  });
  viewEl.querySelectorAll(".rm-hw").forEach((b) => {
    b.onclick = () => updateSettings({ hiddenWords: (priv.hiddenWords || []).filter((w) => w !== b.dataset.word) }, "Removed");
  });
  viewEl.querySelectorAll(".unarchive-btn").forEach((b) => {
    b.onclick = async () => {
      await updateDoc(doc(db, "posts", b.dataset.id), { visibility: "public", updatedAt: serverTimestamp() });
      showToast("Restored to profile");
    };
  });
}

function render() {
  if (!state.user) return;
  if (state.subView === "settings") {
    renderSettings();
    return;
  }
  if (state.subView === "notifications") {
    renderNotifications();
    return;
  }
  if (state.subView === "inbox") {
    renderInbox();
    return;
  }
  if (state.subView === "chat") {
    renderChat();
    return;
  }
  if (state.subView === "user_profile" && state.selectedUserId) {
    renderProfile(state.selectedUserId);
    return;
  }
  switch (state.tab) {
    case "explore": renderExplore(); break;
    case "create": renderCreate(); break;
    case "reels": renderReels(); break;
    case "profile": renderProfile(); break;
    default: renderHome(); break;
  }
}
