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
- Offline translation (`core/ai/translation`) runs Bergamot (`libphalanx_mt.so`, built from the `bergamot-translator` submodule) on language pairs the user downloads or imports, each file checked by hash. It is used when exactly one installed pair translates into the phone's language; anything else goes to the language model. Bergamot's logging is off.
- Never log URLs, tokens, room names, event content or key material.
- Model files (language model, Whisper, vision projector, translation pairs) download only when the user taps Download in AI settings: `core/ai/ModelDownload` streams them over HTTPS from their publisher (Hugging Face for Mistral and whisper.cpp, Mozilla's Remote Settings CDN for the pairs Firefox uses) while hashing, with no cookie or identifier, and keeps them only if the SHA-256 matches the built-in list. The publisher sees the phone's IP address; "From a file" installs a file fetched elsewhere instead. Transfers run in the app scope, one per store, and stop if the process dies.
- Only one native model (`LoadedNative`) runs or stays loaded at a time: loading one releases the others, so the language model and a speech model are never both in memory.
- Emoji Kitchen is off by default (Privacy settings). When on, only the mixes the user views are fetched from `www.gstatic.com`; the pair index ships in the APK (`tools/emoji-kitchen`), so nothing else is sent to Google.
- App lock (`core/security/AppLock`) holds the UI back behind the system's BiometricPrompt, with the phone's PIN as fallback; sync and notifications carry on. With it on, Recents shows no picture of the app.
- `FLAG_SECURE` is set while the Privacy setting or a protected room asks for it (`ui/components/SecureScreens`). Per-room choices (`core/prefs/RoomPrefsStore`) are sealed by `SecretBox` (`room_prefs.bin`), since which rooms are protected or watched says something about them.
- People linked across networks (`core/contacts/PeopleStore`) are sealed by `SecretBox` (`people.bin`) and deleted on sign-out. Links are only suggested (`PeopleLinker`: same name, or same phone number in a WhatsApp, Google Messages or iMessage ID); nothing is linked until the user confirms.
- The incognito keyboard sets `IME_FLAG_NO_PERSONALIZED_LEARNING` on every text field through `InterceptPlatformTextInput`.
- Typing and read alerts (`core/matrix/RoomWatch`) run only while the app syncs on screen: neither is ever pushed, and nothing keeps the app awake to watch for them.
- Reaction notifications turn the account's `.m.rule.reaction` push rule to notify; it is account-wide, so other clients may follow it. The phone keeps only reactions to the account's own messages.
- Settings → Notifications → "Show in notifications" decides how much a notification shows: everything, the sender's name only, or nothing but "New message" (no name, room, photo or shortcut). Typing and read alerts follow it.
- AI results (voice message transcripts, catch-ups) stay in memory unless "Keep AI results on this phone" is on. Then `core/ai/AiDisk` keeps them sealed by `SecretBox` under `ai_results/`, capped in count and age; turning the setting off or signing out deletes the folder's contents.

## 6. On-device AI: GPU (Vulkan) on the Pixel 10

The models run on the CPU only (`n_gpu_layers = 0`). `GGML_CPU_ALL_VARIANTS` ships one CPU backend per Arm level and `cpu_backend.cpp` loads the best one the phone runs, so a Tensor G5 already gets its i8mm and SVE2 kernels. The Tensor G5's TPU is reachable only through Google's AICore and Play Services, which this app does not use. Its GPU (Imagination PowerVR) is reachable through Vulkan, and ggml has a Vulkan backend, but it is not built yet, because it has not been measured.

**Building it.** `GGML_VULKAN=ON` adds `libggml-vulkan.so` as one more runtime-loaded backend next to the CPU ones (`GGML_BACKEND_DL`), so a phone without Vulkan 1.2 simply never loads it. The build needs:
- `glslc`, which the NDK ships under `shader-tools/`;
- the Vulkan C++ headers (`vulkan.hpp`) and SPIRV-Headers, which it does not ship. Both would be pinned as submodules, after the approval CLAUDE.md asks for any new dependency.

`load()` would then pass `n_gpu_layers = 99` when the backend registered. The setting would be off by default and named after what it does ("Use the graphics chip").

**Measuring it, on the Pixel 10, against the CPU i8mm build:**
- prompt reading in tokens/s on a 3,000-token summary prompt (`llama_perf_context` timings, read in a debug build only, never logged in release);
- reply writing in tokens/s over 300 tokens;
- battery used per summary (Settings → Battery usage, ten summaries in a row) and the phone's temperature after them;
- the same for photo descriptions, where the vision projector runs on the GPU too.

**Shipping it** only if prompt reading is at least 1.5× faster, replies are not slower, battery use per summary is not higher, and nothing crashes or answers differently over a week of use. Mobile GPU drivers vary: if any of these fails, the backend stays out and this section records the numbers.
