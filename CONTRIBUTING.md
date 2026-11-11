# Contributing to EasyGamepad

Contributions are welcome through [issues and pull requests](https://github.com/edgardmessias/easygamepad).
For a first look at the code, read the [development guide](docs/development.md).
For controller commands, use the [KRLY reference](docs/protocol.md).

## Set up your environment

- JDK 17 or later, compatible with the project's pinned Android Gradle plugin.
- Android SDK Platform 37 and the build tools requested by Gradle.
- Android SDK Platform-Tools for `adb` and a device with USB debugging enabled.
- The included Gradle wrapper; a separate Gradle installation is unnecessary.

Set `ANDROID_HOME` to your SDK directory, or let Android Studio generate
`local.properties`. Keep machine-specific paths out of version control.
Dependency versions are pinned in [the version catalog](gradle/libs.versions.toml).
The app supports Android 8.0+ (API 26); the `protocol` module runs on the JVM.

## Build and install

From the repository root in PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n br.dev.edgard.easygamepad/.MainActivity
```

On macOS or Linux, use `./gradlew :app:assembleDebug` and forward slashes in paths.
If multiple Android devices are attached, pass `-s SERIAL` to ADB commands.
Updating an existing installation requires a matching signing key. Export your
profiles before any uninstall, which removes the app's local data.

Release builds enable R8 and resource shrinking. Produce `:app:assembleRelease`,
sign the APK with your production key, and ship **corresponding source** (and
third-party notices) alongside any binary you publish, per GPL-3.0-or-later.
Published versions are built and signed in GitHub Actions after maintainer
signing secrets are configured on the repository.

## Run checks

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
python -B -m unittest discover -s .github/scripts -p 'test_*.py' -v
python -B .github/scripts/check_doc_links.py
```

GitHub Actions runs the same Gradle targets on every push and pull request.
Controller-free Compose instrumentation on a managed emulator
(`:app:connectedDebugAndroidTest`) runs only when you dispatch the **CI** workflow
manually on GitHub Actions until emulator stability on Linux runners improves.

For protocol-only work, start with `.\gradlew.bat :protocol:test`.
Extend codec and session tests when changing packet layouts, response matching
or failure handling. Exercise pure app logic in JVM tests where appropriate.
These tests do not replace verification of the Android Bluetooth stack or real
controller behavior.

For hardware-related changes, record controller model, firmware, Android version,
connection mode, operation and observed result. Distinguish an ACK from successful
readback and persistence after a power cycle. See the
[hardware validation checklist](docs/development.md#hardware-validation).

## Prepare a contribution

- Keep changes focused and explain the user-visible result and validation.
- Match the existing Kotlin and Compose conventions. Write new documentation,
  identifiers and explanatory comments in English.
- Follow the existing localization mechanism for user-visible strings. The app
  supports English and Brazilian Portuguese; preserve both when changing UI copy.
- Treat GATT lifecycle, coroutine cancellation and DataStore migration keys with
  care. Read the surrounding code before changing them.
- Preserve SPDX headers, copyright notices and third-party attribution. Do not
  include signing keys, proprietary firmware, vendor APKs or personal captures.
- For documentation changes, check relative links, headings, examples and image
  rendering. Follow the [screenshot procedure](docs/development.md#documentation-screenshots)
  for new app images.

Contributions use **GPL-3.0-or-later**, the same license as the project. See
[LICENSE](LICENSE), [NOTICE](NOTICE) and [THIRD_PARTY.md](THIRD_PARTY.md).
Automated contributors should also read [AGENTS.md](AGENTS.md).

## Report a problem

For bugs, include reproduction steps, expected and actual behavior, app and
Android versions, and the controller model. Attach relevant diagnostics only
after checking them for personal information. For connection problems, mention
whether gameplay, configuration or both are affected.

Report sensitive vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
Participation is covered by the [code of conduct](CODE_OF_CONDUCT.md).
