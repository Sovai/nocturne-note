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


### Higher-quality OmniVoice on your PC (no subscription)

OmniVoice needs a Python/PyTorch host; it is **not** currently a downloadable Android ONNX model. The optional LAN bridge is in tools/omnivoice_server.py. Use an NVIDIA GPU for practical performance. On Windows, from a PowerShell session in the repository:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install torch torchaudio --index-url https://download.pytorch.org/whl/cu128
pip install omnivoice numpy soundfile
$token = python -c "import secrets; print(secrets.token_urlsafe(32))"
Write-Host "OmniVoice token: $token"
python tools/omnivoice_server.py --host 0.0.0.0 --port 8765 --token $token
```

The first launch downloads the model to your **PC**, never to Android. Verify that installed CUDA/PyTorch versions match your GPU drivers (see official OmniVoice installation guide). Find your PC's LAN IP using `ipconfig`. Connect phone and PC to the same trusted Wi-Fi. In **Settings → Read Aloud**, select **OmniVoice (PC / Wi-Fi)**, enter `http://192.168.x.x:8765` and the same secret. Tap **Test OmniVoice connection**. Android supports only literal private IPv4 addresses for this unencrypted local connection.

**Optional consistent Khmer voice:** provide a 3–10 second Khmer speaker recording you have permission to use and its exact Khmer transcript. Add `--reference-wav "C:\path\reference.wav" --reference-text "..." ` to the server command. It creates a reusable prompt at server startup and uses it for subsequent requests. Without a reference, choose Automatic / Male / Female / Older male / Soft female; voice design was trained primarily on English/Chinese and may produce inconsistent Khmer. These options do not change MMS: MMS has only one speaker.

**Security:** do not expose port 8765 to the public internet. The server requires a long token; HTTP on LAN is not encrypted. Note text leaves the phone for the locally configured PC during synthesis, but no paid cloud TTS is used. Do not use untrusted Wi-Fi or port-forward the server. Access token preferences are excluded from Android backup.

**Licensing:** the OmniVoice code is Apache-2.0, but the pretrained weights are CC BY-NC, restricting commercial use. Confirm model rights before monetizing synthesized outputs. Meta MMS checkpoint is CC BY-NC 4.0 (noncommercial). No voice models are bundled into the APK.

**Performance:** the reader pre-generates the next chunk while playing the current one. First-chunk synthesis still incurs latency and low-powered computers can have gaps. Voice speed is adjustable between 0.75× and 1.5×. Voice style changes apply to the next reading session.


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
