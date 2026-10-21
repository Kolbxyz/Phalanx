# Phalanx

Android Matrix client built on the Matrix Rust SDK. Strict Material 3 Expressive. Security-, privacy- and battery-first, zero telemetry, no Google services (UnifiedPush only, no FCM). Messaging only: no voice/video calls, ever.

## 1. Non-Negotiable Hard Rules

| Rule | Why |
| --- | --- |
| **300 lines max per file** | Split when a file reaches 250 lines; 1 concept per file. |
| **Crypto, sync and storage belong to the Rust SDK** | Never reimplement E2EE, key handling or sync. |
| **UI never touches SDK types** | `core/matrix` is the only package importing `org.matrix.rustcomponents`. |
| **Pin the SDK version strictly** | The bindings change API between releases. |
| **Strict Material 3 Expressive** | `MaterialExpressiveTheme`, motion from `MaterialTheme.motionScheme`, never hand-rolled `spring()`. |
| **Explicit window insets** | Edge-to-edge needs manual container-edge padding. |
| **Zero secrets in plaintext** | Keystore-backed storage only; never log URLs, tokens, room/event content or keys. |
| **HTTPS only** | `cleartextTrafficPermitted=false`, no exceptions, user CAs not trusted. |
| **No Google services** | No FCM, Play Services, Firebase or GMS fonts. Push is UnifiedPush. |
| **No polling** | Sync runs in the foreground or on push; background work goes through constrained WorkManager. |
| **Strings in `strings.xml`** | Pass `@StringRes Int`; never hardcode user text in UI. |
| **No dead code & no fake fallbacks** | Surface explicit errors; no blanket `catch (e: Exception)`. |
| **Research before adding a dependency** | Follow `docs/dev/process/research-workflow.md`. Sovereign French, then European, solutions first; ask approval before adding either. |
| **Never push to `main`** | Feature branch and PR. |

## 2. Documentation Directory Map

| Topic | Pointer |
| --- | --- |
| Coding style | [`docs/dev/process/coding-style.md`](docs/dev/process/coding-style.md) |
| Research workflow | [`docs/dev/process/research-workflow.md`](docs/dev/process/research-workflow.md) |
| Window insets | [`docs/dev/android/window-insets.md`](docs/dev/android/window-insets.md) |
| Material 3 Expressive & motion | [`docs/dev/android/motion-m3.md`](docs/dev/android/motion-m3.md) |
| Architecture, battery & security | [`docs/dev/architecture/overview.md`](docs/dev/architecture/overview.md) |
| Commands & testing | [`docs/dev/tools/commands-and-testing.md`](docs/dev/tools/commands-and-testing.md) |

## 3. Quick Commands

```bash
./gradlew :app:assembleDebug          # Build debug APK
./gradlew :app:testDebugUnitTest      # Run local unit tests
./gradlew :app:lintDebug              # Run lint checks
./gradlew :app:detekt                 # Static analysis
```
