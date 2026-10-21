# Phalanx Architecture Overview

Single Gradle module `:app`, package root `org.phalanx.android`.

## 1. Package Structure

```
core/       matrix (Rust SDK facade), push (UnifiedPush), security (Keystore), work, i18n
data/       model · repository
di/         Hilt modules, one per concern
ui/         theme · navigation · components · screens/<screen> (XScreen, XViewModel, XUiState)
```

## 2. Invariants

- **`core/matrix` is the only SDK importer.** It wraps `ClientBuilder`, `SyncService`, `RoomListService`, `Timeline`, `Encryption` and `NotificationClient` behind Kotlin interfaces and flows. SDK bumps stay local to it.
- **The SDK owns crypto, sync and storage.** The app owns presentation and notification delivery.
- **Repository barrier.** ViewModels talk to repositories only.
- **Session secrets** (access token, store passphrase) live in Keystore-wrapped storage, never in plaintext preferences, Room or logs.

## 3. Push (no FCM)

UnifiedPush receiver → expedited WorkManager worker → `NotificationClient.getNotifications` (short sync, local decrypt) → notification. The push gateway sees only `(eventId, roomId)`. The gateway URL is configurable.

## 4. Battery

- No polling loops. Sync runs while the UI is `STARTED` or in response to a push.
- Housekeeping runs through WorkManager constrained by `UNMETERED + charging + !battery-low`.
- Ship `arm64-v8a` and `armeabi-v7a` only; the SDK is the dominant APK cost.

## 5. Security & Privacy

- `allowBackup=false` and empty `dataExtractionRules`: crypto keys never leave the device.
- HTTPS only; user-installed CAs are not trusted.
- Zero third-party telemetry, crash reporting or analytics SDKs.
- Never log URLs, tokens, room names, event content or key material.
