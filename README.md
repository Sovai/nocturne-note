# NocturneNote

Native, **offline-first Android text editor** built with Kotlin, Jetpack Compose (Material 3), and Room.

## Features

- Markdown and plain-text editing, rich preview, full-screen focus mode
- Import local `.md`, `.txt`, `.pdf`, and other text formats
- Export as `.md`, `.txt`, `.html`, or `.pdf`
- Full-text note search, version history, dark mode, and offline note storage

**Limitations:** Current PDF support extracts text rather than reproducing page layouts. File-manager opening currently imports a copy as a new note, not an editable link to the original document.


## Read Aloud (multilingual)

Read a complete note or selected text using two engines:

- **Edge Read Aloud** is selected by default. It uses Microsoft's unofficial consumer Read Aloud WebSocket endpoint and requires internet. Its live voice catalog exposes supported languages and neural voices, with an embedded set of commonly used languages when voice discovery is unavailable.
- **Android TTS** is the system-voice alternative and the automatic fallback when Edge audio generation or playback fails, the service returns HTTP 403/429, or a matching Edge voice is unavailable. Availability and quality depend on installed Android speech data.

Open **Settings → Read Aloud** to select the engine, language, voice, speed (0.75–1.5×), Edge voice pitch (-40–40 Hz), and opt-in background playback. Languages and voices use searchable dialogs. Automatic language selection detects several writing systems and uses the device locale for Latin-script text. Choose a specific language for ambiguous scripts or multilingual text. Voice support is limited to languages supported by Microsoft Edge or the device's installed TTS engine.

Edge playback uses a bounded ordered prefetch queue: generation begins for the current passage and the next three passages immediately. Up to two requests run concurrently; as each passage begins playing, another passage is scheduled. Prepared MP3 is held in a small in-memory window and the existing private disk cache. Network latency should usually be hidden behind playback, although a slow connection can still cause buffering and separate Android MP3 decoders may produce a short audible boundary.

The compact mini-player keeps Previous, Play/Pause, Next, and Close targets visible on narrow screens. It includes a seek slider, speed shortcut, and Settings shortcut. Tapping a passage in the virtualized read-along view seeks to that passage. Resume position is kept per note until the note changes or reading finishes. Leaving the note stops playback.

**Privacy and reliability:** Edge sends the requested passage to Microsoft's online consumer speech service over TLS; MP3 responses are kept in the application's private cache (up to 48 MiB). Android TTS uses installed system voices; some third-party Android speech engines may use their own network services. The app does not upload your saved note database. The Edge integration is **not an official Azure Speech API** and service availability, rate limits, commercial usage rights, or long-term compatibility are not guaranteed. A Google Play release needs a reviewed privacy policy, permission disclosures, and confirmation of the provider's permitted use. The retired OmniVoice and MMS model-download/inference stacks have been removed, including their JNI dependencies.

### CI

Android CI runs on main pushes, PR open/reopen/ready-for-review, or manual workflow dispatch, not on every PR commit. Emulator tests and internal release APK builds remain separate manual opt-ins.

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
