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

UnifiedPush `PushService` → expedited WorkManager worker → `NotificationClient.getNotification` (short sync, local decrypt) → notification. The pusher is `event_id_only`, so the gateway and distributor see only `(eventId, roomId)`.

- `core/push/PushRegistrar` registers on every session open and turns each new endpoint into the homeserver pusher; sign-out unregisters locally (the homeserver drops pushers with the token).
- Gateway: the distributor server's own `/_matrix/push/v1/notify` when it answers `{"unifiedpush":{"gateway":"matrix"}}`, else `matrix.gateway.unifiedpush.org`. Settings → Notifications can override it.
- The endpoint is a capability URL, so the last registration is sealed by `SecretBox` (`push.bin`).

## 4. Battery

- No polling loops. Sync runs while the UI is `STARTED` or in response to a push.
- Housekeeping runs through WorkManager constrained by `UNMETERED + charging + !battery-low`.
- Ship `arm64-v8a` and `armeabi-v7a` only; the SDK is the dominant APK cost.

## 5. Security & Privacy

- `allowBackup=false` and empty `dataExtractionRules`: crypto keys never leave the device.
- HTTPS only; user-installed CAs are not trusted.
- Zero third-party telemetry, crash reporting or analytics SDKs.
- Offline translation (`core/ai/translation`) runs Bergamot (`libphalanx_mt.so`, built from the `bergamot-translator` submodule) on language pairs the user imports, each file checked by hash. It is used when exactly one installed pair translates into the phone's language; anything else goes to the language model. Bergamot's logging is off.
- Never log URLs, tokens, room names, event content or key material.
- Emoji Kitchen is off by default (Privacy settings). When on, only the mixes the user views are fetched from `www.gstatic.com`; the pair index ships in the APK (`tools/emoji-kitchen`), so nothing else is sent to Google.
- App lock (`core/security/AppLock`) holds the UI back behind the system's BiometricPrompt, with the phone's PIN as fallback; sync and notifications carry on. With it on, Recents shows no picture of the app.
- `FLAG_SECURE` is set while the Privacy setting or a protected room asks for it (`ui/components/SecureScreens`). Per-room choices (`core/prefs/RoomPrefsStore`) are sealed by `SecretBox` (`room_prefs.bin`), since which rooms are protected or watched says something about them.
- People linked across networks (`core/contacts/PeopleStore`) are sealed by `SecretBox` (`people.bin`) and deleted on sign-out. Links are only suggested (`PeopleLinker`: same name, or same phone number in a WhatsApp, Google Messages or iMessage ID); nothing is linked until the user confirms.
- The incognito keyboard sets `IME_FLAG_NO_PERSONALIZED_LEARNING` on every text field through `InterceptPlatformTextInput`.
- Typing and read alerts (`core/matrix/RoomWatch`) run only while the app syncs on screen: neither is ever pushed, and nothing keeps the app awake to watch for them.
- Reaction notifications turn the account's `.m.rule.reaction` push rule to notify; it is account-wide, so other clients may follow it. The phone keeps only reactions to the account's own messages.
- Settings → Notifications → "Show in notifications" decides how much a notification shows: everything, the sender's name only, or nothing but "New message" (no name, room, photo or shortcut). Typing and read alerts follow it.
- Photo search (off by default) describes photos with the language model and its vision projector (llama.cpp `mtmd`, built into `phalanx_llm`). `core/ai/photos/PhotoIndexWorker` runs once a day, only while the phone charges, sits idle on an unmetered network with battery to spare; it pages each joined room's image timeline (`RustRecentPhotos`, no sync), describes up to 40 new photos, and keeps the words sealed on the `photos` shelf. Search matches them on the phone. Turning photo search off erases them.
- AI results (voice message transcripts, catch-ups, photo descriptions) stay in memory unless "Keep AI results on this phone" is on. Then `core/ai/AiDisk` keeps them sealed by `SecretBox` under `ai_results/`, capped in count and age; turning the setting off or signing out deletes the folder's contents.
