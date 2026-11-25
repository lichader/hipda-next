# Local Development Setup

Status: draft. Updated on 2026-11-25 for the Android 17 toolchain (AGP 9.1.1, Gradle 9.3.1, JDK 17, compile SDK 37). The command-line build matches the CI workflow ([.github/workflows/build.yml](../.github/workflows/build.yml)), which builds debug and release APKs and runs lint on every push. Android Studio steps have not been tested end to end.

This guide covers building, installing, and checking HiPDA·NG on your own machine. For project layout and coding conventions, see [AGENTS.md](../AGENTS.md).

## Toolchain Summary

| Component | Version | Where it is set |
| --- | --- | --- |
| JDK | 17 | Required by AGP 9.1.1; CI uses Temurin 17 |
| Gradle | 9.3.1 (via wrapper) | [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) |
| Android Gradle plugin (AGP) | 9.1.1 | [build.gradle](../build.gradle) |
| Android SDK platform | API 37 (Android 17) | `compileSdkVersion` in [build.gradle](../build.gradle) |
| Target SDK | 37 | `targetSdkVersion` in [build.gradle](../build.gradle) |
| Android SDK build tools | AGP 9.1.1 default | Gradle installs it on first build if missing |
| Minimum device API | 21 (Android 5.0) | `minSdkVersion` in [build.gradle](../build.gradle) |
| Java language level | 8 | `compileOptions` in both module `build.gradle` files |

Always use the wrapper (`./gradlew`, or `gradlew.bat` on Windows) rather than a system-installed Gradle.

## 1. Install JDK 17

Install any JDK 17 distribution, such as Eclipse Temurin 17 (the one CI uses).

- macOS (Homebrew): `brew install --cask temurin@17`
- Ubuntu/Debian: `sudo apt install openjdk-17-jdk`
- Windows: download the Temurin 17 installer from adoptium.net.

Point Gradle at it. Either set `JAVA_HOME` in your shell:

```sh
export JAVA_HOME=/path/to/jdk-17
java -version   # should report 17.x
```

or, if your default JDK must stay newer, pin it for this project only by adding `org.gradle.java.home=/path/to/jdk-17` to `~/.gradle/gradle.properties`. Do not add it to the tracked `gradle.properties`, because the path is machine-specific.

## 2. Install the Android SDK

You need the SDK platform, build tools, and platform tools (for `adb`).

**With Android Studio:** open *Settings > Languages & Frameworks > Android SDK* and install the *Android 17 / API 37* platform under *SDK Platforms*, and *Android SDK Platform-Tools* under *SDK Tools*. Gradle downloads the matching build tools on the first build if they are missing.

**Without Android Studio:** download the *Command line tools only* package from developer.android.com/studio, unpack it to `<sdk>/cmdline-tools/latest/`, then run:

```sh
export ANDROID_HOME=/path/to/android-sdk
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --list | grep "platforms;android-37"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install \
  "platforms;android-37" \
  "platform-tools"
```

Use the exact API 37 platform package name that `--list` prints. Once the licenses are accepted, Gradle installs any other missing SDK packages (such as build tools) itself.

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

Use a physical device with USB debugging enabled, or an emulator created in Android Studio's Device Manager running API 21 or newer. With the SDK's `platform-tools` on your `PATH`, check that the device is visible and install:

```sh
adb devices
./gradlew :hipda:installDebug
```

Then launch *HiPDA·NG* on the device. Because the app targets API 37, test on an Android 15+ device or emulator as well as an older one: edge-to-edge layout, predictive Back and the notification permission only take effect on newer Android versions. The application ID is `com.lichader.hipdanext`, which differs from the original Play Store build (`net.jejer.hipda.ng`), so both can be installed side by side.

Logging in, posting, and most forum flows need a HiPDA forum account.

## 6. Lint and Tests

```sh
./gradlew :hipda:lintDebug :emoji:lintDebug
```

Application lint errors do not fail the build (`abortOnError false`), so read the HTML reports under `hipda/build/reports/` and `emoji/build/reports/`.

There are no automated tests yet. `./gradlew :hipda:testDebugUnitTest :emoji:testDebugUnitTest` will run JVM tests once they are added. Until then, verify changes manually on a device, covering whichever of the forum, login, posting, image, emoji, and theme flows the change touches.

## Using Android Studio

Open the repository root with *File > Open*. Use an Android Studio release that supports AGP 9.1 and API 37; older releases will refuse to sync. If Studio offers a further AGP upgrade, treat it as a separate change.

Set the Gradle JDK to 17 (or Studio's bundled JDK, if it is 17 or newer) under *Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK*.

The repository tracks a few `.idea/` files (copyright, encodings, scopes, VCS); leave other generated IDE files untracked.

## Release Builds

`./gradlew :hipda:assembleRelease` produces `hipda/build/outputs/apk/release/hipda-release-unsigned.apk` with R8 minification. (The old `hipda-ng-release.apk` rename relied on a variant API that AGP 9 removed.) The build has no signing configuration, so the APK is unsigned and must be signed with `apksigner` before it can be installed. Keep keystores and passwords out of the repository; put any signing properties in `~/.gradle/gradle.properties`.

## Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| `Android Gradle plugin requires Java 17 to run` | Gradle is running on an older JDK. Switch `JAVA_HOME` or `org.gradle.java.home` to JDK 17. |
| `SDK location not found` | Create `local.properties` with `sdk.dir`, or set `ANDROID_HOME`. |
| `Failed to find target` for API 37, or missing build tools | Install the API 37 platform with `sdkmanager`, and accept licenses so Gradle can install build tools. |
| Dependency resolution fails for `com.github.*` artifacts | JitPack (`jitpack.io`) is unreachable; check network or proxy settings. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A differently signed copy of the app is installed. Uninstall it, then reinstall. |
