# Telegram Drive — Android

A Material You Android client for [Telegram-Drive](https://github.com/caamer20/Telegram-Drive) — unlimited free cloud storage backed by your Telegram Saved Messages, with Google Photos-style auto-backup, AES-256 encryption, a password vault, in-app Telegram chat, multi-account support, and more.

> **Status:** Source-complete scaffold. Compiles in Android Studio once you wire up the TDLib native library (see [Building](#building-from-source)). All architectural pieces — TDLib integration, encrypted Room DB, WorkManager-driven auto-backup, Glance widget, QS tile, share target — are in place.

---

## ✨ Features

### Backed by the desktop app
- **Upload / download** any file (split into ≤ 1.5 GB chunks across Saved Messages — effectively unlimited storage)
- **File management**: virtual folders, rename, move, delete, favorite, tag, search
- **Media preview**: in-app image / video / document viewer with thumbnails
- **Sharing**: full share-to / share-from intents (other apps can send files to TG Drive)
- **Encryption**: optional AES-256-GCM client-side encryption with passphrase-derived keys
- **Multi-account**: sign in to multiple Telegram accounts simultaneously, per-account session DBs
- **Password vault**: encrypted passwords / notes / cards synced to Saved Messages, biometric unlock, auto-lock
- **Telegram chat**: lightweight in-app client for reading/sending messages (no need to switch apps)

### New in the Android app
- **Auto-backup (Google Photos style)**: real-time MediaStore observer + 15-min WorkManager fallback
  - Per-folder rules (include/exclude paths, file types, min size, schedule)
  - Smart conditions: Wi-Fi only, charging only, quota cap, time-of-day
  - Deduplication via fast (mediaStoreId + size) + slow (SHA-256) paths
  - Original quality only (no compression — matches Telegram-Drive's design)
  - Storage Saver: free up local space after safe backup
  - Pause/resume, retry queue, progress notifications
- **Material You**: dynamic color on Android 12+, light/dark/system, Material 3 typography
- **Home-screen widget**: backup status at a glance (Glance)
- **Quick Settings tile**: pause/resume backup from the shade
- **Onboarding flow**: permissions, login, backup config — first-run friendly
- **Storage insights**: per-account / per-mime-type usage charts
- **Multi-language scaffold**: English, Spanish, Chinese (extendable)
- **Privacy by default**: local DB encrypted with SQLCipher, secrets in EncryptedSharedPreferences, no telemetry

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────────────┐
│                          UI (Compose)                       │
│  Files · Photos · Backup · Vault · Chat · Settings · ...   │
└──────────────────────────┬─────────────────────────────────┘
                           │ Hilt @HiltViewModel
┌──────────────────────────▼─────────────────────────────────┐
│                     Repositories                            │
│  Account · File · Backup · Vault · Chat · Preferences      │
└──┬─────────────────┬──────────────────┬────────────────────┘
   │                 │                  │
   ▼                 ▼                  ▼
┌───────┐    ┌────────────┐    ┌────────────────┐
│ Room  │    │  TDLib     │    │  WorkManager   │
│ SQLCipher │  (per-acct  │    │ AutoBackup     │
│       │    │   session) │    │ Worker + FGSvc │
└───────┘    └────────────┘    └────────────────┘
```

- **MVVM + Repository**, single-source-of-truth Room DB
- **TDLib** (Telegram Database Library) for full Telegram client features (auth, chat, file upload)
- **One TDLib client per account** → independent session DB under `tdlib-sessions/<accountId>/`
- **Files**: each upload = N chunk messages + 1 header message in Saved Messages. Header is a JSON blob: `{v, name, size, mime, sha256, encrypted, iv, chunks:[msgId,...]}`
- **Vault**: AES-256-GCM with PBKDF2-HMAC-SHA256 (600k iterations) wrapping a random DEK. DEK wrapped again by Android MasterKey in EncryptedSharedPreferences — defense in depth.
- **Auto-backup**: `MediaWatcher` ContentObserver (real-time, app-alive) + `AutoBackupWorker` (15-min periodic, WorkManager). `Deduplicator` short-circuits with `mediaStoreId` then falls back to SHA-256. `StorageSaver` deletes local copies once the header message id is confirmed.

### Package layout
```
com.telegramdrive.app
├── TelegramDriveApp.kt        ← @HiltAndroidApp entry
├── di/                          ← Hilt modules (App, Database, Telegram, Repository, Backup)
├── data/
│   ├── local/                   ← Room (AppDatabase, DAOs, entities, SqlCipherKeyProvider)
│   ├── remote/telegram/         ← TDLib wrapper (TdClient, TdLibManager, File/Chat/Auth services)
│   ├── crypto/                  ← AES-256-GCM + VaultCrypto
│   ├── backup/                  ← MediaWatcher, AutoBackupWorker, Deduplicator, StorageSaver, Scheduler, Notifier
│   ├── repository/              ← Account, File, Backup, Vault, Chat, Preferences
│   └── sync/                    ← (reserved for future cross-device sync)
├── domain/                      ← (reserved for use-cases as the project grows)
├── service/                     ← TransferService (FG), MediaBackupService (FG), BootReceiver
├── widget/                      ← Glance BackupStatusWidget + QS Tile
└── ui/
    ├── theme/                   ← Material 3 + dynamic color
    ├── navigation/              ← Bottom nav destinations
    ├── onboarding/              ← Welcome → Permissions → Login → Backup config
    ├── files/                   ← Folder + file browser
    ├── photos/                  ← Gallery grid
    ├── backup/                  ← Rules + storage insights
    ├── vault/                   ← Locked / Unlocked / Setup
    ├── chat/                    ← Chat list (full message view TODO)
    ├── settings/                ← Account, theme, security, about
    ├── accounts/                ← (account switcher sheet — TODO)
    ├── uploads/                 ← (transfer queue UI — TODO)
    ├── common/                  ← Shared composables
    ├── MainActivity.kt
    ├── ShareTargetActivity.kt   ← ACTION_SEND handler
    └── AppRoot.kt               ← Onboarding ↔ MainApp router
```

---

## 🚀 Quick start: get an APK without installing anything

The fastest path to a runnable APK is via **GitHub Actions** — no Android Studio, no NDK, no JDK on your machine, **and no Telegram API credentials needed at build time**. Push the project to a GitHub repo, CI builds a downloadable APK in ~30 minutes (5-8 min on subsequent runs). On first launch the app asks you to enter your Telegram API credentials — they're stored encrypted on-device.

See **[.github/BUILDING_VIA_CI.md](.github/BUILDING_VIA_CI.md)** for the step-by-step guide.

---

## 🔑 Telegram API credentials

Telegram requires every client app to identify itself with an `api_id` + `api_hash` pair, registered per-developer at https://my.telegram.org/apps.

**This app does NOT bake them into the APK.** Instead:

1. You (the user) register your own app at https://my.telegram.org/apps (free, 2 min)
2. On first launch, the app's onboarding flow asks you to paste your `api_id` + `api_hash`
3. They're stored encrypted on your device using Android's `EncryptedSharedPreferences` (AES-256-GCM with a Keystore-backed master key) — see `SecureCredentialsStore.kt`
4. The credentials never leave the device
5. You can update or clear them at any time in **Settings → Telegram API credentials**

This means:
- **One APK works for any user** — no per-user builds
- **No GitHub Secrets needed for CI** — just push and the workflow produces an APK
- **Your credentials stay yours** — they're not in any committed file or build artifact

---

## 🔧 Building from source (locally)

### Prerequisites
- Android Studio Koala Feature Drop (2024.1.2) or newer
- JDK 17
- Android SDK 34 + Build Tools 34.0.0
- CMake + Android NDK (only needed if you're rebuilding TDLib from source — see below)

### 1. Clone & set up SDK path
```bash
git clone https://github.com/caamer20/Telegram-Drive-Android.git
cd Telegram-Drive-Android
cp local.properties.template local.properties
```

Open `local.properties` and set your Android SDK path (Android Studio does this automatically when you open the project):
```properties
sdk.dir=/home/USER/Android/Sdk
```

**No Telegram API credentials needed at build time** — they're entered inside the app on first launch.

### 2. Provide TDLib native libraries

The prebuilt `tdjni.so` files for `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` need to be placed under:
```
app/src/main/jniLibs/<ABI>/libtdjni.so
```

Two options:

**A) Use a prebuilt distribution (recommended)**
The `gradle/libs.versions.toml` already declares a JitPack dependency on a community TDLib Android build. If that artifact is reachable, you're done — Android Studio will pull it.

**B) Build TDLib from source**
```bash
git clone https://github.com/tdlib/td.git
cd td
# Follow https://github.com/tdlib/td#building for Android
# Copy the resulting libtdjni.so into each jniLibs/<ABI>/ directory
```

### 3. Open & build
```bash
# From the project root:
./gradlew :app:assembleDebug
# Or open the project in Android Studio and press Run
```

Install on a device:
```bash
./gradlew :app:installDebug
```

---

## 🚀 First-run flow

1. **Onboarding**: Welcome → grant media + notifications permissions → sign in to Telegram (phone → code → optional 2FA password) → enable auto-backup.
2. The app creates a default backup rule (DCIM/Camera + Pictures/Screenshots, Wi-Fi only, original quality, dedup on).
3. The `MediaBackupService` foreground service starts watching MediaStore; `AutoBackupWorker` schedules a 15-min periodic catch-up.
4. Files appear in the **Photos** tab grouped by date; tap **Files** for the folder tree.

---

## 🔒 Security model

| Layer | Mechanism |
|-------|-----------|
| Telegram transport | TLS via TDLib |
| Telegram storage   | Per-account TDLib session DB (encrypted by TDLib) |
| File encryption    | Optional AES-256-GCM with PBKDF2-HMAC-SHA256 (600k iters) passphrase-derived key |
| Vault              | Random 32-byte DEK wrapped by passphrase-derived KEK, stored in EncryptedSharedPreferences (Android MasterKey-protected) |
| Local DB           | SQLCipher SupportFactory injected into Room |
| Biometric          | AndroidX BiometricPrompt (BIOMETRIC_STRONG class 3) |
| Backup credentials | No passwords stored — TDLib session only |

**What we don't do:** collect analytics, phone home, log message contents, or upload any metadata beyond what Telegram itself requires.

---

## 🧪 Testing

- Unit tests live under `app/src/test/` (planned — crypto + dedup are the highest-value first targets).
- Instrumented tests under `app/src/androidTest/` (planned — DB migrations, share intent).

Run them:
```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## 📋 Roadmap

The scaffold is complete; the following items are the natural next iterations:

- [ ] Full chat thread screen (currently shows list only)
- [ ] Drag-to-reorder backup rules
- [ ] Account switcher bottom sheet (currently you can switch via Settings)
- [ ] Transfer queue UI screen (currently surfaced via notifications)
- [ ] Native media viewer with ExoPlayer + pinch-zoom + swipe
- [ ] Optional "verify on first download" integrity pass for Storage Saver
- [ ] Photo map view (read GPS EXIF, show on a map)
- [ ] Wear OS tile for backup status
- [ ] Onboarding: scan QR code to log in via t.me client
- [ ] Test coverage for crypto + dedup + worker

---

## 📜 License

Same as the upstream project (see `LICENSE` in the original repo).

## 🙏 Acknowledgements

- [Telegram-Drive](https://github.com/caamer20/Telegram-Drive) — original desktop app
- [TDLib](https://github.com/tdlib/td) — official Telegram Database Library
- [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/)
- [Glance](https://developer.android.com/jetpack/androidx/releases/glance) for app widgets
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for resilient background work
- [SQLCipher](https://www.zetetic.net/sqlcipher/) for full-DB encryption
- [Coil](https://coil-kt.github.io/coil/) for image loading
- [Media3 / ExoPlayer](https://developer.android.com/media/media3) for video playback
