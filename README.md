<p align="center">
  <img src="docs/assets/phalanx-logo.png" width="96" height="96" alt="Phalanx logo" />
</p>

<h1 align="center">Phalanx</h1>

<p align="center">
  A secure, private, battery-first Matrix client for Android, built under EUPL-1.2.
</p>

<p align="center">
  <a href="https://github.com/Kolbxyz/Phalanx/actions/workflows/ci.yml"><img src="https://github.com/Kolbxyz/Phalanx/actions/workflows/ci.yml/badge.svg" alt="CI Status"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-EUPL--1.2-blue" alt="License"/></a>
  <img src="https://img.shields.io/badge/made%20in-%F0%9F%87%AB%F0%9F%87%B7%20France-002395" alt="Made in France"/>
  <img src="https://img.shields.io/badge/push-UnifiedPush-6c5ce7" alt="UnifiedPush"/>
</p>

<p align="center">
  <a href="https://github.com/Kolbxyz/Phalanx/releases">Releases</a> ·
  <a href="docs/dev/README.md">Developer docs</a> ·
  <a href="docs/dev/architecture/overview.md">Architecture</a>
</p>

---

Phalanx is a Matrix client built on the **[Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk)**, with every chat end-to-end encrypted by the same crypto, sync and storage code the reference clients use, and a strict **Material 3 Expressive** interface on top.

Designed for hardened phones like GrapheneOS: no Google services, push through **UnifiedPush**, zero telemetry, Keystore-backed secrets and HTTPS only. Its AI features (summaries, transcription, translation) run **on the phone**, from models you choose to download.

Messaging only: no voice or video calls, ever.

---

## Welcome to Phalanx! (FAQ for Newcomers)

<details>
<summary><b>What is Matrix, and why would I use it?</b></summary>
<br>
Matrix is an open, federated messaging network: like email, you pick a server (or run your own) and still talk to everyone else on the network. Conversations are end-to-end encrypted, so your server stores messages it cannot read.
</details>

<details>
<summary><b>My friends are on WhatsApp, Signal or Discord. Can I still reach them?</b></summary>
<br>
Yes, through <b>bridges</b> on your Matrix server (WhatsApp, Signal, Telegram, Discord, Instagram, Messenger, Slack, Google Messages, LinkedIn, Twitter and Bluesky are recognised). Phalanx shows which network each chat comes from, tells you when a bridge needs signing in again, and can link one person's chats across networks into <em>one</em> thread. Links are only ever suggested: nothing is joined until you confirm.
</details>

<details>
<summary><b>How do notifications work without Google?</b></summary>
<br>
Through <a href="https://unifiedpush.org">UnifiedPush</a>, with the distributor of your choice (ntfy, NextPush, …). The pusher is <code>event_id_only</code>: the gateway and distributor only ever see an event ID and a room ID. Phalanx then fetches and decrypts the message on the phone, and you decide how much a notification shows: everything, the sender only, or just "New message".
</details>

<details>
<summary><b>"On-device AI"? Does anything leave my phone?</b></summary>
<br>
No. Summaries and catch-ups run a local language model (Mistral's Ministral 3B, through llama.cpp); voice messages are transcribed by Whisper; rooms translate offline with Bergamot, the engine Firefox uses. Nothing downloads until you tap Download, every model file is checked against a built-in SHA-256, and results stay in memory unless you ask Phalanx to keep them, sealed, on the phone.
</details>

<details>
<summary><b>Is it tracking me?</b></summary>
<br>
No. There is no analytics, no crash reporting and no tracking SDK of any kind. Phalanx never logs URLs, tokens, room names, message content or keys. Link previews are fetched only from public internet addresses, and tracking parameters can be stripped from the links you send.
</details>

<br>

---

## Key Capabilities

### Messaging
* **End-to-end encryption by the Rust SDK**: Device verification, key backup and sync are the SDK's own, never reimplemented.
* **One thread per person**: A friend's chats across Matrix and bridged networks, merged into one timeline and one card in People.
* **Bookmarks**: Save messages, synced across the account's devices.
* **Reminders & scheduled sending**: Be reminded of a message later, or write one now and send it at a set time.
* **Custom emoji**: Matrix image packs, reactions with any emoji, and optional Emoji Kitchen mixes.
* **Media**: Room galleries, an image editor before sending, voice messages and stickers.

### On the phone, offline
* **Catch me up**: A summary across rooms of what you missed, with dates, tasks and places pulled out.
* **Voice transcription & dictation**: Whisper turns voice messages into text, and your voice into a draft.
* **Offline translation**: Per-room auto-translation with downloaded Bergamot language pairs.

### Privacy at hand
* **App lock**: The system's biometric prompt, with the phone's PIN as fallback; sync and notifications carry on.
* **Screenshot blocking**: App-wide or per room; Recents shows no picture of a locked app.
* **Incognito keyboard**: Every text field asks the keyboard not to learn from what you type.
* **Link hygiene**: Tracking parameters stripped from links before you send them.

---

## Battery & Privacy Discipline

### Battery First
* **Zero Polling Loops**: Sync runs while the app is on screen or in answer to a push, never on a timer.
* **Push, then Sleep**: A push wakes one expedited worker that fetches and decrypts a single event, then lets the phone sleep.
* **Constrained Housekeeping**: Background work goes through WorkManager under unmetered network, charging and battery-not-low.
* **One Model at a Time**: Only one native AI model is ever loaded; loading one releases the others.

### Hardened Privacy
1. **Zero Telemetry**: No Firebase, no Google Play Services, no Crashlytics, no analytics of any kind.
2. **Keystore-Backed Secrets**: Session tokens, the store passphrase, push endpoints, linked people and per-room choices are sealed with keys held in the hardware Android Keystore.
3. **HTTPS Only**: Cleartext traffic is refused everywhere, and user-installed certificate authorities are not trusted.
4. **Backup Resistant**: Cloud backup and device-to-device transfer are blocked (`allowBackup=false`), so crypto keys never leave the phone.
5. **Verified Models**: An AI model loads only if its SHA-256 matches the built-in list, so a swapped file never reaches the native code.

---

## Architectural Principles

The codebase enforces strict engineering rules documented in [CLAUDE.md](CLAUDE.md) and [`docs/dev/`](docs/dev/README.md):

* **300 Lines Max Per File**: Split at 250 lines; one concept per file.
* **The SDK Owns Crypto, Sync and Storage**: `core/matrix` is the only package importing the Rust SDK; the UI never sees its types.
* **Pinned SDK**: The bindings change between releases, so their version is pinned strictly.
* **Strict Material 3 Expressive**: `MaterialExpressiveTheme`, motion from the theme's motion scheme, explicit edge-to-edge insets.

```
app/src/main/java/org/phalanx/android/
├── core/
│   ├── ai/         On-device language model, Whisper and Bergamot translation
│   ├── contacts/   People linked across networks
│   ├── format/     Markdown, HTML rendering and link tracker stripping
│   ├── matrix/     Rust SDK facade: sync, timeline, encryption, bridges
│   ├── media/      Attachments, image edits and voice recording
│   ├── preview/    Link previews over public-only sockets
│   ├── push/       UnifiedPush service, registrar and notifier
│   ├── schedule/   Reminders and scheduled messages
│   └── security/   App lock and Keystore-sealed storage
├── data/           Domain models and repositories
├── di/             Hilt modules, one per concern
└── ui/             Theme, navigation, components and screens
```

---

## Installation & Updates

* **Obtainium**: Add `Kolbxyz/Phalanx` to [Obtainium](https://github.com/ImranR98/Obtainium) for updates straight from GitHub releases.
* **GitHub Releases**: Download signed APKs from the [Releases](https://github.com/Kolbxyz/Phalanx/releases) tab.

Phalanx ships for `arm64-v8a` and `armeabi-v7a`. You need a UnifiedPush distributor installed for notifications while the app is closed.

---

## Building from Source

Requires **JDK 17**, **Android SDK 37** (compileSdk 37, minSdk 26) and the Android NDK. The native code comes from submodules:

```bash
# Fetch native sources (llama.cpp, whisper.cpp, Bergamot, PCRE2)
git submodule update --init
.github/scripts/init-bergamot.sh

# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :app:testDebugUnitTest
```

More commands and the test layers are in [`docs/dev/tools/commands-and-testing.md`](docs/dev/tools/commands-and-testing.md).

---

## License

**EUPL-1.2 (European Union Public Licence v1.2)**: see [`LICENSE`](LICENSE).
