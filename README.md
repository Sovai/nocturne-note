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


### Read Aloud engines: offline models and optional online Khmer voice

- Android TTS: free fallback, using installed Khmer/English voices.
- Edge Read Aloud (online): optional no-key Khmer neural TTS (Piseth male / Sreymom female). Needs internet but no model download, exposes voice, speech speed and pitch. Uses Microsoft's Edge Read Aloud consumer WebSocket protocol, **not Azure Speech's official free-tier API**. This is unofficial and may stop working or restrict access.
- MMS Khmer: optional ~100 MB ONNX model, available for download/delete in Settings; known limitations in Khmer intelligibility.
- OmniVoice ONNX: Android beta supports **fully offline inference** from downloaded ONNX model files: Qwen2 tokenization, 32-step audio-code unmasking, Higgs audio decoding, local PCM playback, and per-chunk generated-audio cache. No PC and no paid API. Memory usage, audio quality, and speed remain unverified on the physical Pixel 8 Pro.
- Model registry: https://github.com/Sovai/-nocturne-note-models
- Both model releases are now published with SHA-256-verified assets: `mms-khm-v1` (~109 MiB) and `omnivoice-onnx-int4-v1` (~735 MiB). The app prefers verified GitHub Release assets for MMS, using upstream only when the catalog cannot be reached. The model registry is currently named `-nocturne-note-models`; both the old and corrected URLs are accepted to support renaming.

#### Edge Read Aloud HTTP 403 investigation (October 2026)

The previously embedded Edge WebSocket client generated an outdated 64-character plain-SHA256 `Sec-MS-GEC` token. Current upstream `rany2/edge-tts` uses a **v2 CS64-SHA256** token (`16HEX-64HEX`), so the Android client now implements that signature with upstream LGPL-3.0-or-later attribution and tests. It also removes the header/audio CRLF separator correctly and explains a server HTTP 403 without silently falling back to another TTS engine.

A third-party website such as `edge-tts.com` can work even if a direct app request is denied, potentially because its own server makes synthesis requests. The site is not an authenticated, documented integration API for Nocturne Note. The current direct client might still receive 403 due to Microsoft endpoint restrictions or network conditions; this update is a protocol fix, **not guaranteed service access**. For guaranteed online access, a separately configured official Azure Speech account/key is a different integration.

#### Online Edge Read Aloud (optional)

Select **Edge Read Aloud · online** in Settings or the mini-player. Use Khmer voice **Piseth (male)** or **Sreymom (female)**, set speed (0.75–1.5×) and pitch (-40 to +40 Hz). It needs internet but no API key, PC, or model download. The app sends each selected passage over a secure WebSocket to Microsoft's Edge consumer Read Aloud service, receives MP3 audio and plays it through Android MediaPlayer. Previously generated audio is cached locally, bounded to 48 MiB. Pausing, seeking and existing notification controls remain available; network errors are shown rather than silently using another model.

**This is not official Azure AI Speech F0** and must not be described as a guaranteed free Azure entitlement. The unsupported consumer endpoint may change, rate-limit, refuse access or be unsuitable for commercial voice output. See Microsoft's actual [Azure service terms](https://www.microsoft.com/licensing/terms/en-US/productoffering/MicrosoftAzure/allprograms). Offline engines remain available independently.

#### OmniVoice Android beta limitations

Generation now reports its current stage (tokenization, model loading, unmasking step 1–32, or waveform decoding). Startup has a 20-second deadline and each OmniVoice chunk has a 180-second deadline. Deadline expiry is shown as an error and the worker client is closed; it is not silently treated as a user cancellation that leaves the loading UI active. Edge synthesis has the same error handling with a 65-second deadline. These bounds and diagnostic stages do not establish that OmniVoice is fast enough for continuous reading on a phone.

The app now runs the published Qwen3-based 32-step unmasking process and Higgs decoding locally through Android ONNX Runtime with a Qwen tokenizer. Only default voice/no reference recording is implemented. Voice cloning/design needs the separate acoustic/semantic/quantizer-encoder path. The model can be enabled in Settings after downloading and validating all assets; use a short Khmer passage initially.

The published INT4 and FP16 Higgs assets total approximately 735 MiB, not counting runtime overhead. Physical Android testing of RAM, synthesis speed and quality is required before declaring this production-ready. The app exposes it as beta, not as a quality-verified replacement for MMS.

Reference: https://huggingface.co/onnx-community/OmniVoice-Onnx

#### Reading UX

- A Read Aloud session temporarily switches the editor/preview body to a virtualized reading view, highlights the currently playing chunk, scrolls it into view, and permits tapping any chunk to play from there.
- Mini-player has a progress seek slider, engine selection, speed and voice controls. Edge has two Khmer voice choices and adjustable pitch; Android fallback shows its installed voices. MMS has one voice.
- Position is persisted per saved note, unless the note's text has changed; the next Read Aloud begins at the saved chunk. When reading finishes, progress resets. On explicit stop the chunk list disappears and normal note preview returns.
- Playback prefetches the next chunk and caches generated MMS PCM under a 64 MiB limit, trims excessive silent tails, and exposes adjustable 0–600 ms extra pause and 1–4 CPU threads. Explicit MMS never silently switches to Android TTS when synthesis fails. First-time synthesis can still take longer than playback on some phones.
- Background playback is opt-in under Settings. It uses an Android media foreground service with lock-screen/notification controls and audio focus. Leaving the note itself always stops playback. Without the option, leaving the app also stops playback.


#### Diagnosing the October 14 OmniVoice tokenizer failure

The captured Android log at 17:03 reported `UnsatisfiedLinkError: libc++_shared.so not found` while loading DJL's `libdjl_tokenizer.so`. The previous APK did not package the tokenizer's required NDK C++ shared runtime. This was **not** a low-memory failure: it stopped before tokenization or ONNX inference. The build now stages the pinned Android NDK's C++ runtime for each ABI into the APK and CI verifies that both the tokenizer and `libc++_shared.so` are packaged. The Android editor process remained alive thanks to the isolated inference worker; end-to-end model synthesis still requires device validation.

#### OmniVoice crash recovery and Android CI

OmniVoice inference runs in a dedicated Android `:omnivoice` subprocess instead of the editor process. If the 735 MiB model exceeds memory limits or a native library crashes, the reader shows an error rather than terminating the note editor. The worker returns PCM through a private cache file, avoiding Android Binder's message-size limit. Available-memory checks and conservative ONNX session optimization reduce the risk of native out-of-memory failures. This **does not prove** that the model produces correct Khmer audio: a device test is still required.

For device diagnostics, run `adb logcat -d -v time | grep -iE 'NocturneOmniVoice|FATAL EXCEPTION|OutOfMemoryError|onnxruntime|djl_tokenizer'` immediately after the failure. Native crashes and low-memory kills can be diagnosed through Android's ApplicationExitInfo when available.

Android CI no longer builds on each feature-branch commit. It runs on main pushes, PR open/reopen/ready-for-review, or manual workflow dispatch. For a targeted post-commit CI check, add the `bug` label to the PR (remove/re-add for a new run); labeled checks skip the costly emulator. The manual workflow has separate opt-ins for emulator tests and internal release APKs.

#### Licenses and privacy

Meta MMS weights: CC BY-NC 4.0 (noncommercial). OmniVoice (k2-fsa) and the onnx-community conversion declare Apache-2.0; check each component and any future model assets individually before commercial redistribution. Voice models are not bundled into the APK. **MMS, OmniVoice and Android TTS never upload note text through this app.** Choosing Edge Read Aloud sends only the selected reading chunks and selected voice settings to Microsoft's online service. Voice requests require TLS; generated MP3 is cached privately and capped at 48 MiB. Edge access is unauthenticated and is not backed by an official Azure service-level or pricing agreement. Microsoft can change or block the consumer endpoint. The Azure Speech free tier is a distinct official product and may have different usage and output rights.

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
