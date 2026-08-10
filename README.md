# SillyTavern E-ink Android Client

Native Android client for the removable `eink-companion` SillyTavern server plugin.

## Requirements

- Android 8.0 / API 26 or newer
- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.9 or Android Studio with a compatible Gradle installation
- The server plugin enabled at `/api/plugins/eink-companion/v1`

This source tree intentionally does not include a generated Gradle wrapper JAR. Open it in Android Studio or build with an already installed compatible Gradle; no wrapper binary was available in the authoring environment.

The authoring machine did not have an Android/JDK/Gradle toolchain, so this tree was reviewed statically but was not compiled locally. Run a clean build and the unit tests on the target build machine.

## Build

```powershell
$ErrorActionPreference = 'Stop'
gradle --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Remove `--offline` when the declared Android and Maven artifacts are not already cached on the build machine.

## Build an APK with GitHub Actions

Treat this `android-eink` directory as the root of its own GitHub repository. The workflow at `.github/workflows/build-extension.yml` installs JDK 17, Gradle 8.9, Android API 35, and Build Tools 35.0.0 on a GitHub-hosted runner. It then runs the unit tests and Android lint before building an installable debug APK.

1. Push the complete contents of this directory, including the hidden `.github` directory, to GitHub.
2. Open the repository's **Actions** tab and select **Build Android APK**.
3. Choose **Run workflow**, select the branch, and confirm the run.
4. After the run succeeds, open it and download the `sillytavern-eink-debug-<run number>` artifact.
5. Unzip the artifact and install `app-debug.apk` on the Android device. The adjacent `.sha256` file can be used to verify the download.

The same workflow also runs automatically when relevant project files are pushed or changed in a pull request. It produces a debug-signed APK intended for personal testing and does not need repository secrets. A distributable release APK requires a private signing key and a separate signing configuration; never commit a keystore or its passwords.

## Server connection

Remote servers require HTTPS validated by the Android trust store. The app never installs a permissive trust manager. Cleartext HTTP requires explicit approval and is limited in application code to localhost, `.local` names, and private IPv4/IPv6 address ranges.

For LAN access, SillyTavern must listen on the LAN interface and its IP whitelist must include the e-reader. Do not expose port 8000 directly to the public internet.

The current stream parser accepts OpenAI-compatible SSE, including sources such as OpenAI and OpenRouter. SillyTavern sources that return provider-native Claude, Google AI Studio (`makersuite`), or Cohere event schemas are rejected during connection with a clear error. They require a future normalization layer rather than being treated as compatible and silently losing output.

## E-ink behavior

- No animated transitions or message cards
- High-contrast transcript layout
- Stream rendering batched to approximately 750 ms
- Page Up/Page Down hardware key support
- Periodic full invalidation after partial updates
- A vendor-neutral `EinkController` boundary for future BOOX, Hanvon, Bigme, or other SDK adapters

The generic controller uses standard Android invalidation. Vendor waveform modes require the corresponding vendor SDK and physical-device verification.

## Current boundaries

- Persona positions `IN_PROMPT` and `NONE` are supported.
- Token budgeting uses the companion plugin's conservative estimate, not exact tokenizer parity.
- Groups, tools, Text Completion, and browser extension interception are not implemented.
- Cookies are encrypted with an account-scoped Android Keystore key, and application backup is disabled.
- Legacy SillyTavern browser writes cannot join the plugin's compare-and-swap critical section.
- Direct plugin chat writes do not invoke SillyTavern's normal chat-backup route.
