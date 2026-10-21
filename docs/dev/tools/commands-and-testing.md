# Commands & Testing

```bash
./gradlew :app:assembleDebug        # Debug APK (installs as org.phalanx.android.debug)
./gradlew :app:assembleFastDebug    # Release-speed build on the debug identity, for judging animations
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:koverVerifyDebug     # Coverage floor (a ratchet: only ever raised)
./gradlew :app:lintDebug            # Android Lint
./gradlew :app:detekt               # Static analysis
```

ktlint runs as a standalone binary in CI; run it locally with `ktlint "app/src/**/*.kt"`.

The Android SDK lives on the Windows side, so build from Android Studio or a Windows shell. The WSL Gradle cannot run the Windows build tools.

## Test layers

- Domain and ViewModel tests with fakes of the `core/matrix` interfaces (no Rust SDK needed).
- Compose UI tests for each screen in light, dark and reduced-motion.
- End-to-end checks against a test homeserver by hand: E2EE with another client, verification, UnifiedPush delivery after the app is killed.
