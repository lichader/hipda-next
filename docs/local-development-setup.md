# Local Development Setup

Status: draft. Updated on 2026-11-26 for the Android 17 toolchain (AGP 9.1.1, Gradle 9.3.1, JDK 21, compile SDK 37) and Android SDK command-line tools 23.0. The command-line build matches the CI workflow ([.github/workflows/build.yml](../.github/workflows/build.yml)), which builds debug and release APKs and runs lint on every push. The bootstrap script has been tested on Linux x86_64 only; its macOS paths and the Android Studio steps have not been tested end to end.

This guide covers building, installing, and checking HiPDA Next on your own machine. For project layout and coding conventions, see [AGENTS.md](../AGENTS.md).

## Quick Start: Bootstrap Script

On Linux (x86_64) or macOS, [scripts/bootstrap.sh](../scripts/bootstrap.sh) does steps 1 to 4 for you:

```sh
git clone https://github.com/lichader/hipda-next.git
cd hipda-next
scripts/bootstrap.sh              # JDK 21, Android SDK, local.properties, then a debug build
scripts/bootstrap.sh --emulator   # the same, plus the emulator and two virtual devices (about 10 GB)
```

The script needs `curl`, `unzip`, `zip`, `tar` and `sed`, and installs everything under your home directory without `sudo`:

- SDKMAN!, if it is missing, and the JDK named in [.sdkmanrc](../.sdkmanrc). Your default JDK does not change.
- The Android SDK command-line tools, at a pinned version whose SHA-256 is checked. On first use, their `android` CLI downloads the rest of itself (about 340 MB) into `~/.android/`.
- The compile SDK platform (`platforms/android-37.0`) and platform tools. Installing them accepts the Android SDK licenses on your behalf.
- With `--emulator`: the emulator, a `google_apis` system image for API 37 and one for the oldest API the app supports (21), and the virtual devices `hipda-api37` and `hipda-api21` in `~/.android/avd`. This takes about 10 GB, and each device grows by several GB once it has been started. Without `--emulator`, the SDK takes about 0.5 GB.

It then writes `sdk.dir` to `local.properties` and runs `./gradlew :hipda:assembleDebug`, which also installs the build tools. The SDK goes to `sdk.dir` from an existing `local.properties`, then `$ANDROID_HOME`, then `~/Android/Sdk` (Linux) or `~/Library/Android/sdk` (macOS); `--sdk-dir DIR` overrides all of them. `--skip-build` skips the build. Running the script again is safe: finished steps are skipped.

The script does not edit your shell startup files. At the end it prints the `ANDROID_HOME` and `PATH` lines to add. On Windows, use WSL or follow the manual steps below.

## Toolchain Summary

| Component | Version | Where it is set |
| --- | --- | --- |
| JDK | 21 | Pinned in [gradle/gradle-daemon-jvm.properties](../gradle/gradle-daemon-jvm.properties); [.sdkmanrc](../.sdkmanrc) names a Temurin 21 build for SDKMAN! users; CI uses Temurin 21 |
| Gradle | 9.3.1 (via wrapper) | [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) |
| Android Gradle plugin (AGP) | 9.1.1 | [build.gradle](../build.gradle) |
| Android SDK platform | API 37 (Android 17), package `platforms;android-37.0` | `compileSdkVersion` in [build.gradle](../build.gradle) |
| Target SDK | 37 | `targetSdkVersion` in [build.gradle](../build.gradle) |
| Android SDK build tools | 36.0.0 (AGP 9.1.1 default) | Gradle installs it on first build if missing |
| Android SDK command-line tools | 23.0 | Pinned in [scripts/bootstrap.sh](../scripts/bootstrap.sh) |
| Minimum device API | 21 (Android 5.0) | `minSdkVersion` in [build.gradle](../build.gradle) |
| Java language level | 8 | `compileOptions` in both module `build.gradle` files |

Always use the wrapper (`./gradlew`, or `gradlew.bat` on Windows) rather than a system-installed Gradle.

## 1. Install JDK 21

Install any JDK 21 distribution, such as Eclipse Temurin 21 (the one CI uses). The project pins the Gradle daemon to Java 21 in `gradle/gradle-daemon-jvm.properties`, so the build stops with an error if no JDK 21 can be found.

- SDKMAN! (macOS, Linux, WSL): run `sdk env install` in the repository root. It installs the Temurin 21 build named in `.sdkmanrc` and switches the current shell to it. In later shells, run `sdk env`, or set `sdkman_auto_env=true` in `~/.sdkman/etc/config` to switch automatically on `cd`. Gradle also finds JDKs installed by SDKMAN! on its own, so the build works even if your default Java is a different version.
- macOS (Homebrew): `brew install --cask temurin@21`
- Ubuntu/Debian: `sudo apt install openjdk-21-jdk`
- Windows: download the Temurin 21 installer from adoptium.net.

Point Gradle at it. Either set `JAVA_HOME` in your shell:

```sh
export JAVA_HOME=/path/to/jdk-21
java -version   # should report 21.x
```

If JDK 21 is installed somewhere Gradle does not look, add `org.gradle.java.installations.paths=/path/to/jdk-21` to `~/.gradle/gradle.properties`. Do not add it to the tracked `gradle.properties`, because the path is machine-specific.

## 2. Install the Android SDK

You need the SDK platform, build tools, and platform tools (for `adb`). SDKMAN! does not manage the Android SDK; the `sdkmanager` tool below is Android's own, unrelated to SDKMAN!.

**With Android Studio:** open *Settings > Languages & Frameworks > Android SDK* and install the *Android 17 / API 37* platform under *SDK Platforms*, and *Android SDK Platform-Tools* under *SDK Tools*. Gradle downloads the matching build tools on the first build if they are missing.

**Without Android Studio:** download the *Command line tools only* package from developer.android.com/studio, unpack it to `<sdk>/cmdline-tools/latest/`, then run:

```sh
export ANDROID_HOME=/path/to/android-sdk
"$ANDROID_HOME/cmdline-tools/latest/bin/android" sdk list --all 'platforms/android-37*'
"$ANDROID_HOME/cmdline-tools/latest/bin/android" sdk install platforms/android-37.0 platform-tools
```

From command-line tools 23.0, the `android` CLI replaces `sdkmanager` and writes package names with `/` instead of `;`. The API 37 platform is published as `android-37.0`; `android-37.1` and later are minor releases this project does not need. `sdk install` accepts each package's license, so Gradle can then install other missing SDK packages (such as build tools) itself. With older command-line tools, use `sdkmanager --licenses` and `sdkmanager "platforms;android-37.0" "platform-tools"` instead.

## 3. Clone and Configure the Project

```sh
git clone https://github.com/lichader/hipda-next.git
cd hipda-next
```

Create `local.properties` in the repository root so Gradle can find the SDK. The file is ignored by Git.

```properties
# macOS example; on Windows, escape backslashes: C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
sdk.dir=/Users/you/Library/Android/sdk
```

Android Studio creates this file automatically when it opens the project. Setting `ANDROID_HOME` also works in place of `sdk.dir`.

No other local configuration is needed for a debug build. In particular, the README's instruction to copy `fabric.properties.example` is outdated: that file does not exist and the build has no Fabric configuration.

## 4. Build

From the repository root:

```sh
./gradlew :hipda:assembleDebug
```

The first run downloads Gradle 9.3.1 and all dependencies from Maven Central, Google Maven, and JitPack, so it needs network access to those hosts. The APK is written to `hipda/build/outputs/apk/debug/`.

## 5. Run on a Device or Emulator

Use a physical device with USB debugging enabled, or an emulator running API 21 or newer. `scripts/bootstrap.sh --emulator` creates `hipda-api37` and `hipda-api21`; start one with `emulator -avd hipda-api37`, or without a window with `android emulator start --headless hipda-api37`. Android Studio's Device Manager can create others. With the SDK's `platform-tools` on your `PATH`, check that the device is visible and install:

```sh
adb devices
./gradlew :hipda:installDebug
```

Then launch *HiPDA Next* on the device. Because the app targets API 37, test on an Android 15+ device or emulator as well as an older one: edge-to-edge layout, predictive Back and the notification permission only take effect on newer Android versions. The application ID is `com.lichader.hipdanext`, which differs from the original Play Store build (`net.jejer.hipda.ng`), so both can be installed side by side.

Logging in, posting, and most forum flows need a HiPDA forum account.

## 6. Lint and Tests

```sh
./gradlew :hipda:lintDebug :emoji:lintDebug
```

Application lint errors do not fail the build (`abortOnError false`), so read the HTML reports under `hipda/build/reports/` and `emoji/build/reports/`.

There are no automated tests yet. `./gradlew :hipda:testDebugUnitTest :emoji:testDebugUnitTest` will run JVM tests once they are added. Until then, verify changes manually on a device, covering whichever of the forum, login, posting, image, emoji, and theme flows the change touches.

## Using Android Studio

Open the repository root with *File > Open*. Use an Android Studio release that supports AGP 9.1 and API 37; older releases will refuse to sync. If Studio offers a further AGP upgrade, treat it as a separate change.

Studio follows the project's Daemon JVM criteria (Java 21) when its Gradle JDK setting allows it; otherwise set *Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK* to a JDK 21, such as Studio's bundled JDK if it is version 21.

The repository tracks a few `.idea/` files (copyright, encodings, scopes, VCS); leave other generated IDE files untracked.

## Release Builds

`./gradlew :hipda:assembleRelease` produces `hipda/build/outputs/apk/release/hipda-release-unsigned.apk` with R8 minification. (The old `hipda-ng-release.apk` rename relied on a variant API that AGP 9 removed.) The build has no signing configuration, so the APK is unsigned and must be signed with `apksigner` before it can be installed. Keep keystores and passwords out of the repository; put any signing properties in `~/.gradle/gradle.properties`.

## Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| Gradle cannot find a Java 21 toolchain for the daemon | Install JDK 21, or point `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties` at it. |
| `SDK location not found` | Create `local.properties` with `sdk.dir`, or set `ANDROID_HOME`. |
| `Failed to find target` for API 37, or missing build tools | Install `platforms/android-37.0` (see step 2) so its license is accepted and Gradle can install build tools. |
| Gradle warns that it "only understands SDK XML versions up to 3" | Harmless: command-line tools 23.0 write a newer metadata format than AGP 9.1.1 reads. The build still works. |
| `adb server version (...) doesn't match this client` | Two different `adb` binaries are on your `PATH`, such as a system package and the SDK's `platform-tools`. Keep one first on your `PATH`. |
| Dependency resolution fails for `com.github.*` artifacts | JitPack (`jitpack.io`) is unreachable; check network or proxy settings. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A differently signed copy of the app is installed. Uninstall it, then reinstall. |
