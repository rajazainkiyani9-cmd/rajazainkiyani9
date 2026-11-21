# Deploy Aura directly to Google & Chrome (No Play Store needed)

Aura is already converted into a complete **Google Chrome / Safari installable Web App (PWA)** inside `pwa-web/`.
It connects to your exact same Firebase project (`gen-lang-client-0990515920`) and Cloud Firestore database (`ai-studio-android-aura-e1bf04cc-5631-4fb9-b51f-4b64c3feaf86`) with real-time sync.

## Option A: 1-Command Deploy from Google Cloud Shell (Fastest — No local install)
1. Open **[Google Cloud Shell](https://shell.cloud.google.com/)** in your browser (signed in as your Google account).
2. Export/upload this project (or push to GitHub and `git clone` it in Cloud Shell).
3. Run:
   ```bash
   cd pwa-web
   bash deploy-google.sh
   ```
4. That automatically registers the Web App ID, updates `public/firebase-config.js`, and deploys live to:
   - **`https://gen-lang-client-0990515920.web.app`**

## Option B: One-Time Console Toggles (for Storage Videos & Social Logins)
1. Open **Firebase Console** -> select project **`gen-lang-client-0990515920`**.
2. **Storage (for Reels Videos & HD Photos):** **Build -> Storage -> Get started**, then run `firebase deploy --only storage` (note: even without Storage, photos automatically save in Ultra HD!).
3. **Authorized Domains:** **Authentication -> Settings -> Authorized domains** -> make sure `gen-lang-client-0990515920.web.app` and `gen-lang-client-0990515920.firebaseapp.com` are listed.
4. **Sign-in Providers:** Enable **Google** (default active), plus **Email/Password**, **Phone**, **Facebook**, or **Apple** if you want all login tabs active on the web.

## Install on Phone & Desktop Chrome
- **Android Chrome:** Visit `https://gen-lang-client-0990515920.web.app` -> tap **Install Aura App** banner (or Chrome `⋮` menu -> **Install app** / **Add to Home screen**). Chrome creates a native home-screen app icon with splash screen and full-screen standalone mode.
- **Desktop Chrome:** Click the **Install** icon inside Chrome's address bar to install Aura as a desktop app.
- **iPhone Safari:** Tap **Share** -> **Add to Home Screen**.
