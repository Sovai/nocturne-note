# NocturneNote

Native, **offline-first Android text editor** built with Kotlin, Jetpack Compose (Material 3), and Room.

## Features

- Markdown and plain-text editing, rich preview, full-screen focus mode
- Import local `.md`, `.txt`, `.pdf`, and other text formats
- Export as `.md`, `.txt`, `.html`, or `.pdf`
- Full-text note search, version history, dark mode, and offline note storage

**Limitations:** Current PDF support extracts text rather than reproducing page layouts. File-manager opening currently imports a copy as a new note, not an editable link to the original document.


## Read Aloud (Khmer)

The editor supports **Read Aloud** for the current document or highlighted text in Edit mode. A compact speech controller offers play/pause, paragraph skipping, and speed adjustments. Markdown formatting is removed before synthesis.

**Khmer voice**: Open Settings → **Read Aloud · Khmer** → **Download Khmer voice** to fetch the ONNX voice and tokens to app-private storage (~114 MB model plus tokens). Installed models are used for Khmer offline, with Android TTS as a fallback if the model is absent or inference fails. Settings displays the download progress, installed model size, error/retry status, and a confirmation before deletion. Downloading requires internet, but generated speech does not use a speech API or incur usage fees.

The voice is the Khmer Meta MMS model converted to ONNX by [willwade](https://huggingface.co/willwade/mms-tts-multilingual-models-onnx), used locally through [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx). **Model license is CC BY-NC 4.0: noncommercial use only.** The model is not bundled in APKs; if commercial app distribution is planned, obtain appropriate model rights or replace the model. Deleting the downloaded model does not delete notes.


### On-device model support (no PC, no external speech service)

- Android TTS: free fallback, using installed Khmer/English voices.
- MMS Khmer: optional ~100 MB ONNX model, available for download/delete in Settings; known limitations in Khmer intelligibility.
- OmniVoice ONNX: phone-only experimental candidate. **Model catalog/downloader is implemented, but full Android inference is not yet validated, and OmniVoice is disabled for playback until then.** No PC server and no paid API are used.
- Model registry: https://github.com/Sovai/-nocturne-note-models
- The model repository currently contains a catalog and a manual workflow for release mirroring; do not claim it already hosts weights. The app prefers verified GitHub Release assets for MMS when published and falls back to the existing upstream location in the meantime.

#### Why mobile OmniVoice is not marked playable yet

The ONNX-Community conversion is a Qwen3-based 32-step unmasking system with several ONNX subgraphs and the Higgs audio codec. ONNX Runtime can execute the graphs, but running an isolated ONNX session is **not** an end-to-end Khmer speech engine. A mobile port still requires Qwen tokenizer logic, iterative 8-codebook decoding, accurate codec synthesis, audio buffers, lifecycle and cancellation, and device profiling.

Candidate INT4 artifacts total roughly 758 MB, not counting runtime overhead or extra files. Physical Android testing of RAM, synthesis speed and quality must pass before enabling the OmniVoice model in the player.

Reference: https://huggingface.co/onnx-community/OmniVoice-Onnx

#### Reading UX

- A Read Aloud session temporarily switches the editor/preview body to a virtualized reading view, highlights the currently playing chunk, scrolls it into view, and permits tapping any chunk to play from there.
- Mini-player has a progress seek slider, engine selection and speed; only installed Android fallback voices can be selected currently. MMS has one voice.
- Position is persisted per saved note, unless the note's text has changed; the next Read Aloud begins at the saved chunk. When reading finishes, progress resets. On explicit stop the chunk list disappears and normal note preview returns.
- Playback prefetches the next chunk; pause and focus loss pause audio rather than triggering model fallback.
- Background playback is opt-in under Settings. It uses an Android media foreground service with lock-screen/notification controls and audio focus. Leaving the note itself always stops playback. Without the option, leaving the app also stops playback.

#### Licenses and privacy

Meta MMS weights: CC BY-NC 4.0 (noncommercial). OmniVoice (k2-fsa) and the onnx-community conversion declare Apache-2.0; check each component and any future model assets individually before commercial redistribution. Voice models are not bundled into the APK. Normal reading is entirely local; internet is only used for optional model catalog/downloads.

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
