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

**Signing:** CI debug APKs use the committed, development-only signing identity for reproducible test updates. Never use this publicly known key for production or commit a production signing key.

### Installable internal release APK

To build a release-mode APK that updates current development-signed installations, run GitHub Actions → Android CI → **Run workflow** on `main`, or merge a commit whose message contains `[internal-release]`. The workflow verifies signing and the app ID/version and attaches `Nocturne-Note-v1.0.5-internal-release` to the workflow run (30-day retention).

Locally (with Gradle 9.3.1 and Android SDK), run:

```bash
gradle -PnocturneInternalRelease=true :app:assembleRelease
```

The installable file is `app/build/outputs/apk/release/app-release.apk` with version `1.0.5` (code `6`), package `com.sovai.nocturnenote`, release build configuration, and the existing **development signing certificate**. This is for internal testing **only**, not a production-signed or Play Store release. Do not distribute it as a production release. Normal `assembleRelease` without this property does not use that test signing identity.

## Privacy

No account or internet connection is required. Unused Firebase / Gemini / Google Services integrations from the AI Studio starter project have been removed from the build.

## Installing debug APKs

NocturneNote uses the application ID `com.sovai.nocturnenote` as of v1.0.2. It can be installed alongside older AI Studio copies using `com.aistudio.nocturnenote.nxqz`. Existing notes stay in the old app and **do not transfer automatically**. Android refuses to update any copy signed by a different certificate (often shown as “App not installed”).

1. **Export or back up important notes before uninstalling.** This offline app stores notes locally, and uninstalling may delete them.
2. Install the new package (`com.sovai.nocturnenote`) without removing the old (`com.aistudio.nocturnenote.nxqz`); export/import your notes if needed. Uninstall the old app only when the notes are safely migrated.
3. Future CI debug APKs using this same development signing certificate and `com.sovai.nocturnenote` ID support updates in place.
4. Android Play Protect may still warn about an app distributed outside Play; debug signing is **not** Play certification. Do not disable Play Protect globally.
5. Install from your desktop using `adb install -r app-debug.apk` and confirm with `adb shell pm path com.sovai.nocturnenote`. Capture any `INSTALL_FAILED_...` error. Check free storage and OS compatibility.

**Security:** The CI test-signing key is public and only for internal debug builds. For production, use a separate private key protected by GitHub Actions secrets; never reuse the public debug key.

## Fonts

Khmer text is always rendered with bundled Noto Serif Khmer (SIL Open Font License), including in mixed Khmer/Latin notes. The six choices in Settings affect only Latin/English glyphs: Sans, Serif, Mono, System, Script and Happy Monkey (also bundled under the SIL Open Font License). English font and light/dark theme preferences persist across restarts.

The two app themes are Light (default) and Dark. The palette is inspired by the logo's indigo, turquoise and muted moon-gold.
