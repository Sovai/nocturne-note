# NocturneNote

Native, **offline-first Android text editor** built with Kotlin, Jetpack Compose (Material 3), and Room.

## Features

- Markdown and plain-text editing, rich preview, full-screen focus mode
- Import local `.md`, `.txt`, `.pdf`, and other text formats
- Export as `.md`, `.txt`, `.html`, or `.pdf`
- Full-text note search, version history, dark mode, and offline note storage

**Limitations:** Current PDF support extracts text rather than reproducing page layouts. File-manager opening currently imports a copy as a new note, not an editable link to the original document.

## Build

Requirements: Android Studio, JDK 17, Android SDK 36.1, and Gradle 9.3.1.

The original Google AI Studio export did not include the complete Gradle wrapper (`gradlew` and `gradle-wrapper.jar`). Open in Android Studio or install Gradle 9.3.1 locally:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

## CI / APK

GitHub Actions runs on PRs and `main` pushes and publishes a debug APK artifact for successful builds.

**Signing:** CI debug APKs use the temporary build-environment debug key. To distribute app updates without uninstalling, configure persistent signing in a future release workflow. Never commit production keystores or passwords.

## Privacy

No account or internet connection is required. Unused Firebase / Gemini / Google Services integrations from the AI Studio starter project have been removed from the build.

## Installing debug APKs

NocturneNote uses the application ID `com.aistudio.nocturnenote.nxqz`. Android refuses to install one APK **over an existing installation signed by a different certificate** (often shown as “App not installed”). This includes AI Studio builds and the earlier CI artifacts signed with temporary GitHub runners' debug keys.

1. **Export or back up important notes before uninstalling.** This offline app stores notes locally, and uninstalling may delete them.
2. Uninstall the previous NocturneNote only if you have safely saved your notes. Then install the APK from the latest successful GitHub Actions build.
3. Future CI debug APKs from this development signing setup share the same signing certificate, allowing updates in place. Keep the application ID unchanged.
4. Android Play Protect may still warn about an app distributed outside Play; debug signing is **not** Play certification. Do not disable Play Protect globally.
5. If installation still fails, connect the phone by USB and run `adb install -r app-debug.apk` to capture the exact `INSTALL_FAILED_...` error. Check free storage and OS compatibility.

**Security:** The CI test-signing key is public and only for internal debug builds. For production, use a separate private key protected by GitHub Actions secrets; never reuse the public debug key.

## Fonts

The editor defaults to bundled Noto Serif Khmer (SIL Open Font License), including offline text rendering. You can choose another font in Settings; font selection is stored locally across app restarts.
