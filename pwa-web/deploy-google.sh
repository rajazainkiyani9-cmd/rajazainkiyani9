#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="gen-lang-client-0990515920"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Aura -> Google Firebase Hosting & Chrome PWA Deploy ==="

if ! command -v firebase >/dev/null 2>&1; then
  echo "Installing firebase-tools..."
  npm install -g firebase-tools
fi

if ! firebase projects:list >/dev/null 2>&1; then
  echo "Please sign in with your Google account:"
  firebase login --no-localhost || firebase login
fi

firebase use "$PROJECT_ID"

echo "Checking Firebase Web App registration..."
WEB_APPS_JSON="$(firebase apps:list WEB --project "$PROJECT_ID" --json 2>/dev/null || echo '{"result":[]}')"
APP_ID="$(node -e '
  const data = JSON.parse(process.argv[1] || "{}");
  const list = data.result || [];
  const aura = list.find(a => /aura/i.test(a.displayName || "")) || list[0];
  console.log(aura ? aura.appId : "");
' "$WEB_APPS_JSON")"

if [ -z "$APP_ID" ]; then
  echo "Creating Firebase Web App 'Aura PWA'..."
  firebase apps:create WEB "Aura PWA" --project "$PROJECT_ID"
  WEB_APPS_JSON="$(firebase apps:list WEB --project "$PROJECT_ID" --json)"
  APP_ID="$(node -e '
    const data = JSON.parse(process.argv[1] || "{}");
    const list = data.result || [];
    const aura = list.find(a => /aura/i.test(a.displayName || "")) || list[0];
    console.log(aura ? aura.appId : "");
  ' "$WEB_APPS_JSON")"
fi

if [ -n "$APP_ID" ]; then
  echo "Syncing real Web App config ($APP_ID) into public/firebase-config.js..."
  SDK_JSON="$(firebase apps:sdkconfig WEB "$APP_ID" --project "$PROJECT_ID" --json)"
  node -e '
    const fs = require("fs");
    const sdk = JSON.parse(process.argv[1]).result.sdkConfig;
    const content = `// Auto-synced Firebase Web App config for Aura PWA.\nexport const firebaseConfig = ${JSON.stringify(sdk, null, 2)};\n\nexport const FIRESTORE_DATABASE_ID = "ai-studio-android-aura-e1bf04cc-5631-4fb9-b51f-4b64c3feaf86";\n`;
    fs.writeFileSync("public/firebase-config.js", content);
  ' "$SDK_JSON"
fi

echo "Deploying Aura PWA & Firestore Rules to Google Firebase Hosting..."
firebase deploy --only hosting,firestore --project "$PROJECT_ID"

if firebase storage:buckets:list --project "$PROJECT_ID" >/dev/null 2>&1; then
  firebase deploy --only storage --project "$PROJECT_ID" || true
fi

echo ""
echo "=== LIVE ON GOOGLE & CHROME ==="
echo "Open in Google Chrome (Android / iPhone / Desktop):"
echo "  https://${PROJECT_ID}.web.app"
echo "  https://${PROJECT_ID}.firebaseapp.com"
