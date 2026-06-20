# Building the APK via GitHub Actions

This guide walks you through getting an installable debug APK **without installing anything on your computer**. GitHub Actions builds TDLib + the APK in the cloud and uploads it as a downloadable artifact.

**Time investment:** ~5 minutes of setup, ~30 minutes for the first build (CI is compiling TDLib from source across 4 ABIs). Subsequent builds take ~5-8 minutes thanks to caching.

> 💡 **Telegram API credentials are entered in the app, NOT at build time.**
> The same APK works for any user — when you first open the app, the onboarding flow asks for your `api_id` and `api_hash` (from https://my.telegram.org/apps) and stores them encrypted on your device. No GitHub Secrets needed.

---

## Prerequisites

- A GitHub account (free)
- That's it. No Android Studio, no NDK, no JDK, no Telegram API credentials required to build.

You'll need your Telegram API credentials later when you **open** the app — see step 6.

---

## Step 1 — Create a new GitHub repository (1 min)

1. Go to https://github.com/new
2. Name it whatever you like (e.g. `telegram-drive-android`)
3. Set it to **Private** or **Public** — either is fine (no secrets in the build)
4. **Don't** initialize with a README or .gitignore (we'll push our own)
5. Click **Create repository**

GitHub will show you a page with push instructions. Copy the URL (looks like `https://github.com/yourusername/telegram-drive-android.git`) — you'll need it in Step 3.

---

## Step 2 — Push the project to GitHub (3 min)

You need Git on your computer. (Mac: `brew install git` · Windows: https://git-scm.com/download/win · Linux: `sudo apt install git`)

Then:

```bash
# 1. Extract the zip you downloaded somewhere
cd path/to/TelegramDrive-Android

# 2. Initialize git
git init
git branch -M main

# 3. Stage and commit all files
git add .
git commit -m "Initial import: Telegram Drive Android app"

# 4. Add your GitHub repo as the remote (use the URL from Step 1)
git remote add origin https://github.com/YOUR_USERNAME/telegram-drive-android.git

# 5. Push
git push -u origin main
```

If you prefer a GUI, you can also drag-and-drop the extracted folder into https://github.com/YOUR_USERNAME/telegram-drive-android/upload/main — GitHub will accept it as a single commit.

---

## Step 3 — Trigger the build (1 min + ~30 min wait)

The workflow runs automatically on every push. To trigger it manually:

1. Open your repo on GitHub
2. Click the **Actions** tab at the top
3. On the left, click **Build APK**
4. Click the **Run workflow** button on the right → **Run workflow**
5. A new run appears in the list — click it to watch progress

You'll see live logs as CI:
1. Sets up JDK 17 + Android SDK + NDK + CMake
2. Checks out TDLib source from `tdlib/td`
3. Compiles `libtdjni.so` for `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`
4. Compiles the TDLib Java bindings JAR
5. Builds the debug APK with Gradle (no API credentials needed)
6. Uploads the APK as an artifact

The first run takes **20-30 minutes** (TDLib compilation is heavy). Future runs take **5-8 minutes** because the TDLib `.so` files are cached.

---

## Step 4 — Download the APK (1 min)

1. When the run finishes (green checkmark), open the run page
2. Scroll to the bottom — there's an **Artifacts** section
3. Click **telegram-drive-debug-apk** to download a `.zip`
4. Extract it — inside is `app-debug.apk`

---

## Step 5 — Install on your phone (1 min)

1. **Transfer the APK to your phone** — email it to yourself, use Google Drive, USB cable, etc.
2. On your phone, open the APK file (you may need to allow "Install from unknown sources" in Settings)
3. Tap **Install**
4. Open the app — you'll see the onboarding flow

> ℹ️ The debug APK is signed with the Android debug keystore, which is fine for personal testing. For distributing to others, set up a proper release keystore.

---

## Step 6 — Get Telegram API credentials (2 min, only needed at app launch)

When you open the app for the first time, the onboarding flow will ask you for your Telegram API credentials. Get them now:

1. Open https://my.telegram.org/apps in a browser
2. Log in with your phone number (the one tied to your Telegram account)
3. Click **Create new application**
4. Fill in any name (e.g. `Telegram Drive Android`) and a short description
5. Copy the **App api_id** (a number like `1234567`) and **App api_hash** (a long hex string like `abcdef1234567890abcdef1234567890`)
6. Paste them into the app's onboarding screen

The credentials are stored encrypted on your device via Android's EncryptedSharedPreferences (AES-256-GCM with a Keystore-backed master key). They never leave the device.

You can update them later in **Settings → Telegram API credentials**.

---

## Onboarding flow (what you'll see)

1. **Welcome** screen
2. **Permissions** — grant media access (photos, videos) + notifications
3. **Telegram API credentials** — paste your `api_id` and `api_hash`
4. **Login** — enter phone number → verification code → optional 2FA password
5. **Backup config** — enable auto-backup (you can customize rules later)

After onboarding, new photos/videos will start backing up to your Saved Messages automatically (per the default rule: DCIM/Camera + Pictures/Screenshots, Wi-Fi only, original quality, dedup on).

---

## Troubleshooting

### Build failed at "Build TDLib for Android" step
- Check the workflow logs for the specific CMake error
- TDLib sometimes needs more RAM than GitHub's free runners provide — re-run the workflow, it often succeeds on retry
- If it persistently fails, open an issue on https://github.com/tdlib/td with the CMake error

### Build failed at "Build debug APK" step
- Usually a code compile error — check the Gradle log
- If it's a TDLib class not found error, the Java bindings JAR wasn't built — check the previous step's log

### Login fails with "api_id invalid" or "Unauthorized"
- The credentials you entered in the app are wrong or have a typo
- Go to **Settings → Telegram API credentials → Update credentials** and re-enter them

### App crashes on launch
- Open the app, wait for the crash, then run `adb logcat | grep TelegramDrive` from your computer (with USB debugging on) to get the stack trace

### Want to skip TDLib compilation?
If you can pre-build `libtdjni.so` yourself (or grab a prebuilt build from https://github.com/tdlib/td/releases), place them in `app/src/main/jniLibs/<ABI>/` and commit them. Then add `app/src/main/jniLibs/` to `.gitignore` exclusion. The CI build will skip TDLib compilation since the cache key matches.

---

## What if I want to make changes?

1. Edit files locally
2. `git add . && git commit -m "Describe change" && git push`
3. GitHub Actions automatically builds a new APK
4. Download the new APK from the latest run

That's it — every push gives you a fresh APK in ~5-8 minutes. Your API credentials stay on your device; you only need to re-enter them if you uninstall the app or clear its data.
